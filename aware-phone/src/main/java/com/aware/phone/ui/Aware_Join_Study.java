package com.aware.phone.ui;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.*;
import android.content.pm.PackageInfo;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.Editable;
import android.text.Html;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.aware.Aware;
import com.aware.Aware_Preferences;
import com.aware.phone.R;
import com.aware.phone.ui.dialogs.JoinStudyDialog;
import com.aware.phone.utils.AwareUtil;
import com.aware.providers.Aware_Provider;
import com.aware.utils.*;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

public class Aware_Join_Study extends Aware_Activity {

    private ArrayList<PluginInfo> active_plugins;
    private ArrayList<SensorInfo> active_sensors;

    private RecyclerView pluginsRecyclerView;
    private RecyclerView sensorsRecyclerView;
    private RecyclerView.Adapter mPluginsAdapter;
    private RecyclerView.Adapter mSensorsAdapter;
    private RecyclerView.LayoutManager mPluginsLayoutManager;
    private RecyclerView.LayoutManager mSensorsLayoutManager;
    private boolean pluginsInstalled;
    private Button btnAction, btnQuit;
    private LinearLayout llPluginsRequired;

    public static final String EXTRA_STUDY_URL = "study_url";
    public static final String EXTRA_STUDY_CONFIG = "study_config";
    public static final String INPUT_PASSWORD = "input_password";



