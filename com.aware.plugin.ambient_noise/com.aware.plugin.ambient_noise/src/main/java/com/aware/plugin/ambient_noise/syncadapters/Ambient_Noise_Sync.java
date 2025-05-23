package com.aware.plugin.ambient_noise.syncadapters;

import android.app.Service;
import android.content.Intent;
import android.net.Uri;
import android.os.IBinder;
import android.util.Log;

import com.aware.plugin.ambient_noise.Provider;
import com.aware.syncadapters.AwareSyncAdapter;

/**
 * Created by denzilferreira on 17/08/2017.
 */

public class Ambient_Noise_Sync extends Service {
    private static final String TAG = "Ambient_Noise_Sync";
    private AwareSyncAdapter sSyncAdapter = null;
    private static final Object sSyncAdapterLock = new Object();

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "onCreate called");
        synchronized (sSyncAdapterLock) {
            if (sSyncAdapter == null) {
                Log.d(TAG, "Creating new sync adapter");
                sSyncAdapter = new AwareSyncAdapter(getApplicationContext(), true, true);
                try {
                    sSyncAdapter.init(
                            Provider.DATABASE_TABLES,
                            Provider.TABLES_FIELDS,
                            new Uri[]{Provider.AmbientNoise_Data.CONTENT_URI}
                    );
                    Log.d(TAG, "Sync adapter initialized successfully");
                } catch (Exception e) {
                    Log.e(TAG, "Error initializing sync adapter: " + e.getMessage());
                }
            }
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "onBind called");
        return sSyncAdapter.getSyncAdapterBinder();
    }
}
