package com.bulksms.smsmanager.service;

import android.app.Service;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;
import android.os.ResultReceiver;
import android.util.Log;

/**
 * Service for responding to SMS messages
 * Required for default SMS app functionality (RESPOND_VIA_MESSAGE)
 */
public class SmsService extends Service {
    
    private static final String TAG = "SmsService";
    
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            return START_NOT_STICKY;
        }
        
        String action = intent.getAction();
        if ("android.intent.action.RESPOND_VIA_MESSAGE".equals(action)) {
            Bundle extras = intent.getExtras();
            if (extras != null) {
                String message = extras.getString(Intent.EXTRA_TEXT);
                ResultReceiver resultReceiver = extras.getParcelable(Intent.EXTRA_RESULT_RECEIVER);
                
                Log.d(TAG, "Respond via message: " + message);
                
                // Handle quick response functionality here
                // This would typically send a quick SMS response
                
                if (resultReceiver != null) {
                    Bundle resultBundle = new Bundle();
                    resultReceiver.send(0, resultBundle);
                }
            }
        }
        
        return START_NOT_STICKY;
    }
}
