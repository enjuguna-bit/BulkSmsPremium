package com.bulksms.smsmanager.data.scheduler;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.bulksms.smsmanager.data.dao.ScheduledCampaignDao;
import com.bulksms.smsmanager.data.dao.CampaignDao;
import com.bulksms.smsmanager.data.entity.ScheduledCampaignEntity;
import com.bulksms.smsmanager.data.entity.CampaignEntity;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;
import javax.inject.Singleton;

import dagger.hilt.android.qualifiers.ApplicationContext;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * Campaign scheduler for managing scheduled SMS campaigns
 * Handles one-time and recurring campaigns with proper alarm management
 */
@Singleton
public class CampaignScheduler {
    
    private static final String TAG = "CampaignScheduler";
    
    // Scheduler state
    private final MutableLiveData<SchedulerState> _schedulerState = new MutableLiveData<>();
    public final LiveData<SchedulerState> schedulerState = _schedulerState;
    
    // Statistics
    private final MutableLiveData<SchedulerStats> _schedulerStats = new MutableLiveData<>();
    public final LiveData<SchedulerStats> schedulerStats = _schedulerStats;
    
    private final Context context;
    private final ScheduledCampaignDao scheduledCampaignDao;
    private final CampaignDao campaignDao;
    private final AlarmManager alarmManager;
    private final ScheduledExecutorService executorService;
    
    @Inject
    public CampaignScheduler(
        @ApplicationContext Context context,
        ScheduledCampaignDao scheduledCampaignDao,
        CampaignDao campaignDao
    ) {
        this.context = context;
        this.scheduledCampaignDao = scheduledCampaignDao;
        this.campaignDao = campaignDao;
        this.alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        this.executorService = Executors.newScheduledThreadPool(2);
        
        _schedulerState.postValue(SchedulerState.IDLE);
        
        // Start the scheduler
        startScheduler();
        
        Log.d(TAG, "Campaign scheduler initialized");
    }
    
    /**
     * Schedule a campaign for one-time execution
     */
    public Completable scheduleCampaign(long campaignId, long scheduledTime, String timezone) {
        return Completable.fromAction(() -> {
            try {
                // Validate campaign exists
                CampaignEntity campaign = campaignDao.getCampaignById(campaignId).blockingGet();
                if (campaign == null) {
                    throw new RuntimeException("Campaign not found: " + campaignId);
                }
                
                // Create scheduled campaign
                ScheduledCampaignEntity scheduledCampaign = new ScheduledCampaignEntity(campaignId, scheduledTime, timezone);
                
                // Insert into database
                scheduledCampaignDao.insertScheduledCampaign(scheduledCampaign).blockingAwait();
                long scheduledId = System.currentTimeMillis(); // Use timestamp as ID
                
                // Set alarm for execution
                setAlarmForCampaign(scheduledId, scheduledTime);
                
                Log.d(TAG, "Scheduled campaign " + campaignId + " for execution at " + scheduledTime);
                updateSchedulerStats();
                
            } catch (Exception e) {
                Log.e(TAG, "Failed to schedule campaign", e);
                throw new RuntimeException(e);
            }
        }).subscribeOn(Schedulers.io());
    }
    
    /**
     * Schedule a recurring campaign
     */
    public Completable scheduleRecurringCampaign(
        long campaignId,
        long firstExecutionTime,
        String timezone,
        String recurrencePattern,
        int recurrenceInterval,
        Integer maxOccurrences
    ) {
        return Completable.fromAction(() -> {
            try {
                // Validate campaign exists
                CampaignEntity campaign = campaignDao.getCampaignById(campaignId).blockingGet();
                if (campaign == null) {
                    throw new RuntimeException("Campaign not found: " + campaignId);
                }
                
                // Create recurring scheduled campaign
                ScheduledCampaignEntity scheduledCampaign = new ScheduledCampaignEntity(campaignId, firstExecutionTime, timezone);
                scheduledCampaign.isRecurring = true;
                scheduledCampaign.recurrencePattern = recurrencePattern;
                scheduledCampaign.recurrenceInterval = recurrenceInterval;
                scheduledCampaign.maxOccurrences = maxOccurrences;
                scheduledCampaign.nextExecutionTime = firstExecutionTime;
                
                // Insert into database
                scheduledCampaignDao.insertScheduledCampaign(scheduledCampaign).blockingAwait();
                long scheduledId = System.currentTimeMillis(); // Use timestamp as ID
                
                // Set alarm for first execution
                setAlarmForCampaign(scheduledId, firstExecutionTime);
                
                Log.d(TAG, "Scheduled recurring campaign " + campaignId + " with pattern: " + recurrencePattern);
                updateSchedulerStats();
                
            } catch (Exception e) {
                Log.e(TAG, "Failed to schedule recurring campaign", e);
                throw new RuntimeException(e);
            }
        }).subscribeOn(Schedulers.io());
    }
    
