package com.bulksms.smsmanager.data.scheduler;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;

/**
 * Broadcast receiver for handling campaign execution alarms
 */
@AndroidEntryPoint
public class CampaignAlarmReceiver extends BroadcastReceiver {
    
    private static final String TAG = "CampaignAlarmReceiver";
    
    @Inject
    CampaignScheduler campaignScheduler;
    
    @Override
    public void onReceive(Context context, Intent intent) {
        try {
            long scheduledCampaignId = intent.getLongExtra("scheduledCampaignId", -1);
            
            if (scheduledCampaignId == -1) {
                Log.e(TAG, "Invalid scheduled campaign ID received");
                return;
            }
            
            Log.d(TAG, "Received alarm for scheduled campaign: " + scheduledCampaignId);
            
            // Execute the scheduled campaign
            campaignScheduler.executeScheduledCampaign(scheduledCampaignId)
                .subscribe(
                    () -> Log.d(TAG, "Successfully executed campaign from alarm: " + scheduledCampaignId),
                    error -> Log.e(TAG, "Failed to execute campaign from alarm: " + scheduledCampaignId, error)
                );
            
        } catch (Exception e) {
            Log.e(TAG, "Error in campaign alarm receiver", e);
        }
    }
}
