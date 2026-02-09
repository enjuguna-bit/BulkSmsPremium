package com.afriserve.smsmanager;

import android.app.Application;
import android.util.Log;

import androidx.hilt.work.HiltWorkerFactory;
import androidx.work.Configuration;
import androidx.work.WorkManager;

import javax.inject.Inject;

import dagger.hilt.android.HiltAndroidApp;

/**
 * Application class for Bulk SMS Manager
 * Required for Hilt dependency injection and WorkManager integration
 */
@HiltAndroidApp
public class BulkSmsApplication extends Application implements Configuration.Provider {
    
    @Inject
    HiltWorkerFactory workerFactory;
    
    @Inject
    BulkSmsService smsService;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d("BulkSmsStartup", "Application onCreate: " + System.currentTimeMillis());
        // WorkManager initializer is removed in the manifest; initialize manually.
        try {
            WorkManager.getInstance(this);
        } catch (IllegalStateException e) {
            WorkManager.initialize(this, getWorkManagerConfiguration());
        }
    }

    @Override
    public Configuration getWorkManagerConfiguration() {
        return new Configuration.Builder()
                .setWorkerFactory(workerFactory)
                .build();
    }
    
    public BulkSmsService getSmsService() {
        return smsService;
    }
} 
