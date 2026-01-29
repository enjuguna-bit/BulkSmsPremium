package com.bulksms.smsmanager.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * BroadcastReceiver for MMS messages (WAP_PUSH)
 * Required for default SMS app functionality
 */
public class MmsReceiver extends BroadcastReceiver {
    
    private static final String TAG = "MmsReceiver";
    
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) {
            return;
        }
        
        Log.d(TAG, "MMS received: " + intent.getAction());
        
        // Handle MMS messages here
        // This would typically parse MMS content and store in database
        // For now, just log that MMS was received
    }
}
