package com.bulksms.smsmanager;

import android.app.Application;
import android.util.Log;
import dagger.hilt.android.HiltAndroidApp;

/**
 * Application class for Bulk SMS Manager
 * Required for Hilt dependency injection
 */
@HiltAndroidApp
public class BulkSmsApplication extends Application {
    
    @Override
    public void onCreate() {
        super.onCreate();
        Log.d("BulkSmsStartup", "Application onCreate: " + System.currentTimeMillis());
    }
}