    /**
     * Cancel a scheduled campaign
     */
    public Completable cancelScheduledCampaign(long scheduledCampaignId) {
        return Completable.fromAction(() -> {
            try {
                // Cancel alarm
                cancelAlarmForCampaign(scheduledCampaignId);
                
                // Update database
                scheduledCampaignDao.cancelScheduledCampaign(scheduledCampaignId, System.currentTimeMillis()).blockingAwait();
                
                Log.d(TAG, "Cancelled scheduled campaign: " + scheduledCampaignId);
                updateSchedulerStats();
                
            } catch (Exception e) {
                Log.e(TAG, "Failed to cancel scheduled campaign", e);
                throw new RuntimeException(e);
            }
        }).subscribeOn(Schedulers.io());
    }
    
    /**
     * Get all active scheduled campaigns
     */
    public Single<List<ScheduledCampaignEntity>> getActiveScheduledCampaigns() {
        return scheduledCampaignDao.getPendingScheduledCampaigns()
            .subscribeOn(Schedulers.io());
    }
    
    /**
     * Get campaigns ready to execute
     */
    public Single<List<ScheduledCampaignEntity>> getReadyToExecuteCampaigns() {
        return scheduledCampaignDao.getReadyToExecuteCampaigns(System.currentTimeMillis())
            .subscribeOn(Schedulers.io());
    }
    
    /**
     * Execute a scheduled campaign
     */
    public Completable executeScheduledCampaign(long scheduledCampaignId) {
        return Completable.fromAction(() -> {
            try {
                ScheduledCampaignEntity scheduledCampaign = 
                    scheduledCampaignDao.getScheduledCampaignById(scheduledCampaignId).blockingGet();
                
                if (scheduledCampaign == null || !scheduledCampaign.shouldExecute()) {
                    Log.w(TAG, "Campaign not ready for execution: " + scheduledCampaignId);
                    return;
                }
                
                // Mark as executing
                scheduledCampaign.markAsExecuting();
                scheduledCampaignDao.updateScheduledCampaign(scheduledCampaign).blockingAwait();
                
                // Get the actual campaign
                CampaignEntity campaign = campaignDao.getCampaignById(scheduledCampaign.campaignId).blockingGet();
                if (campaign == null) {
                    scheduledCampaign.markAsFailed("Campaign not found");
                    scheduledCampaignDao.updateScheduledCampaign(scheduledCampaign).blockingAwait();
                    return;
                }
                
                // Execute the campaign (this would integrate with BulkSmsService)
                Log.d(TAG, "Executing scheduled campaign: " + campaign.name);
                
                // For now, we'll simulate execution
                Thread.sleep(2000); // Simulate campaign execution
                
                // Mark as completed
                scheduledCampaign.markAsCompleted();
                scheduledCampaignDao.updateScheduledCampaign(scheduledCampaign).blockingAwait();
                
                // Schedule next execution if recurring
                if (scheduledCampaign.isRecurring && scheduledCampaign.isActive) {
                    setAlarmForCampaign(scheduledCampaign.id, scheduledCampaign.nextExecutionTime);
                }
                
                Log.d(TAG, "Completed execution of scheduled campaign: " + campaign.name);
                updateSchedulerStats();
                
            } catch (Exception e) {
                Log.e(TAG, "Failed to execute scheduled campaign", e);
                
                // Mark as failed
                try {
                    ScheduledCampaignEntity scheduledCampaign = 
                        scheduledCampaignDao.getScheduledCampaignById(scheduledCampaignId).blockingGet();
                    if (scheduledCampaign != null) {
                        scheduledCampaign.markAsFailed(e.getMessage());
                        scheduledCampaignDao.updateScheduledCampaign(scheduledCampaign).blockingAwait();
                    }
                } catch (Exception updateError) {
                    Log.e(TAG, "Failed to update campaign status", updateError);
                }
                
                throw new RuntimeException(e);
            }
        }).subscribeOn(Schedulers.io());
    }
    
