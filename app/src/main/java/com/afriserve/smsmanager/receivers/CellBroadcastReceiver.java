package com.afriserve.smsmanager.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.telephony.SmsMessage;
import android.util.Log;

public class CellBroadcastReceiver extends BroadcastReceiver {
    private static final String TAG = "CellBroadcastReceiver";
    
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent != null && intent.getAction() != null) {
            if (intent.getAction().equals("android.provider.Telephony.CELL_BROADCAST_RECEIVED")) {
                handleCellBroadcast(context, intent);
            }
        }
    }
    
    private void handleCellBroadcast(Context context, Intent intent) {
        Bundle bundle = intent.getExtras();
        if (bundle != null) {
            Object[] pdus = (Object[]) bundle.get("pdus");
            String format = bundle.getString("format");
            
            if (pdus != null && pdus.length > 0) {
                for (Object pdu : pdus) {
                    try {
                        SmsMessage smsMessage = SmsMessage.createFromPdu((byte[]) pdu, format);
                        if (smsMessage != null) {
                            String message = smsMessage.getMessageBody();
                            String sender = smsMessage.getOriginatingAddress();
                            
                            Log.d(TAG, "Cell broadcast received from: " + sender + ", message: " + message);
                            
                            // Handle emergency alerts and cell broadcast messages here
                            // You can store them in database or show notifications as needed
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing cell broadcast message", e);
                    }
                }
            }
        }
    }
}
