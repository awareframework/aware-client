package com.aware.syncadapters;

import android.content.Intent;
import android.net.Uri;

import com.aware.providers.Notes_Provider;

import android.app.Service;
import android.os.IBinder;

import androidx.annotation.Nullable;

public class Notes_Sync extends Service {
    private AwareSyncAdapter sSyncAdapter = null;
    private static final Object sSyncAdapterLock = new Object();

    @Override
    public void onCreate(){
        super.onCreate();
        synchronized (sSyncAdapterLock) {
            if (sSyncAdapter == null) {
                sSyncAdapter = new AwareSyncAdapter(getApplicationContext(), true, true);


                sSyncAdapter.init(
                        Notes_Provider.DATABASE_TABLES,
                        Notes_Provider.TABLES_FIELDS,
                        new Uri[]{
                                Notes_Provider.Notes_Data.CONTENT_URI
                        });
            }
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return sSyncAdapter.getSyncAdapterBinder();
    }
}