    /**
     * Set alarm for campaign execution
     */
    private void setAlarmForCampaign(long scheduledCampaignId, long executionTime) {
        try {
            Intent intent = new Intent(context, CampaignAlarmReceiver.class);
            intent.putExtra("scheduledCampaignId", scheduledCampaignId);
            
            PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                (int) scheduledCampaignId,
                intent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
            );
            
            // Use AlarmManager for precise timing
            if (executionTime > System.currentTimeMillis()) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    executionTime,
                    pendingIntent
                );
                
                Log.d(TAG, "Set alarm for campaign " + scheduledCampaignId + " at " + executionTime);
            } else {
                // Execute immediately if time is in the past
                executeScheduledCampaign(scheduledCampaignId)
                    .subscribe(
                        () -> Log.d(TAG, "Executed overdue campaign immediately"),
                        error -> Log.e(TAG, "Failed to execute overdue campaign", error)
                    );
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to set alarm for campaign", e);
        }
    }
    
    /**
     * Cancel alarm for campaign
     */
    private void cancelAlarmForCampaign(long scheduledCampaignId) {
        try {
            Intent intent = new Intent(context, CampaignAlarmReceiver.class);
            intent.putExtra("scheduledCampaignId", scheduledCampaignId);
            
            PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                (int) scheduledCampaignId,
                intent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
            );
            
            alarmManager.cancel(pendingIntent);
            Log.d(TAG, "Cancelled alarm for campaign: " + scheduledCampaignId);
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to cancel alarm for campaign", e);
        }
    }
    
    /**
     * Start the scheduler
     */
    private void startScheduler() {
        _schedulerState.postValue(SchedulerState.ACTIVE);
        
        // Schedule periodic check for overdue campaigns
        executorService.scheduleAtFixedRate(() -> {
            try {
                checkOverdueCampaigns();
            } catch (Exception e) {
                Log.e(TAG, "Error in periodic check", e);
            }
        }, 1, 1, TimeUnit.MINUTES);
        
        Log.d(TAG, "Campaign scheduler started");
    }
    
    /**
     * Check for overdue campaigns and execute them
     */
    private void checkOverdueCampaigns() {
        getReadyToExecuteCampaigns()
            .subscribe(
                campaigns -> {
                    for (ScheduledCampaignEntity campaign : campaigns) {
                        executeScheduledCampaign(campaign.id)
                            .subscribe(
                                () -> Log.d(TAG, "Executed overdue campaign: " + campaign.id),
                                error -> Log.e(TAG, "Failed to execute overdue campaign", error)
                            );
                    }
                },
                error -> Log.e(TAG, "Failed to check overdue campaigns", error)
            );
    }
    
    /**
     * Update scheduler statistics
     */
    private void updateSchedulerStats() {
        Single.zip(
            scheduledCampaignDao.getActiveScheduledCampaignsCount(),
            scheduledCampaignDao.getPendingScheduledCampaignsCount(),
            scheduledCampaignDao.getExecutingScheduledCampaignsCount(),
            scheduledCampaignDao.getRecurringScheduledCampaignsCount(),
            scheduledCampaignDao.getOverdueScheduledCampaignsCount(System.currentTimeMillis()),
            (total, pending, executing, recurring, overdue) -> 
                new SchedulerStats(total, pending, executing, recurring, overdue)
        ).subscribeOn(Schedulers.io())
            .subscribe(
                stats -> _schedulerStats.postValue(stats),
                error -> Log.e(TAG, "Failed to update scheduler stats", error)
            );
    }
    
    /**
     * Stop the scheduler
     */
    public void stopScheduler() {
        try {
            executorService.shutdown();
            _schedulerState.postValue(SchedulerState.STOPPED);
            Log.d(TAG, "Campaign scheduler stopped");
        } catch (Exception e) {
            Log.e(TAG, "Failed to stop scheduler", e);
        }
    }
    
    /**
     * Scheduler state enum
     */
    public enum SchedulerState {
        IDLE,
        ACTIVE,
        STOPPED,
        ERROR
    }
    
    /**
     * Scheduler statistics
     */
    public static class SchedulerStats {
        public final int totalActive;
        public final int pending;
        public final int executing;
        public final int recurring;
        public final int overdue;
        
        public SchedulerStats(int totalActive, int pending, int executing, int recurring, int overdue) {
            this.totalActive = totalActive;
            this.pending = pending;
            this.executing = executing;
            this.recurring = recurring;
            this.overdue = overdue;
        }
        
        @Override
        public String toString() {
            return "SchedulerStats{" +
                    "totalActive=" + totalActive +
                    ", pending=" + pending +
                    ", executing=" + executing +
                    ", recurring=" + recurring +
                    ", overdue=" + overdue +
                    '}';
        }
    }
}
