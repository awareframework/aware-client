package com.aware.utils;

/**
 * Created by denzilferreira on 16/02/16.
 */

import android.app.IntentService;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import com.aware.Applications;
import com.aware.Aware;
import com.aware.Aware_Preferences;
import com.aware.ESM;
import com.aware.R;
import com.aware.providers.Aware_Provider;
import com.aware.ui.esms.ESM_Question;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.skyscreamer.jsonassert.JSONAssert;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import androidx.core.app.NotificationCompat;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Service that allows plugins/applications to send data to AWARE's dashboard study
 * Note: joins a study without requiring a QRCode, just the study URL
 */
public class StudyUtils extends IntentService {
    private static final String[] REQUIRED_STUDY_CONFIG_KEYS = {"database", "questions",
            "schedules", "sensors", "study_info"};

    /**
     * Received broadcast to join a study
     */
    public static final String EXTRA_JOIN_STUDY = "study_url";

    public static String input_password_ = "";

    public StudyUtils() {
        super("StudyUtils Service");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        String full_url = intent.getStringExtra(EXTRA_JOIN_STUDY);

        if (Aware.DEBUG) Log.d(Aware.TAG, "Joining: " + full_url);

        Uri study_uri = Uri.parse(full_url);

        List<String> path_segments = study_uri.getPathSegments();
        String protocol = study_uri.getScheme();
        String study_api_key = path_segments.get(path_segments.size() - 1);
        String study_id = path_segments.get(path_segments.size() - 2);

        // TODO RIO: Replace GET to webserver a GET to study config URL
        String request;
        if (protocol.equals("https")) {
//            SSLManager.handleUrl(getApplicationContext(), full_url, true);

            try {
                request = new Https(SSLManager.getHTTPS(getApplicationContext(), full_url)).dataGET(full_url.substring(0, full_url.indexOf("/index.php")) + "/index.php/webservice/client_get_study_info/" + study_api_key, true);
            } catch (FileNotFoundException e) {
                request = null;
            }
        } else {
            request = new Http().dataGET(full_url.substring(0, full_url.indexOf("/index.php")) + "/index.php/webservice/client_get_study_info/" + study_api_key, true);
        }

        if (request != null) {

            if (request.equals("[]")) return;

            try {
                JSONObject studyInfo = new JSONObject(request);

                //Request study settings
                Hashtable<String, String> data = new Hashtable<>();
                data.put(Aware_Preferences.DEVICE_ID, Aware.getSetting(getApplicationContext(), Aware_Preferences.DEVICE_ID));
                data.put("platform", "android");
                try {
                    PackageInfo package_info = getApplicationContext().getPackageManager().getPackageInfo(getApplicationContext().getPackageName(), 0);
                    data.put("package_name", package_info.packageName);
                    data.put("package_version_code", String.valueOf(package_info.versionCode));
                    data.put("package_version_name", String.valueOf(package_info.versionName));
                } catch (PackageManager.NameNotFoundException e) {
                    Log.d(Aware.TAG, "Failed to put package info: " + e);
                    e.printStackTrace();
                }

                // TODO RIO: Replace POST to webserver with DB insert
                String answer;
                if (protocol.equals("https")) {
                    try {
                        answer = new Https(SSLManager.getHTTPS(getApplicationContext(), full_url)).dataPOST(full_url, data, true);
                    } catch (FileNotFoundException e) {
                        answer = null;
                    }
                } else {
                    answer = new Http().dataPOST(full_url, data, true);
                }

                if (answer == null) {
                    Toast.makeText(getApplicationContext(), "Failed to connect to server, try again.", Toast.LENGTH_SHORT).show();
                    return;
                }

                JSONArray study_config = new JSONArray(answer);

                if (study_config.getJSONObject(0).has("message")) {
                    Toast.makeText(getApplicationContext(), "This study is no longer available.", Toast.LENGTH_SHORT).show();
                    return;
                }

                Cursor dbStudy = Aware.getStudy(getApplicationContext(), full_url);
                if (Aware.DEBUG)
                    Log.d(Aware.TAG, DatabaseUtils.dumpCursorToString(dbStudy));

                if (dbStudy == null || !dbStudy.moveToFirst()) {
                    ContentValues studyData = new ContentValues();
                    studyData.put(Aware_Provider.Aware_Studies.STUDY_DEVICE_ID, Aware.getSetting(getApplicationContext(), Aware_Preferences.DEVICE_ID));
                    studyData.put(Aware_Provider.Aware_Studies.STUDY_TIMESTAMP, System.currentTimeMillis());
                    studyData.put(Aware_Provider.Aware_Studies.STUDY_JOINED, System.currentTimeMillis());
                    studyData.put(Aware_Provider.Aware_Studies.STUDY_KEY, study_id);
                    studyData.put(Aware_Provider.Aware_Studies.STUDY_API, study_api_key);
                    studyData.put(Aware_Provider.Aware_Studies.STUDY_URL, full_url);
                    studyData.put(Aware_Provider.Aware_Studies.STUDY_PI, studyInfo.getString("researcher_first") + " " + studyInfo.getString("researcher_last") + "\nContact: " + studyInfo.getString("researcher_contact"));
                    studyData.put(Aware_Provider.Aware_Studies.STUDY_CONFIG, study_config.toString());
                    studyData.put(Aware_Provider.Aware_Studies.STUDY_TITLE, studyInfo.getString("study_name"));
                    studyData.put(Aware_Provider.Aware_Studies.STUDY_DESCRIPTION, studyInfo.getString("study_description"));

                    getContentResolver().insert(Aware_Provider.Aware_Studies.CONTENT_URI, studyData);

                    if (Aware.DEBUG) {
                        Log.d(Aware.TAG, "New study data: " + studyData.toString());
                    }
                } else {
                    //User rejoined a study he was already part of. Mark as abandoned.
                    ContentValues complianceEntry = new ContentValues();
                    complianceEntry.put(Aware_Provider.Aware_Studies.STUDY_DEVICE_ID, Aware.getSetting(getApplicationContext(), Aware_Preferences.DEVICE_ID));
                    complianceEntry.put(Aware_Provider.Aware_Studies.STUDY_TIMESTAMP, System.currentTimeMillis());
                    complianceEntry.put(Aware_Provider.Aware_Studies.STUDY_KEY, dbStudy.getInt(dbStudy.getColumnIndex(Aware_Provider.Aware_Studies.STUDY_KEY)));
                    complianceEntry.put(Aware_Provider.Aware_Studies.STUDY_API, dbStudy.getString(dbStudy.getColumnIndex(Aware_Provider.Aware_Studies.STUDY_API)));
                    complianceEntry.put(Aware_Provider.Aware_Studies.STUDY_URL, dbStudy.getString(dbStudy.getColumnIndex(Aware_Provider.Aware_Studies.STUDY_URL)));
                    complianceEntry.put(Aware_Provider.Aware_Studies.STUDY_PI, dbStudy.getString(dbStudy.getColumnIndex(Aware_Provider.Aware_Studies.STUDY_PI)));
                    complianceEntry.put(Aware_Provider.Aware_Studies.STUDY_CONFIG, dbStudy.getString(dbStudy.getColumnIndex(Aware_Provider.Aware_Studies.STUDY_CONFIG)));
                    complianceEntry.put(Aware_Provider.Aware_Studies.STUDY_JOINED, dbStudy.getLong(dbStudy.getColumnIndex(Aware_Provider.Aware_Studies.STUDY_JOINED)));
                    complianceEntry.put(Aware_Provider.Aware_Studies.STUDY_EXIT, System.currentTimeMillis());
                    complianceEntry.put(Aware_Provider.Aware_Studies.STUDY_TITLE, dbStudy.getString(dbStudy.getColumnIndex(Aware_Provider.Aware_Studies.STUDY_TITLE)));
                    complianceEntry.put(Aware_Provider.Aware_Studies.STUDY_DESCRIPTION, dbStudy.getString(dbStudy.getColumnIndex(Aware_Provider.Aware_Studies.STUDY_DESCRIPTION)));
                    complianceEntry.put(Aware_Provider.Aware_Studies.STUDY_COMPLIANCE, "rejoined study. abandoning previous");

                    getContentResolver().insert(Aware_Provider.Aware_Studies.CONTENT_URI, complianceEntry);

                    //Update the information to the latest
                    ContentValues studyData = new ContentValues();
                    studyData.put(Aware_Provider.Aware_Studies.STUDY_DEVICE_ID, Aware.getSetting(getApplicationContext(), Aware_Preferences.DEVICE_ID));
                    studyData.put(Aware_Provider.Aware_Studies.STUDY_TIMESTAMP, System.currentTimeMillis());
                    studyData.put(Aware_Provider.Aware_Studies.STUDY_JOINED, System.currentTimeMillis());
                    studyData.put(Aware_Provider.Aware_Studies.STUDY_KEY, study_id);
                    studyData.put(Aware_Provider.Aware_Studies.STUDY_API, study_api_key);
                    studyData.put(Aware_Provider.Aware_Studies.STUDY_URL, full_url);
                    studyData.put(Aware_Provider.Aware_Studies.STUDY_PI, studyInfo.getString("researcher_first") + " " + studyInfo.getString("researcher_last") + "\nContact: " + studyInfo.getString("researcher_contact"));
                    studyData.put(Aware_Provider.Aware_Studies.STUDY_CONFIG, study_config.toString());
                    studyData.put(Aware_Provider.Aware_Studies.STUDY_TITLE, studyInfo.getString("study_name"));
                    studyData.put(Aware_Provider.Aware_Studies.STUDY_DESCRIPTION, studyInfo.getString("study_description"));

                    getContentResolver().insert(Aware_Provider.Aware_Studies.CONTENT_URI, studyData);

                    if (Aware.DEBUG) {
                        Log.d(Aware.TAG, "Rejoined study data: " + studyData.toString());
                    }
                }

                if (dbStudy != null && !dbStudy.isClosed()) dbStudy.close();

                applySettings(getApplicationContext(), full_url, study_config, input_password_);

            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Sets first all the settings to the client.
     * If there are plugins, apply the same settings to them.
     * This allows us to add plugins to studies from the dashboard.
     *
     * @param context
     * @param configs
     */
    public static void applySettings(Context context, JSONArray configs, String input_password) {
        applySettings(context, Aware.getSetting(context, Aware_Preferences.WEBSERVICE_SERVER), configs, input_password);
    }

    /**
     * Sets first all the settings to the client.
     * If there are plugins, apply the same settings to them.
     * This allows us to add plugins to studies from the dashboard.
     *
     * @param context
     * @param webserviceServer
     * @param configs
     */
    public static void applySettings(Context context, String webserviceServer, JSONArray configs, String input_password) {
        applySettings(context, webserviceServer, configs, false, input_password);
    }

    /**
     * Sets first all the settings to the client.
     * If there are plugins, apply the same settings to them.
     * This allows us to add plugins to studies from the dashboard.
     *
     * @param context
     * @param webserviceServer
     * @param configs
     * @param insertCompliance true to insert a new compliance record (i.e. when updating a study)
     * @param input_password password for database if required
     */
    public static void applySettings(Context context, String webserviceServer, JSONArray configs, Boolean insertCompliance, String input_password) {
        boolean is_developer = Aware.getSetting(context, Aware_Preferences.DEBUG_FLAG).equals("true");

        //First reset the client to default settings...
        Aware.reset(context);

        input_password_ = input_password;
        if (is_developer) Aware.setSetting(context, Aware_Preferences.DEBUG_FLAG, true);

        //Now apply the new settings
        try {
            Aware.setSetting(context, Aware_Preferences.WEBSERVICE_SERVER, webserviceServer);
            JSONObject studyConfig = configs.getJSONObject(0);  // use first config
            JSONObject studyInfo = studyConfig.optJSONObject("study_info");

            if (studyInfo == null) {
                Log.e(Aware.TAG, "Study info is missing or invalid in configuration");
                return;
            }

            // Set database settings
            try {
                JSONObject dbConfig = studyConfig.optJSONObject("database");
                if (dbConfig != null) {
                    Aware.setSetting(context, Aware_Preferences.DB_HOST, dbConfig.optString("database_host", ""));
                    Aware.setSetting(context, Aware_Preferences.DB_PORT, dbConfig.optInt("database_port", 3306));
                    Aware.setSetting(context, Aware_Preferences.DB_NAME, dbConfig.optString("database_name", ""));
                    Aware.setSetting(context, Aware_Preferences.DB_USERNAME, dbConfig.optString("database_username", ""));

                    boolean configWithoutPassword = dbConfig.optBoolean("config_without_password", false);
                    if (!configWithoutPassword) {
                        Aware.setSetting(context, Aware_Preferences.DB_PASSWORD, dbConfig.optString("database_password", ""));
                    } else {
                        Aware.setSetting(context, Aware_Preferences.DB_PASSWORD, input_password);
                    }
                } else {
                    Log.e(Aware.TAG, "Database configuration is missing");
                }
            } catch (Exception e) {
                Log.e(Aware.TAG, "Error setting database configuration: " + e.getMessage());
            }

            // Set study information
            if (insertCompliance) {
                try {
                    ContentValues studyData = new ContentValues();
                    studyData.put(Aware_Provider.Aware_Studies.STUDY_DEVICE_ID, Aware.getSetting(context, Aware_Preferences.DEVICE_ID));
                    studyData.put(Aware_Provider.Aware_Studies.STUDY_TIMESTAMP, System.currentTimeMillis());
                    studyData.put(Aware_Provider.Aware_Studies.STUDY_API, "");
                    studyData.put(Aware_Provider.Aware_Studies.STUDY_URL, webserviceServer);
                    studyData.put(Aware_Provider.Aware_Studies.STUDY_CONFIG, studyConfig.toString());
                    studyData.put(Aware_Provider.Aware_Studies.STUDY_KEY, "0");
                    studyData.put(Aware_Provider.Aware_Studies.STUDY_PI,
                            studyInfo.optString("researcher_first", "") + " " +
                                    studyInfo.optString("researcher_last", "") + "\nContact: " +
                                    studyInfo.optString("researcher_contact", ""));
                    studyData.put(Aware_Provider.Aware_Studies.STUDY_TITLE,
                            studyInfo.optString("study_title", studyInfo.optString("study_name", "Unknown Study")));
                    studyData.put(Aware_Provider.Aware_Studies.STUDY_DESCRIPTION,
                            studyInfo.optString("study_description", ""));
                    studyData.put(Aware_Provider.Aware_Studies.STUDY_COMPLIANCE, "updated study");
                    studyData.put(Aware_Provider.Aware_Studies.STUDY_JOINED, System.currentTimeMillis());
                    studyData.put(Aware_Provider.Aware_Studies.STUDY_EXIT, 0);
                    context.getContentResolver().insert(Aware_Provider.Aware_Studies.CONTENT_URI, studyData);
                } catch (Exception e) {
                    Log.e(Aware.TAG, "Error inserting study compliance: " + e.getMessage());
                }
            }
        } catch (JSONException e) {
            Log.e(Aware.TAG, "Error parsing study configuration: " + e.getMessage());
            return;
        }

        // Initialize arrays for different configuration types
        JSONArray plugins = new JSONArray();
        JSONArray sensors = new JSONArray();
        JSONArray schedulers = new JSONArray();
        JSONArray esm_schedules = new JSONArray();
        JSONArray questions = new JSONArray();

        // Extract configuration elements
        for (int i = 0; i < configs.length(); i++) {
            try {
                JSONObject element = configs.getJSONObject(i);

                // Extract plugins
                if (element.has("plugins") && !element.isNull("plugins")) {
                    plugins = element.getJSONArray("plugins");
                }

                // Extract sensors
                if (element.has("sensors") && !element.isNull("sensors")) {
                    sensors = element.getJSONArray("sensors");
                }

                // Extract schedulers
                if (element.has("schedulers") && !element.isNull("schedulers")) {
                    schedulers = element.getJSONArray("schedulers");
                }

                // Extract ESM schedules and questions
                if (element.has("schedules") && !element.isNull("schedules")) {
                    esm_schedules = element.getJSONArray("schedules");
                }

                if (element.has("questions") && !element.isNull("questions")) {
                    questions = element.getJSONArray("questions");
                }
            } catch (JSONException e) {
                Log.e(Aware.TAG, "Error parsing configuration element: " + e.getMessage());
            }
        }

        // Set the sensors' settings first
        processSensorSettings(context, sensors);

        // Set the plugins' settings and prepare for activation
        ArrayList<String> active_plugins = processPluginSettings(context, plugins);

        // Process ESM settings if available
        if (questions.length() > 0 || esm_schedules.length() > 0) {
            try {
                processEsmSettings(context, questions, esm_schedules);
            } catch (Exception e) {
                Log.e(Aware.TAG, "Error processing ESM settings: " + e.getMessage(), e);
                // Continue with other settings even if ESM fails
            }
        }

        // Set other schedulers
        if (schedulers.length() > 0) {
            try {
                Scheduler.setSchedules(context, schedulers);
            } catch (Exception e) {
                Log.e(Aware.TAG, "Error setting schedulers: " + e.getMessage());
            }
        }

        // Activate plugins
        for (String package_name : active_plugins) {
            try {
                PackageInfo installed = PluginsManager.isInstalled(context, package_name);
                if (installed != null) {
                    Aware.startPlugin(context, package_name);
                } else {
                    Aware.downloadPlugin(context, package_name, null, false);
                }
            } catch (Exception e) {
                Log.e(Aware.TAG, "Error activating plugin " + package_name + ": " + e.getMessage());
            }
        }

        // Start Aware service and sync data
        Intent aware = new Intent(context, Aware.class);
        context.startService(aware);

        Intent sync = new Intent(Aware.ACTION_AWARE_SYNC_DATA);
        context.sendBroadcast(sync);
    }

    /**
     * Process and apply plugin settings from configuration
     *
     * @param context Application context
     * @param plugins JSONArray of plugin configurations
     * @return ArrayList of active plugin package names
     */
    private static ArrayList<String> processPluginSettings(Context context, JSONArray plugins) {
        ArrayList<String> active_plugins = new ArrayList<>();
        if (plugins == null) return active_plugins;

        for (int i = 0; i < plugins.length(); i++) {
            try {
                JSONObject plugin_config = plugins.getJSONObject(i);

                if (plugin_config.has("plugin")) {
                    String package_name = plugin_config.getString("plugin");
                    active_plugins.add(package_name);

                    // Apply plugin-specific settings if available
                    if (plugin_config.has("settings") && !plugin_config.isNull("settings")) {
                        JSONArray plugin_settings = plugin_config.getJSONArray("settings");
                        for (int j = 0; j < plugin_settings.length(); j++) {
                            try {
                                JSONObject plugin_setting = plugin_settings.getJSONObject(j);
                                if (plugin_setting.has("setting") && plugin_setting.has("value")) {
                                    String setting = plugin_setting.getString("setting");
                                    Object value = plugin_setting.get("value");

                                    // Apply setting based on value type
                                    if (value instanceof Boolean) {
                                        Aware.setSetting(context, setting, (Boolean) value, package_name);
                                    } else if (value instanceof Integer) {
                                        Aware.setSetting(context, setting, (Integer) value, package_name);
                                    } else if (value instanceof Double) {
                                        Aware.setSetting(context, setting, (Double) value, package_name);
                                    } else if (value instanceof String) {
                                        Aware.setSetting(context, setting, (String) value, package_name);
                                    }
                                }
                            } catch (JSONException e) {
                                Log.e(Aware.TAG, "Error processing plugin setting: " + e.getMessage());
                            }
                        }
                    }
                }
            } catch (JSONException e) {
                Log.e(Aware.TAG, "Error processing plugin: " + e.getMessage());
            }
        }

        return active_plugins;
    }

    /**
     * Process and apply sensor settings from configuration with extensive debug logging
     *
     * @param context Application context
     * @param sensors JSONArray of sensor configurations
     */
    private static void processSensorSettings(Context context, JSONArray sensors) {
        if (sensors == null) {
            Log.d(Aware.TAG, "processSensorSettings: sensors array is null");
            return;
        }

        Log.d(Aware.TAG, "processSensorSettings: Processing " + sensors.length() + " sensor settings");

        // Track all settings to verify they're applied correctly
        HashMap<String, String> appliedSettings = new HashMap<>();

        for (int i = 0; i < sensors.length(); i++) {
            try {
                JSONObject sensor_config = sensors.getJSONObject(i);
                Log.d(Aware.TAG, "processSensorSettings: Sensor #" + i + ": " + sensor_config.toString());

                if (sensor_config.has("setting") && sensor_config.has("value")) {
                    String setting = sensor_config.getString("setting");
                    Object value = sensor_config.get("value");
                    String valueType = value.getClass().getSimpleName();

                    Log.d(Aware.TAG, "processSensorSettings: Processing setting: " + setting +
                            " with value: " + value + " (type: " + valueType + ")");

                    // Apply setting based on value type
                    try {
                        if (value instanceof Boolean) {
                            Aware.setSetting(context, setting, (Boolean) value);
                        } else if (value instanceof Integer) {
                            Aware.setSetting(context, setting, (Integer) value);
                        } else if (value instanceof Double) {
                            Aware.setSetting(context, setting, (Double) value);
                        } else if (value instanceof Long) {
                            Aware.setSetting(context, setting, ((Long) value).intValue());
                        } else if (value instanceof String) {
                            Aware.setSetting(context, setting, (String) value);
                        } else {
                            // For any other type, convert to string
                            Aware.setSetting(context, setting, value.toString());
                        }
                        appliedSettings.put(setting, value.toString());
                        Log.d(Aware.TAG, "processSensorSettings: Successfully applied setting: " + setting);
                    } catch (Exception e) {
                        Log.e(Aware.TAG, "processSensorSettings: Error applying setting " + setting +
                                ": " + e.getMessage());
                    }
                } else {
                    Log.d(Aware.TAG, "processSensorSettings: Sensor config missing required fields");
                }
            } catch (JSONException e) {
                Log.e(Aware.TAG, "processSensorSettings: JSONException: " + e.getMessage());
            }
        }

        // Verify all settings were successfully applied
        Log.d(Aware.TAG, "processSensorSettings: Verifying " + appliedSettings.size() + " applied settings");
        for (Map.Entry<String, String> entry : appliedSettings.entrySet()) {
            String key = entry.getKey();
            String expectedValue = entry.getValue();
            String actualValue = Aware.getSetting(context, key);

            if (actualValue.equals(expectedValue)) {
                Log.d(Aware.TAG, "processSensorSettings: Verified setting: " + key +
                        " = " + actualValue + " ✓");
            } else {
                Log.e(Aware.TAG, "processSensorSettings: Setting verification failed for " + key +
                        ": expected=" + expectedValue + ", actual=" + actualValue + " ✗");
            }
        }
    }

    /**
     * Process and apply ESM settings from configuration with debug logging
     *
     * @param context Application context
     * @param questions JSONArray of ESM questions
     * @param schedules JSONArray of ESM schedules
     */
    private static void processEsmSettings(Context context, JSONArray questions, JSONArray schedules) {
        Log.d(Aware.TAG, "processEsmSettings: Starting ESM processing");

        if (questions == null) {
            Log.e(Aware.TAG, "processEsmSettings: questions array is null");
            return;
        }

        if (schedules == null) {
            Log.e(Aware.TAG, "processEsmSettings: schedules array is null");
            return;
        }

        Log.d(Aware.TAG, "processEsmSettings: Found " + questions.length() + " questions and " +
                schedules.length() + " schedules");

        // Log details about each question
        for (int i = 0; i < questions.length(); i++) {
            try {
                JSONObject questionJson = questions.getJSONObject(i);
                String questionId = questionJson.optString("id", "unknown");
                String questionType = questionJson.optString("esm_type", "unknown");
                String questionTitle = questionJson.optString("esm_title", "unknown");

                Log.d(Aware.TAG, "processEsmSettings: Question #" + i +
                        ": id=" + questionId +
                        ", type=" + questionType +
                        ", title=" + questionTitle);

                Log.d(Aware.TAG, "processEsmSettings: Full question: " + questionJson.toString());
            } catch (JSONException e) {
                Log.e(Aware.TAG, "processEsmSettings: Error processing question #" + i + ": " + e.getMessage());
            }
        }

        // Log details about each schedule
        for (int i = 0; i < schedules.length(); i++) {
            try {
                JSONObject scheduleJson = schedules.getJSONObject(i);
                String scheduleTitle = scheduleJson.optString("title", "unknown");
                String scheduleType = scheduleJson.optString("type", "unknown");

                Log.d(Aware.TAG, "processEsmSettings: Schedule #" + i +
                        ": title=" + scheduleTitle +
                        ", type=" + scheduleType);

                // Log the question IDs for this schedule
                if (scheduleJson.has("questions")) {
                    Object questionsObj = scheduleJson.get("questions");
                    if (questionsObj instanceof JSONArray) {
                        JSONArray questionIds = (JSONArray) questionsObj;
                        StringBuilder idsStr = new StringBuilder();
                        for (int j = 0; j < questionIds.length(); j++) {
                            idsStr.append(questionIds.get(j)).append(", ");
                        }
                        Log.d(Aware.TAG, "processEsmSettings: Schedule questions: " + idsStr);
                    } else {
                        Log.d(Aware.TAG, "processEsmSettings: Schedule has single question: " + questionsObj);
                    }
                } else {
                    Log.e(Aware.TAG, "processEsmSettings: Schedule missing questions field");
                }

                Log.d(Aware.TAG, "processEsmSettings: Full schedule: " + scheduleJson.toString());
            } catch (JSONException e) {
                Log.e(Aware.TAG, "processEsmSettings: Error processing schedule #" + i + ": " + e.getMessage());
            }
        }

        // Create a map of question objects by their IDs
        HashMap<String, JSONObject> esm_questions = new HashMap<>();
        for (int i = 0; i < questions.length(); i++) {
            try {
                JSONObject questionJson = questions.getJSONObject(i);
                if (questionJson.has("id")) {
                    String questionId = questionJson.getString("id");
                    JSONObject esmWrapper = new JSONObject().put("esm", questionJson);
                    esm_questions.put(questionId, esmWrapper);
                    Log.d(Aware.TAG, "processEsmSettings: Mapped question ID " + questionId +
                            " to object: " + esmWrapper.toString());
                } else {
                    Log.e(Aware.TAG, "processEsmSettings: Question without ID: " + questionJson.toString());
                }
            } catch (JSONException e) {
                Log.e(Aware.TAG, "processEsmSettings: Error mapping question: " + e.getMessage());
            }
        }

        // Process each schedule
        for (int i = 0; i < schedules.length(); i++) {
            try {
                JSONObject scheduleJson = schedules.getJSONObject(i);
                Log.d(Aware.TAG, "processEsmSettings: Processing schedule: " + scheduleJson.optString("title", "unknown"));

                // Skip if no questions array or not of the expected type
                if (!scheduleJson.has("questions") || scheduleJson.isNull("questions")) {
                    Log.e(Aware.TAG, "processEsmSettings: Schedule missing questions array");
                    continue;
                }

                // Get question IDs from the schedule
                JSONArray questionIds;
                try {
                    questionIds = scheduleJson.getJSONArray("questions");
                    Log.d(Aware.TAG, "processEsmSettings: Found questions array with " +
                            questionIds.length() + " items");
                } catch (JSONException e) {
                    // Try to handle case where questions might be a single value instead of array
                    try {
                        Object questionObj = scheduleJson.get("questions");
                        questionIds = new JSONArray();
                        questionIds.put(questionObj);
                        Log.d(Aware.TAG, "processEsmSettings: Converted single question to array");
                    } catch (Exception ex) {
                        Log.e(Aware.TAG, "processEsmSettings: Invalid questions format: " + e.getMessage());
                        continue;
                    }
                }

                // Build question objects array for this schedule
                JSONArray questionObjects = new JSONArray();
                for (int j = 0; j < questionIds.length(); j++) {
                    try {
                        String questionId = String.valueOf(questionIds.get(j));
                        JSONObject questionObj = esm_questions.get(questionId);

                        if (questionObj != null) {
                            questionObjects.put(questionObj);
                            Log.d(Aware.TAG, "processEsmSettings: Added question ID " + questionId +
                                    " to schedule");
                        } else {
                            Log.e(Aware.TAG, "processEsmSettings: Question ID " + questionId +
                                    " not found in questions map");
                        }
                    } catch (JSONException e) {
                        Log.e(Aware.TAG, "processEsmSettings: Error adding question to schedule: " + e.getMessage());
                    }
                }

                // Create schedule if there are questions to add
                if (questionObjects.length() > 0) {
                    Log.d(Aware.TAG, "processEsmSettings: Creating schedule with " +
                            questionObjects.length() + " questions");
                    createEsmSchedule(context, scheduleJson, questionObjects);
                } else {
                    Log.e(Aware.TAG, "processEsmSettings: No valid questions for schedule");
                }
            } catch (JSONException e) {
                Log.e(Aware.TAG, "processEsmSettings: Error processing schedule: " + e.getMessage());
            }
        }

        Log.d(Aware.TAG, "processEsmSettings: Completed ESM processing");
    }

    /**
     * Creates a schedule for triggering ESMs with debug logging
     *
     * @param context Application context
     * @param scheduleJson JSONObject representing the schedule
     * @param esmsArray JSONArray representing the ESM questions
     */
    private static void createEsmSchedule(Context context, JSONObject scheduleJson, JSONArray esmsArray) {
        try {
            String scheduleTitle = scheduleJson.optString("title", "unnamed");
            Log.d(Aware.TAG, "createEsmSchedule: Creating schedule: " + scheduleTitle);

            // Get required schedule properties with defaults
            String type = scheduleJson.optString("type", "");
            Log.d(Aware.TAG, "createEsmSchedule: Schedule type: " + type);

            // Handle different types of "esm_keep" field (could be string or boolean)
            String keep;
            try {
                boolean keepBool = scheduleJson.getBoolean("esm_keep");
                keep = String.valueOf(keepBool);
                Log.d(Aware.TAG, "createEsmSchedule: Found esm_keep as boolean: " + keep);
            } catch (Exception e) {
                keep = scheduleJson.optString("esm_keep", "false");
                Log.d(Aware.TAG, "createEsmSchedule: Found esm_keep as string: " + keep);

                // Handle potential typo in the field name (seen in some configs)
                if (keep.equals("false") && scheduleJson.has("esm_kepp")) {
                    keep = scheduleJson.optString("esm_kepp", "false");
                    Log.d(Aware.TAG, "createEsmSchedule: Found esm_kepp (typo): " + keep);
                }
            }

            // Create schedule object
            Scheduler.Schedule schedule = new Scheduler.Schedule(scheduleTitle);

            // Set schedule parameters based on type
            switch (type.toLowerCase()) {
                case "interval":
                    Log.d(Aware.TAG, "createEsmSchedule: Configuring interval schedule");
                    try {
                        // Add days if available
                        if (scheduleJson.has("days") && !scheduleJson.isNull("days")) {
                            JSONArray days = scheduleJson.getJSONArray("days");
                            for (int i = 0; i < days.length(); i++) {
                                String day = days.getString(i);
                                schedule.addWeekday(day);
                                Log.d(Aware.TAG, "createEsmSchedule: Added day: " + day);
                            }
                        } else {
                            Log.d(Aware.TAG, "createEsmSchedule: No days specified");
                        }

                        // Add hours if available
                        if (scheduleJson.has("hours") && !scheduleJson.isNull("hours")) {
                            JSONArray hours = scheduleJson.getJSONArray("hours");
                            for (int i = 0; i < hours.length(); i++) {
                                int hour = hours.getInt(i);
                                schedule.addHour(hour);
                                Log.d(Aware.TAG, "createEsmSchedule: Added hour: " + hour);
                            }
                        } else {
                            Log.d(Aware.TAG, "createEsmSchedule: No hours specified");
                        }
                    } catch (Exception e) {
                        Log.e(Aware.TAG, "createEsmSchedule: Error setting interval: " + e.getMessage());
                    }
                    break;

                case "random":
                    Log.d(Aware.TAG, "createEsmSchedule: Configuring random schedule");
                    try {
                        // Parse first hour
                        String firstHourString = scheduleJson.optString("firsthour", "9:00");
                        Log.d(Aware.TAG, "createEsmSchedule: First hour string: " + firstHourString);

                        int firstHour;
                        try {
                            firstHour = Integer.parseInt(firstHourString.split(":")[0]);
                        } catch (Exception e) {
                            firstHour = 9; // Default to 9 AM if parsing fails
                            Log.e(Aware.TAG, "createEsmSchedule: Error parsing firsthour, using default: " + e.getMessage());
                        }

                        // Parse last hour
                        String lastHourString = scheduleJson.optString("lasthour", "21:00");
                        Log.d(Aware.TAG, "createEsmSchedule: Last hour string: " + lastHourString);

                        int lastHour;
                        try {
                            lastHour = Integer.parseInt(lastHourString.split(":")[0]);
                        } catch (Exception e) {
                            lastHour = 21; // Default to 9 PM if parsing fails
                            Log.e(Aware.TAG, "createEsmSchedule: Error parsing lasthour, using default: " + e.getMessage());
                        }

                        int randomCount = scheduleJson.optInt("randomCount", 1);
                        int randomInterval = scheduleJson.optInt("randomInterval", 30);

                        Log.d(Aware.TAG, "createEsmSchedule: Random parameters - firstHour: " + firstHour +
                                ", lastHour: " + lastHour +
                                ", count: " + randomCount +
                                ", interval: " + randomInterval);

                        // Add hours and random parameters
                        schedule.addHour(firstHour)
                                .addHour(lastHour)
                                .random(randomCount, randomInterval);
                    } catch (Exception e) {
                        Log.e(Aware.TAG, "createEsmSchedule: Error setting random schedule: " + e.getMessage());
                    }
                    break;

                case "repeat":
                    Log.d(Aware.TAG, "createEsmSchedule: Configuring repeat schedule");
                    try {
                        int repeatInterval = scheduleJson.optInt("repeatInterval", 60);
                        Log.d(Aware.TAG, "createEsmSchedule: Repeat interval: " + repeatInterval);
                        schedule.setInterval(repeatInterval);
                    } catch (Exception e) {
                        Log.e(Aware.TAG, "createEsmSchedule: Error setting repeat interval: " + e.getMessage());
                    }
                    break;

                default:
                    Log.e(Aware.TAG, "createEsmSchedule: Unknown schedule type: " + type);
                    return;
            }

            // Set trigger for ESMs as the schedule's title
            Log.d(Aware.TAG, "createEsmSchedule: Setting trigger and keep for " + esmsArray.length() + " ESMs");
            for (int i = 0; i < esmsArray.length(); i++) {
                try {
                    JSONObject esmObj = esmsArray.getJSONObject(i);
                    JSONObject esm = esmObj.getJSONObject("esm");

                    esm.put(ESM_Question.esm_trigger, scheduleTitle);
                    esm.put(ESM_Question.esm_keep, keep);

                    Log.d(Aware.TAG, "createEsmSchedule: Set trigger=" + scheduleTitle +
                            " and keep=" + keep + " for ESM #" + i);
                } catch (JSONException e) {
                    Log.e(Aware.TAG, "createEsmSchedule: Error setting ESM trigger: " + e.getMessage());
                }
            }

            // Save the schedule
            try {
                schedule.setActionType(Scheduler.ACTION_TYPE_BROADCAST)
                        .setActionIntentAction(ESM.ACTION_AWARE_QUEUE_ESM)
                        .addActionExtra(ESM.EXTRA_ESM, esmsArray.toString());

                Scheduler.saveSchedule(context, schedule);
                Log.d(Aware.TAG, "createEsmSchedule: Successfully saved schedule: " + scheduleTitle);
            } catch (Exception e) {
                Log.e(Aware.TAG, "createEsmSchedule: Error saving schedule: " + e.getMessage(), e);
            }
        } catch (Exception e) {
            Log.e(Aware.TAG, "createEsmSchedule: Error creating ESM schedule: " + e.getMessage(), e);
        }
    }

    /**
     * Synchronizes the study configuration with the server
     *
     * @param context Application context
     * @param toast Whether to show toast messages
     */
    public static void syncStudyConfig(Context context, Boolean toast) {
        if (!Aware.isStudy(context)) return;

        String studyUrl = Aware.getSetting(context, Aware_Preferences.WEBSERVICE_SERVER);
        Cursor study = Aware.getStudy(context,
                Aware.getSetting(context, Aware_Preferences.WEBSERVICE_SERVER));

        if (study != null && study.moveToFirst()) {
            try {
                JSONObject localConfig = new JSONObject(study.getString(
                        study.getColumnIndex(Aware_Provider.Aware_Studies.STUDY_CONFIG)));
                JSONObject newConfig = getStudyConfig(studyUrl);
                if (!validateStudyConfig(context, newConfig, Aware.getSetting(context, Aware_Preferences.DB_PASSWORD))) {
                    String msg = "Failed to sync study, something is wrong with the config.";
                    Log.e(Aware.TAG, msg);
                    if (toast) {
                        new Handler(Looper.getMainLooper()).post(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                    return;
                }
                if (jsonEquals(localConfig, newConfig, false)) {
                    String msg = "There are no study updates.";
                    if (Aware.DEBUG) Aware.debug(context, msg);
                    if (toast) {
                        new Handler(Looper.getMainLooper()).post(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                    return;
                }
                applySettings(context, studyUrl, new JSONArray().put(newConfig), true, Aware.getSetting(context, Aware_Preferences.DB_PASSWORD));
                if (Aware.DEBUG) Aware.debug(context, "Updated study config: " + newConfig);
                if (toast) {
                    new Handler(Looper.getMainLooper()).post(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(context, "Study was updated.", Toast.LENGTH_SHORT).show();
                        }
                    });
                }

                // TODO RIO: Update last sync date

                // Notify the user that study config has been updated
                Intent intent = new Intent()
                        .setComponent(new ComponentName("com.aware.phone", "com.aware.phone.ui.Aware_Light_Client"))
                        .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                PendingIntent clickIntent = PendingIntent.getActivity(context, 0, intent, 0);

                NotificationCompat.Builder builder = new NotificationCompat.Builder(context, Aware.AWARE_NOTIFICATION_CHANNEL_GENERAL)
                        .setChannelId(Aware.AWARE_NOTIFICATION_CHANNEL_GENERAL)
                        .setContentIntent(clickIntent)
                        .setSmallIcon(R.drawable.ic_stat_aware_accessibility)
                        .setAutoCancel(true)
                        .setContentTitle(context.getResources().getString(R.string.aware_notif_study_sync_title))
                        .setContentText(context.getResources().getString(R.string.aware_notif_study_sync));
                builder = Aware.setNotificationProperties(builder, Aware.AWARE_NOTIFICATION_IMPORTANCE_GENERAL);

                NotificationManager notManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
                notManager.notify(Applications.ACCESSIBILITY_NOTIFICATION_ID, builder.build());
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Retrieves a study config from a file hosted online.
     *
     * @param studyUrl direct download link to the file or a link to the shared file (via Google
     *                 drive or Dropbox)
     * @return JSONObject representing the study config
     */
    public static JSONObject getStudyConfig(String studyUrl) throws JSONException {
        // Convert shared links from Google drive and Dropbox into direct download URLs
        if (studyUrl.contains("drive.google.com")) {
            String patternStr = studyUrl.contains("drive.google.com/file") ?
                    "(?<=\\/d\\/).*(?=\\/)" : "(?<=id=).*";
            Pattern pattern = Pattern.compile(patternStr);
            Matcher matcher = pattern.matcher(studyUrl);
            if (matcher.find()) {
                String fileId = matcher.group(0);
                studyUrl = "https://drive.google.com/uc?export=download&id=" + fileId;
            }
        } else if (studyUrl.contains("www.dropbox.com")) {
            studyUrl = studyUrl.replace("www.dropbox.com", "dl.dropboxusercontent.com");
        }

        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder().url(studyUrl).build();

        try (Response response = client.newCall(request).execute()) {
            String responseStr = response.body().string();
            JSONObject responseJson = new JSONObject(responseStr);
            return responseJson;
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * Validates that the study config has the correct JSON schema for AWARE.
     * It needs to have the keys: "database", "sensors" and "study_info".
     *
     * @param context application context
     * @param config JSON representing a study configuration
     * @return true if the study config is valid, false otherwise
     */
    public static boolean validateStudyConfig(Context context, JSONObject config, String input_password) {
        if (config == null) {
            Log.e(Aware.TAG, "Study configuration is null");
            return false;
        }

        // Check for required keys
        for (String key: REQUIRED_STUDY_CONFIG_KEYS) {
            if (!config.has(key)) {
                Log.e(Aware.TAG, "Study configuration missing required key: " + key);
                return false;
            }
        }

        // Test database connection
        try {
            JSONObject dbInfo = config.getJSONObject("database");
            return Jdbc.testConnection(
                    dbInfo.getString("database_host"),
                    dbInfo.getString("database_port"),
                    dbInfo.getString("database_name"),
                    dbInfo.getString("database_username"),
                    dbInfo.getString("database_password"),
                    dbInfo.optBoolean("config_without_password", false),
                    input_password);
        } catch (JSONException e) {
            Log.e(Aware.TAG, "Error validating database configuration: " + e.getMessage());
            return false;
        }
    }

    /**
     * Compares two JSON objects for equality
     *
     * @param obj1 First JSON object
     * @param obj2 Second JSON object
     * @param strict Whether to perform strict comparison
     * @return true if the objects are equal, false otherwise
     */
    private static boolean jsonEquals(JSONObject obj1, JSONObject obj2, boolean strict) {
        try {
            JSONAssert.assertEquals(obj1, obj2, strict);
            return true;
        } catch (JSONException | AssertionError e) {
            return false;
        }
    }
}