    private static String study_url;
    private JSONArray study_configs;
    private static String input_password;




    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.aware_join_study);

        pluginsInstalled = false;

        EditText participant_label = findViewById(R.id.participant_label);
        participant_label.setText(Aware.getSetting(getApplicationContext(), Aware_Preferences.DEVICE_LABEL));
        participant_label.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                Aware.setSetting(getApplicationContext(), Aware_Preferences.DEVICE_LABEL, s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        pluginsRecyclerView = (RecyclerView) findViewById(R.id.rv_plugins);
        sensorsRecyclerView = (RecyclerView) findViewById(R.id.rv_sensors);
        mPluginsLayoutManager = new LinearLayoutManager(this);
        mSensorsLayoutManager = new LinearLayoutManager(this);
        pluginsRecyclerView.setLayoutManager(mPluginsLayoutManager);
        sensorsRecyclerView.setLayoutManager(mSensorsLayoutManager);

        llPluginsRequired = (LinearLayout) findViewById(R.id.ll_plugins_required);

        study_url = getIntent().getStringExtra(EXTRA_STUDY_URL);
        String studyConfigStr = getIntent().getStringExtra(EXTRA_STUDY_CONFIG);
        input_password = getIntent().getStringExtra(INPUT_PASSWORD);

        //If we are getting here from an AWARE study link (deeplink)
        String scheme = getIntent().getScheme();
        if (scheme != null) {
            if (Aware.DEBUG)
                Log.d(Aware.TAG, "AWARE Link detected: " + getIntent().getDataString() + " SCHEME: " + scheme);
            if (scheme.equalsIgnoreCase("aware")) {
                study_url = getIntent().getDataString().replace("aware://", "https://");
            }
        }

        if (Aware.DEBUG) Log.d(Aware.TAG, "Study URL:" + study_url);

        // Fetch study config if not passed in intent
        if (studyConfigStr == null) {
            new JoinStudyDialog(Aware_Join_Study.this).validateStudy(study_url);  // TODO TEST 1
        } else {
            Cursor qry = Aware.getStudy(this, study_url);
            if (qry == null || !qry.moveToFirst()) {
                new PopulateStudy().execute(study_url, studyConfigStr);
            } else {
                initView(qry, studyConfigStr);
            }

            IntentFilter pluginStatuses = new IntentFilter();
            pluginStatuses.addAction(Aware.ACTION_AWARE_PLUGIN_INSTALLED);
            pluginStatuses.addAction(Aware.ACTION_AWARE_PLUGIN_UNINSTALLED);
            registerReceiver(pluginCompliance, pluginStatuses);
        }
    }

    private void initView(Cursor qry, String studyConfig) {
        TextView txtStudyTitle = findViewById(R.id.txt_title);
        TextView txtStudyDescription = findViewById(R.id.txt_description);
        TextView txtStudyResearcher = findViewById(R.id.txt_researcher);
        btnAction = findViewById(R.id.btn_sign_up);
        btnQuit = findViewById(R.id.btn_quit_study);

        // Quitting a study will be done through the main activity - Aware_Light_Client.java
        btnQuit.setVisibility(View.GONE);

        try {
            study_configs = new JSONArray().put(new JSONObject(studyConfig));
//                study_configs = new JSONArray().put(new JSONObject(qry.getString(qry.getColumnIndex(Aware_Provider.Aware_Studies.STUDY_CONFIG))));
            txtStudyTitle.setText(qry.getString(qry.getColumnIndex(Aware_Provider.Aware_Studies.STUDY_TITLE)));
            txtStudyDescription.setText(Html.fromHtml(qry.getString(qry.getColumnIndex(Aware_Provider.Aware_Studies.STUDY_DESCRIPTION)), null, null));
            txtStudyResearcher.setText(qry.getString(qry.getColumnIndex(Aware_Provider.Aware_Studies.STUDY_PI)));
        } catch (JSONException e) {
            e.printStackTrace();
        }

        if (!qry.isClosed()) qry.close();

        if (study_configs != null) {
            populateStudyInfo(study_configs);
        }

        btnAction.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                btnAction.setEnabled(false);
                btnAction.setAlpha(0.5f);

                Cursor study = Aware.getStudy(getApplicationContext(), study_url);
                if (study != null && study.moveToFirst()) {
                    ContentValues studyData = new ContentValues();
                    studyData.put(Aware_Provider.Aware_Studies.STUDY_JOINED, System.currentTimeMillis());
                    studyData.put(Aware_Provider.Aware_Studies.STUDY_EXIT, 0);
                    getContentResolver().update(Aware_Provider.Aware_Studies.CONTENT_URI, studyData, Aware_Provider.Aware_Studies.STUDY_URL + " LIKE '" + study_url + "'", null);
                }
                if (study != null && !study.isClosed()) study.close();

                new JoinStudyAsync().execute();
            }
        });

        btnQuit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                Cursor dbStudy = Aware.getStudy(getApplicationContext(), study_url);
                if (dbStudy != null && dbStudy.moveToFirst()) {
                    ContentValues complianceEntry = new ContentValues();
                    complianceEntry.put(Aware_Provider.Aware_Studies.STUDY_TIMESTAMP, System.currentTimeMillis());
                    complianceEntry.put(Aware_Provider.Aware_Studies.STUDY_DEVICE_ID, Aware.getSetting(getApplicationContext(), Aware_Preferences.DEVICE_ID));
                    complianceEntry.put(Aware_Provider.Aware_Studies.STUDY_KEY, dbStudy.getInt(dbStudy.getColumnIndex(Aware_Provider.Aware_Studies.STUDY_KEY)));
                    complianceEntry.put(Aware_Provider.Aware_Studies.STUDY_API, dbStudy.getString(dbStudy.getColumnIndex(Aware_Provider.Aware_Studies.STUDY_API)));
                    complianceEntry.put(Aware_Provider.Aware_Studies.STUDY_URL, dbStudy.getString(dbStudy.getColumnIndex(Aware_Provider.Aware_Studies.STUDY_URL)));
                    complianceEntry.put(Aware_Provider.Aware_Studies.STUDY_PI, dbStudy.getString(dbStudy.getColumnIndex(Aware_Provider.Aware_Studies.STUDY_PI)));
                    complianceEntry.put(Aware_Provider.Aware_Studies.STUDY_CONFIG, dbStudy.getString(dbStudy.getColumnIndex(Aware_Provider.Aware_Studies.STUDY_CONFIG)));
                    complianceEntry.put(Aware_Provider.Aware_Studies.STUDY_JOINED, dbStudy.getLong(dbStudy.getColumnIndex(Aware_Provider.Aware_Studies.STUDY_JOINED)));
                    complianceEntry.put(Aware_Provider.Aware_Studies.STUDY_EXIT, dbStudy.getLong(dbStudy.getColumnIndex(Aware_Provider.Aware_Studies.STUDY_EXIT)));
                    complianceEntry.put(Aware_Provider.Aware_Studies.STUDY_TITLE, dbStudy.getString(dbStudy.getColumnIndex(Aware_Provider.Aware_Studies.STUDY_TITLE)));
                    complianceEntry.put(Aware_Provider.Aware_Studies.STUDY_DESCRIPTION, dbStudy.getString(dbStudy.getColumnIndex(Aware_Provider.Aware_Studies.STUDY_DESCRIPTION)));
                    complianceEntry.put(Aware_Provider.Aware_Studies.STUDY_COMPLIANCE, "attempt to quit study");

                    getContentResolver().insert(Aware_Provider.Aware_Studies.CONTENT_URI, complianceEntry);
                }
                if (dbStudy != null && !dbStudy.isClosed()) dbStudy.close();

                new AlertDialog.Builder(Aware_Join_Study.this)
                        .setMessage("Are you sure you want to quit the study?")
                        .setCancelable(false)
                        .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                btnQuit.setEnabled(false);
                                btnQuit.setAlpha(1f);
                                btnAction.setEnabled(false);
                                btnAction.setAlpha(1f);

                                Cursor dbStudy = Aware.getStudy(getApplicationContext(), Aware.getSetting(getApplicationContext(), Aware_Preferences.WEBSERVICE_SERVER));
                                if (dbStudy != null && dbStudy.moveToFirst()) {
                                    ContentValues complianceEntry = new ContentValues();
                                    complianceEntry.put(Aware_Provider.Aware_Studies.STUDY_TIMESTAMP, System.currentTimeMillis());
                                    complianceEntry.put(Aware_Provider.Aware_Studies.STUDY_DEVICE_ID, Aware.getSetting(getApplicationContext(), Aware_Preferences.DEVICE_ID));
                                    complianceEntry.put(Aware_Provider.Aware_Studies.STUDY_KEY, dbStudy.getInt(dbStudy.getColumnIndex(Aware_Provider.Aware_Studies.STUDY_KEY)));
                                    complianceEntry.put(Aware_Provider.Aware_Studies.STUDY_API, dbStudy.getString(dbStudy.getColumnIndex(Aware_Provider.Aware_Studies.STUDY_API)));
                                    complianceEntry.put(Aware_Provider.Aware_Studies.STUDY_URL, dbStudy.getString(dbStudy.getColumnIndex(Aware_Provider.Aware_Studies.STUDY_URL)));
                                    complianceEntry.put(Aware_Provider.Aware_Studies.STUDY_PI, dbStudy.getString(dbStudy.getColumnIndex(Aware_Provider.Aware_Studies.STUDY_PI)));
                                    complianceEntry.put(Aware_Provider.Aware_Studies.STUDY_CONFIG, dbStudy.getString(dbStudy.getColumnIndex(Aware_Provider.Aware_Studies.STUDY_CONFIG)));
                                    complianceEntry.put(Aware_Provider.Aware_Studies.STUDY_JOINED, dbStudy.getLong(dbStudy.getColumnIndex(Aware_Provider.Aware_Studies.STUDY_JOINED)));
                                    complianceEntry.put(Aware_Provider.Aware_Studies.STUDY_EXIT, System.currentTimeMillis());
                                    complianceEntry.put(Aware_Provider.Aware_Studies.STUDY_TITLE, dbStudy.getString(dbStudy.getColumnIndex(Aware_Provider.Aware_Studies.STUDY_TITLE)));
                                    complianceEntry.put(Aware_Provider.Aware_Studies.STUDY_DESCRIPTION, dbStudy.getString(dbStudy.getColumnIndex(Aware_Provider.Aware_Studies.STUDY_DESCRIPTION)));
                                    complianceEntry.put(Aware_Provider.Aware_Studies.STUDY_COMPLIANCE, "quit study");

                                    getContentResolver().insert(Aware_Provider.Aware_Studies.CONTENT_URI, complianceEntry);
                                }
                                if (dbStudy != null && !dbStudy.isClosed()) dbStudy.close();

                                dialogInterface.dismiss();

                                new QuitStudyAsync().execute();
                            }
                        })
                        .setNegativeButton("No", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                Cursor dbStudy = Aware.getStudy(getApplicationContext(), Aware.getSetting(getApplicationContext(), Aware_Preferences.WEBSERVICE_SERVER));
                                if (dbStudy != null && dbStudy.moveToFirst()) {
                                    ContentValues complianceEntry = new ContentValues();
                                    complianceEntry.put(Aware_Provider.Aware_Studies.STUDY_TIMESTAMP, System.currentTimeMillis());
                                    complianceEntry.put(Aware_Provider.Aware_Studies.STUDY_DEVICE_ID, Aware.getSetting(getApplicationContext(), Aware_Preferences.DEVICE_ID));
                                    complianceEntry.put(Aware_Provider.Aware_Studies.STUDY_KEY, dbStudy.getInt(dbStudy.getColumnIndex(Aware_Provider.Aware_Studies.STUDY_KEY)));
                                    complianceEntry.put(Aware_Provider.Aware_Studies.STUDY_API, dbStudy.getString(dbStudy.getColumnIndex(Aware_Provider.Aware_Studies.STUDY_API)));
                                    complianceEntry.put(Aware_Provider.Aware_Studies.STUDY_URL, dbStudy.getString(dbStudy.getColumnIndex(Aware_Provider.Aware_Studies.STUDY_URL)));
                                    complianceEntry.put(Aware_Provider.Aware_Studies.STUDY_PI, dbStudy.getString(dbStudy.getColumnIndex(Aware_Provider.Aware_Studies.STUDY_PI)));
                                    complianceEntry.put(Aware_Provider.Aware_Studies.STUDY_CONFIG, dbStudy.getString(dbStudy.getColumnIndex(Aware_Provider.Aware_Studies.STUDY_CONFIG)));
                                    complianceEntry.put(Aware_Provider.Aware_Studies.STUDY_JOINED, dbStudy.getLong(dbStudy.getColumnIndex(Aware_Provider.Aware_Studies.STUDY_JOINED)));
                                    complianceEntry.put(Aware_Provider.Aware_Studies.STUDY_EXIT, dbStudy.getLong(dbStudy.getColumnIndex(Aware_Provider.Aware_Studies.STUDY_EXIT)));
                                    complianceEntry.put(Aware_Provider.Aware_Studies.STUDY_TITLE, dbStudy.getString(dbStudy.getColumnIndex(Aware_Provider.Aware_Studies.STUDY_TITLE)));
                                    complianceEntry.put(Aware_Provider.Aware_Studies.STUDY_DESCRIPTION, dbStudy.getString(dbStudy.getColumnIndex(Aware_Provider.Aware_Studies.STUDY_DESCRIPTION)));
                                    complianceEntry.put(Aware_Provider.Aware_Studies.STUDY_COMPLIANCE, "canceled quit");

                                    getContentResolver().insert(Aware_Provider.Aware_Studies.CONTENT_URI, complianceEntry);
                                }
                                if (dbStudy != null && !dbStudy.isClosed()) dbStudy.close();

                                dialogInterface.dismiss();
                            }
                        })
                        .setOnDismissListener(new DialogInterface.OnDismissListener() {
                            @Override
                            public void onDismiss(DialogInterface dialogInterface) {
                                //Sync to server the studies statuses
                                Bundle sync = new Bundle();
                                sync.putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true);
                                sync.putBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, true);
                                ContentResolver.requestSync(Aware.getAWAREAccount(getApplicationContext()), Aware_Provider.getAuthority(getApplicationContext()), sync);
                            }
                        })
                        .show();
            }
        });
    }

    private class PopulateStudy extends AsyncTask<String, Void, JSONObject> {

        ProgressDialog mPopulating;

        private String study_url = "";
        private String study_api_key = "";
        private String study_id = "";
        private String study_config = "";

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
//
//            mPopulating = new ProgressDialog(Aware_Join_Study.this);
//            mPopulating.setMessage("Retrieving study information, please wait.");
//            mPopulating.setCancelable(false);
//            mPopulating.setInverseBackgroundForced(false);
//            mPopulating.show();
        }

        @Override
        protected JSONObject doInBackground(String... params) {
            study_url = params[0];
            study_config = params[1];

            if (study_url.length() == 0) return null;

            try {
                return new JSONObject(study_config);
            } catch (JSONException e) {
                e.printStackTrace();
                return null;
            }
        }

        @Override
        protected void onPostExecute(JSONObject result) {
            super.onPostExecute(result);

            if (result == null) {
//                mPopulating.dismiss();

                android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(Aware_Join_Study.this);
                builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        setResult(Activity.RESULT_CANCELED);
                        finish();
                    }
                });
                builder.setTitle("Study information");
                builder.setMessage("Unable to retrieve this study information: " + study_url + "\nTry again.");
                builder.show();

                //Reset the webservice server status because this one is not valid
                Aware.setSetting(getApplicationContext(), Aware_Preferences.STATUS_WEBSERVICE, false);

                Intent resetClient = new Intent(getApplicationContext(), Aware_Client.class);
                resetClient.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(resetClient);

                finish();

            } else {
                try {
                    Cursor dbStudy = Aware.getStudy(getApplicationContext(), study_url);
                    JSONObject studyInfo = result.getJSONObject("study_info");

                    if (Aware.DEBUG)
                        Log.d(Aware.TAG, DatabaseUtils.dumpCursorToString(dbStudy));

                    if (dbStudy == null || !dbStudy.moveToFirst()) {
                        ContentValues studyData = new ContentValues();
                        studyData.put(Aware_Provider.Aware_Studies.STUDY_DEVICE_ID, Aware.getSetting(getApplicationContext(), Aware_Preferences.DEVICE_ID));
                        studyData.put(Aware_Provider.Aware_Studies.STUDY_TIMESTAMP, System.currentTimeMillis());
                        studyData.put(Aware_Provider.Aware_Studies.STUDY_API, study_api_key);
                        studyData.put(Aware_Provider.Aware_Studies.STUDY_URL, study_url);
                        studyData.put(Aware_Provider.Aware_Studies.STUDY_CONFIG, study_config);
                        studyData.put(Aware_Provider.Aware_Studies.STUDY_KEY, "0"); // studyInfo.getString("id")); STUDY_KEY needs type INT
                        studyData.put(Aware_Provider.Aware_Studies.STUDY_PI, studyInfo.getString("researcher_first") + " " + studyInfo.getString("researcher_last") + "\nContact: " + studyInfo.getString("researcher_contact"));
                        studyData.put(Aware_Provider.Aware_Studies.STUDY_TITLE, studyInfo.getString("study_title"));
                        studyData.put(Aware_Provider.Aware_Studies.STUDY_DESCRIPTION, studyInfo.getString("study_description"));

                        getContentResolver().insert(Aware_Provider.Aware_Studies.CONTENT_URI, studyData);

                        if (Aware.DEBUG) {
                            Log.d(Aware.TAG, "New study data: " + studyData.toString());
                        }
                    } else {
                        //Update the information to the latest
                        ContentValues studyData = new ContentValues();
                        studyData.put(Aware_Provider.Aware_Studies.STUDY_DEVICE_ID, Aware.getSetting(getApplicationContext(), Aware_Preferences.DEVICE_ID));
                        studyData.put(Aware_Provider.Aware_Studies.STUDY_TIMESTAMP, System.currentTimeMillis());
                        studyData.put(Aware_Provider.Aware_Studies.STUDY_JOINED, 0);
                        studyData.put(Aware_Provider.Aware_Studies.STUDY_EXIT, 0);
                        studyData.put(Aware_Provider.Aware_Studies.STUDY_API, study_api_key);
                        studyData.put(Aware_Provider.Aware_Studies.STUDY_URL, study_url);
                        studyData.put(Aware_Provider.Aware_Studies.STUDY_CONFIG, study_config);
                        studyData.put(Aware_Provider.Aware_Studies.STUDY_KEY, "0"); // studyInfo.getString("id")); STUDY_KEY needs type INT
                        studyData.put(Aware_Provider.Aware_Studies.STUDY_PI, studyInfo.getString("researcher_first") + " " + studyInfo.getString("researcher_last") + "\nContact: " + studyInfo.getString("researcher_contact"));
                        studyData.put(Aware_Provider.Aware_Studies.STUDY_TITLE, studyInfo.getString("study_title"));
                        studyData.put(Aware_Provider.Aware_Studies.STUDY_DESCRIPTION, studyInfo.getString("study_description"));

                        getContentResolver().insert(Aware_Provider.Aware_Studies.CONTENT_URI, studyData);

                        if (Aware.DEBUG) {
                            Log.d(Aware.TAG, "Re-scanned study data: " + studyData.toString());
                        }
                    }

                    if (dbStudy != null && !dbStudy.isClosed()) dbStudy.close();

//                    mPopulating.dismiss();
                    Cursor qry = Aware.getStudy(Aware_Join_Study.this, study_url);
                    if (qry != null && qry.moveToFirst()) {
                        initView(qry, study_config);
                    } else {
                        if (Aware.DEBUG) Log.d(Aware.TAG, "Failed to init join study activity.");
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private class QuitStudyAsync extends AsyncTask<Void, Void, Void> {
        ProgressDialog mQuitting;

        @Override
        protected void onPreExecute() {
            super.onPreExecute();

            mQuitting = new ProgressDialog(Aware_Join_Study.this);
            mQuitting.setMessage("Quitting study, please wait.");
            mQuitting.setCancelable(false);
            mQuitting.setInverseBackgroundForced(false);
            mQuitting.show();
            mQuitting.setOnDismissListener(new DialogInterface.OnDismissListener() {
                @Override
                public void onDismiss(DialogInterface dialogInterface) {
                    finish();

                    //Redirect the user to the main UI
                    Intent mainUI = new Intent(getApplicationContext(), Aware_Client.class);
                    mainUI.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(mainUI);
                }
            });
        }

        @Override
        protected Void doInBackground(Void... params) {
            Aware.reset(getApplicationContext());
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            super.onPostExecute(aVoid);
            mQuitting.dismiss();
        }
    }

    /**
     * Join study asynchronously
     */
    private class JoinStudyAsync extends AsyncTask<Void, Void, Void> {
        ProgressDialog mLoading;

        @Override
        protected void onPreExecute() {
            super.onPreExecute();

            mLoading = new ProgressDialog(Aware_Join_Study.this);
            mLoading.setMessage("Joining study, please wait.");
            mLoading.setCancelable(false);
            mLoading.setInverseBackgroundForced(false);
            mLoading.show();
            mLoading.setOnDismissListener(new DialogInterface.OnDismissListener() {
                @Override
                public void onDismiss(DialogInterface dialogInterface) {
                    finish();
                    //Redirect the user to the main UI
                    Intent mainUI = new Intent(getApplicationContext(), Aware_Client.class);
                    mainUI.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(mainUI);
                }
            });
        }

        @Override
        protected Void doInBackground(Void... params) {
            StudyUtils.applySettings(getApplicationContext(), study_url, study_configs, input_password);
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            super.onPostExecute(aVoid);
            mLoading.dismiss();
        }
    }

    private static PluginCompliance pluginCompliance = new PluginCompliance();

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        //no-op, dummy from Aware_Activity super class interface
    }

    public static class PluginCompliance extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equalsIgnoreCase(Aware.ACTION_AWARE_PLUGIN_INSTALLED)) {
                Intent joinStudy = new Intent(context, Aware_Join_Study.class);
                joinStudy.putExtra(EXTRA_STUDY_URL, study_url);
                context.startActivity(joinStudy);
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (pluginCompliance != null) {
            try {
                unregisterReceiver(pluginCompliance);
            } catch (IllegalArgumentException e) {
                //no-op we can get here if we still need to retrieve the study.
            }
        }
    }

    private void populateStudyInfo(JSONArray study_config) {
        JSONArray plugins = new JSONArray();
        JSONArray sensors = new JSONArray();

        for (int i = 0; i < study_config.length(); i++) {
            try {
                JSONObject element = study_config.getJSONObject(i);
                if (element.has("plugins")) {
                    plugins = element.getJSONArray("plugins");
                }
                if (element.has("sensors")) {
                    sensors = element.getJSONArray("sensors");
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        // Show the required sensors
        active_sensors = new ArrayList<>();
        for (int i = 0; i < sensors.length(); i++) {
            try {
                JSONObject sensor_config = sensors.getJSONObject(i);
                String sensor_setting = sensor_config.getString("setting");
                if (sensor_setting.contains("status") && sensor_config.getBoolean("value")) {
                    active_sensors.add(new SensorInfo(sensor_setting,
                            AwareUtil.getSensorType(sensor_setting)));
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        Collections.sort(active_sensors, new Comparator<SensorInfo>() {
            @Override
            public int compare(SensorInfo s1, SensorInfo s2) {
                return s1.sensorName.compareToIgnoreCase(s2.sensorName);
            }
        });

        mSensorsAdapter = new SensorsAdapter(active_sensors);
        sensorsRecyclerView.setAdapter(mSensorsAdapter);

        //Show the plugins' information
        active_plugins = new ArrayList<>();
        for (int i = 0; i < plugins.length(); i++) {
            try {
                JSONObject plugin_config = plugins.getJSONObject(i);
                String package_name = plugin_config.getString("plugin");

                PackageInfo installed = PluginsManager.isInstalled(this, package_name);
                if (installed == null) {
                    active_plugins.add(new PluginInfo(package_name, package_name, false));
                } else {
                    active_plugins.add(new PluginInfo(PluginsManager.getPluginName(getApplicationContext(), package_name), package_name, true));
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        mPluginsAdapter = new PluginsAdapter(active_plugins);
        pluginsRecyclerView.setAdapter(mPluginsAdapter);
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (active_plugins == null) return;

        Cursor qry = Aware.getStudy(this, study_url);
        if (qry != null && qry.moveToFirst()) {

            /*
            --NOTE--
            Denzil: as plugins are bundled with the client, we no longer need this. Also allows people to join study whether or not a plugin exists for Android and iOS
            --------

            if (active_plugins.size() == 0) {
                pluginsInstalled = true;
                llPluginsRequired.setVisibility(View.GONE);
            } else {
                pluginsInstalled = verifyInstalledPlugins();
                llPluginsRequired.setVisibility(View.VISIBLE);
            }
            */

            pluginsInstalled = true;
//            llPluginsRequired.setVisibility(View.GONE);

            if (pluginsInstalled) {
                btnAction.setAlpha(1f);
                btnAction.setEnabled(true);
            } else {
                btnAction.setEnabled(false);
                btnAction.setAlpha(.3f);
            }

//            if (Aware.isStudy(getApplicationContext())) {
//                btnQuit.setVisibility(View.VISIBLE);
//                btnAction.setOnClickListener(new View.OnClickListener() {
//                    @Override
//                    public void onClick(View view) {
//                        finish();
//                    }
//                });
//                btnAction.setText("OK");
//            } else {
//                btnQuit.setVisibility(View.GONE);
//            }
            qry.close();
        }

        if (Aware.getSetting(this, Aware_Preferences.INTERFACE_LOCKED).equals("true")) {
            BottomNavigationView bottomNavigationView = (BottomNavigationView) findViewById(R.id.aware_bottombar);
            bottomNavigationView.setVisibility(View.GONE);
        }
    }

    private boolean verifyInstalledPlugins() {
        boolean result = true;
        for (PluginInfo plugin : active_plugins) {
            PackageInfo installed = PluginsManager.isInstalled(this, plugin.packageName);
            if (installed != null) {
                plugin.installed = true;
            } else {
                plugin.installed = false;
                result = false;
            }
        }
        mPluginsAdapter.notifyDataSetChanged();
        return result;
    }

    public class SensorsAdapter extends RecyclerView.Adapter<SensorsAdapter.ViewHolder> {
        private ArrayList<SensorInfo> mDataset;

        public class ViewHolder extends RecyclerView.ViewHolder {
            public TextView txtSensorName;

            public ViewHolder(View v) {
                super(v);
                txtSensorName = (TextView) v.findViewById(R.id.txt_sensor_name);
            }
        }

        public SensorsAdapter(ArrayList<SensorInfo> myDataset) {
            mDataset = myDataset;
        }

        @Override
        public SensorsAdapter.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.sensors_required_list_item, parent, false);

            ViewHolder vh = new ViewHolder(v);
            return vh;
        }

        @Override
        public void onBindViewHolder(ViewHolder holder, final int position) {
            holder.txtSensorName.setText(mDataset.get(position).sensorName);
        }

        @Override
        public int getItemCount() {
            return mDataset.size();
        }
    }

    public class PluginsAdapter extends RecyclerView.Adapter<PluginsAdapter.ViewHolder> {
        private ArrayList<PluginInfo> mDataset;

        public class ViewHolder extends RecyclerView.ViewHolder {
            public TextView txtPackageName;
            public Button btnInstall;
            public CheckBox cbInstalled;

            public ViewHolder(View v) {
                super(v);
                txtPackageName = (TextView) v.findViewById(R.id.txt_package_name);
                btnInstall = (Button) v.findViewById(R.id.btn_install);
                cbInstalled = (CheckBox) v.findViewById(R.id.cb_installed);
            }
        }

        public PluginsAdapter(ArrayList<PluginInfo> myDataset) {
            mDataset = myDataset;
        }

        @Override
        public PluginsAdapter.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.plugins_installation_list_item, parent, false);

            ViewHolder vh = new ViewHolder(v);
            return vh;
        }

        @Override
        public void onBindViewHolder(ViewHolder holder, final int position) {
            holder.txtPackageName.setText(mDataset.get(position).pluginName);
            holder.btnInstall.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Toast.makeText(Aware_Join_Study.this, "Installing...", Toast.LENGTH_SHORT).show();
                    Aware.downloadPlugin(getApplicationContext(), mDataset.get(position).packageName, study_url, false);
                }
            });
            if (mDataset.get(position).installed) {
                holder.btnInstall.setVisibility(View.INVISIBLE);
                holder.cbInstalled.setVisibility(View.VISIBLE);
            } else {
                holder.btnInstall.setVisibility(View.VISIBLE);
                holder.cbInstalled.setVisibility(View.INVISIBLE);
            }
        }

        @Override
        public int getItemCount() {
            return mDataset.size();
        }
    }

    public class PluginInfo {
        public String pluginName;
        public String packageName;
        public boolean installed;

        public PluginInfo(String pluginName, String packageName, boolean installed) {
            this.pluginName = pluginName;
            this.packageName = packageName;
            this.installed = installed;
        }
    }

    public class SensorInfo {
        public String setting;
        public String sensorName;

        public SensorInfo(String setting, String sensorName) {
            this.setting = setting;
            this.sensorName = sensorName;
        }
    }
}
