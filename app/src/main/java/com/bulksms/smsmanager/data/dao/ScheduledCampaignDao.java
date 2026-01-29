package com.bulksms.smsmanager.data.dao;

import androidx.paging.PagingSource;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Update;
import androidx.room.Delete;
import androidx.room.Query;

import com.bulksms.smsmanager.data.entity.ScheduledCampaignEntity;

import java.util.List;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;

/**
 * Data Access Object for Scheduled Campaign entities
 */
@Dao
public interface ScheduledCampaignDao {
    
    @Insert
    Completable insertScheduledCampaign(ScheduledCampaignEntity scheduledCampaign);
    
    @Insert
    Completable insertScheduledCampaigns(List<ScheduledCampaignEntity> scheduledCampaigns);
    
    @Update
    Completable updateScheduledCampaign(ScheduledCampaignEntity scheduledCampaign);
    
    @Delete
    Completable deleteScheduledCampaign(ScheduledCampaignEntity scheduledCampaign);
    
    @Query("DELETE FROM scheduled_campaigns WHERE id = :id")
    Completable deleteScheduledCampaignById(long id);
    
    @Query("SELECT * FROM scheduled_campaigns WHERE id = :id LIMIT 1")
    Single<ScheduledCampaignEntity> getScheduledCampaignById(long id);
    
    @Query("SELECT * FROM scheduled_campaigns WHERE campaignId = :campaignId LIMIT 1")
    Single<ScheduledCampaignEntity> getScheduledCampaignByCampaignId(long campaignId);
    
    @Query("SELECT * FROM scheduled_campaigns WHERE isActive = 1 ORDER BY nextExecutionTime ASC")
    PagingSource<Integer, ScheduledCampaignEntity> getActiveScheduledCampaignsPaged();
    
    @Query("SELECT * FROM scheduled_campaigns WHERE isActive = 1 AND status = 'SCHEDULED' ORDER BY nextExecutionTime ASC")
    Single<List<ScheduledCampaignEntity>> getPendingScheduledCampaigns();
    
    @Query("SELECT * FROM scheduled_campaigns WHERE isActive = 1 AND status = 'SCHEDULED' AND nextExecutionTime <= :currentTime ORDER BY nextExecutionTime ASC")
    Single<List<ScheduledCampaignEntity>> getReadyToExecuteCampaigns(long currentTime);
    
    @Query("SELECT * FROM scheduled_campaigns WHERE status = :status ORDER BY createdAt DESC")
    PagingSource<Integer, ScheduledCampaignEntity> getScheduledCampaignsByStatusPaged(String status);
    
    @Query("SELECT * FROM scheduled_campaigns WHERE isRecurring = 1 AND isActive = 1 ORDER BY nextExecutionTime ASC")
    PagingSource<Integer, ScheduledCampaignEntity> getRecurringCampaignsPaged();
    
    @Query("SELECT * FROM scheduled_campaigns WHERE campaignId = :campaignId ORDER BY createdAt DESC")
    Single<List<ScheduledCampaignEntity>> getScheduledCampaignsForCampaign(long campaignId);
    
    @Query("SELECT COUNT(*) FROM scheduled_campaigns WHERE isActive = 1")
    Single<Integer> getActiveScheduledCampaignsCount();
    
    @Query("SELECT COUNT(*) FROM scheduled_campaigns WHERE status = 'SCHEDULED' AND isActive = 1")
    Single<Integer> getPendingScheduledCampaignsCount();
    
    @Query("SELECT COUNT(*) FROM scheduled_campaigns WHERE status = 'EXECUTING' AND isActive = 1")
    Single<Integer> getExecutingScheduledCampaignsCount();
    
    @Query("SELECT COUNT(*) FROM scheduled_campaigns WHERE isRecurring = 1 AND isActive = 1")
    Single<Integer> getRecurringScheduledCampaignsCount();
    
    @Query("SELECT COUNT(*) FROM scheduled_campaigns WHERE nextExecutionTime <= :currentTime AND status = 'SCHEDULED' AND isActive = 1")
    Single<Integer> getOverdueScheduledCampaignsCount(long currentTime);
    
    @Query("UPDATE scheduled_campaigns SET status = 'CANCELLED', isActive = 0, updatedAt = :updatedAt WHERE id = :id")
    Completable cancelScheduledCampaign(long id, long updatedAt);
    
    @Query("UPDATE scheduled_campaigns SET status = 'CANCELLED', isActive = 0, updatedAt = :updatedAt WHERE campaignId = :campaignId")
    Completable cancelScheduledCampaignsByCampaignId(long campaignId, long updatedAt);
    
    @Query("UPDATE scheduled_campaigns SET isActive = 0, updatedAt = :updatedAt WHERE id = :id")
    Completable deactivateScheduledCampaign(long id, long updatedAt);
    
    @Query("UPDATE scheduled_campaigns SET isActive = 1, updatedAt = :updatedAt WHERE id = :id")
    Completable activateScheduledCampaign(long id, long updatedAt);
    
    @Query("UPDATE scheduled_campaigns SET nextExecutionTime = :nextTime, updatedAt = :updatedAt WHERE id = :id")
    Completable updateNextExecutionTime(long id, long nextTime, long updatedAt);
    
    @Query("UPDATE scheduled_campaigns SET status = 'EXECUTING', lastExecutionTime = :execTime, currentOccurrences = currentOccurrences + 1, updatedAt = :updatedAt WHERE id = :id")
    Completable markAsExecuting(long id, long execTime, long updatedAt);
    
    @Query("UPDATE scheduled_campaigns SET status = 'COMPLETED', updatedAt = :updatedAt WHERE id = :id")
    Completable markAsCompleted(long id, long updatedAt);
    
    @Query("UPDATE scheduled_campaigns SET status = 'FAILED', updatedAt = :updatedAt WHERE id = :id")
    Completable markAsFailed(long id, long updatedAt);
    
    @Query("DELETE FROM scheduled_campaigns WHERE isActive = 0 AND updatedAt < :before")
    Completable deleteInactiveScheduledCampaignsBefore(long before);
    
    @Query("SELECT * FROM scheduled_campaigns WHERE nextExecutionTime BETWEEN :startTime AND :endTime AND isActive = 1 ORDER BY nextExecutionTime ASC")
    Single<List<ScheduledCampaignEntity>> getScheduledCampaignsInTimeRange(long startTime, long endTime);
    
    @Query("SELECT DATE(nextExecutionTime/1000, 'unixepoch') as date, COUNT(*) as count " +
           "FROM scheduled_campaigns WHERE isActive = 1 " +
           "GROUP BY DATE(nextExecutionTime/1000, 'unixepoch') ORDER BY date ASC")
    Single<List<ScheduledCampaignDailyStats>> getScheduledCampaignsDailyStats();
    
    @Query("SELECT recurrencePattern, COUNT(*) as count FROM scheduled_campaigns WHERE isRecurring = 1 AND isActive = 1 GROUP BY recurrencePattern ORDER BY count DESC")
    Single<List<ScheduledCampaignPatternStats>> getScheduledCampaignsPatternStats();
    
    /**
     * Statistics data classes
     */
    class ScheduledCampaignDailyStats {
        public final String date;
        public final int count;
        
        public ScheduledCampaignDailyStats(String date, int count) {
            this.date = date;
            this.count = count;
        }
    }
    
    class ScheduledCampaignPatternStats {
        public final String pattern;
        public final int count;
        
        public ScheduledCampaignPatternStats(String pattern, int count) {
            this.pattern = pattern;
            this.count = count;
        }
    }
}
