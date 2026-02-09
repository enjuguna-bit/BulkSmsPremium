package com.afriserve.smsmanager.data.dao;

import androidx.paging.PagingSource;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Update;
import androidx.room.Delete;
import androidx.room.Query;
import androidx.room.OnConflictStrategy;

import com.afriserve.smsmanager.data.entity.CampaignEntity;

import java.util.List;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;

/**
 * Data Access Object for Campaign entities
 */
@Dao
public interface CampaignDao {
    
    @Query("SELECT * FROM campaign_entities ORDER BY createdAt DESC")
    PagingSource<Integer, CampaignEntity> getAllCampaignsPaged();
    
    @Query("SELECT * FROM campaign_entities WHERE status = :status ORDER BY createdAt DESC")
    PagingSource<Integer, CampaignEntity> getCampaignsByStatusPaged(String status);
    
    @Query("SELECT * FROM campaign_entities WHERE name LIKE '%' || :query || '%' OR description LIKE '%' || :query || '%' ORDER BY createdAt DESC")
    PagingSource<Integer, CampaignEntity> searchCampaignsPaged(String query);
    
    @Query("SELECT * FROM campaign_entities WHERE id = :id")
    Single<CampaignEntity> getCampaignById(long id);
    
    @Query("SELECT * FROM campaign_entities WHERE status = :status ORDER BY createdAt DESC LIMIT :limit")
    Single<List<CampaignEntity>> getRecentCampaignsByStatus(String status, int limit);
    
    @Query("SELECT * FROM campaign_entities WHERE scheduledAt IS NOT NULL AND scheduledAt <= :currentTime AND status = 'DRAFT' ORDER BY scheduledAt ASC")
    Single<List<CampaignEntity>> getScheduledCampaigns(long currentTime);
    
    @Query("SELECT * FROM campaign_entities WHERE status = 'ACTIVE' ORDER BY startedAt ASC")
    Single<List<CampaignEntity>> getActiveCampaigns();
    
    @Query("SELECT COUNT(*) FROM campaign_entities")
    Single<Integer> getTotalCampaignsCount();
    
    @Query("SELECT COUNT(*) FROM campaign_entities WHERE status = :status")
    Single<Integer> getCampaignsCountByStatus(String status);
    
    @Query("SELECT COUNT(*) FROM campaign_entities WHERE createdAt >= :startTime AND createdAt <= :endTime")
    Single<Integer> getCampaignsCountByTimeRange(long startTime, long endTime);
    
    @Query("SELECT SUM(recipientCount) FROM campaign_entities WHERE status = 'COMPLETED'")
    Single<Integer> getTotalRecipientsFromCompletedCampaigns();
    
    @Query("SELECT SUM(sentCount) FROM campaign_entities")
    Single<Integer> getTotalSentMessages();
    
    @Query("SELECT SUM(deliveredCount) FROM campaign_entities")
    Single<Integer> getTotalDeliveredMessages();
    
    @Query("SELECT SUM(failedCount) FROM campaign_entities")
    Single<Integer> getTotalFailedMessages();
    
    @Query("SELECT AVG(CAST(deliveredCount AS FLOAT) / recipientCount) * 100 FROM campaign_entities WHERE status = 'COMPLETED' AND recipientCount > 0")
    Single<Double> getAverageDeliveryRate();
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    Single<Long> insertCampaign(CampaignEntity campaign);
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    Completable insertCampaignList(List<CampaignEntity> campaigns);
    
    @Update
    Completable updateCampaign(CampaignEntity campaign);
    
    @Update
    Completable updateCampaignList(List<CampaignEntity> campaigns);
    
    @Delete
    Completable deleteCampaign(CampaignEntity campaign);
    
    @Delete
    Completable deleteCampaignList(List<CampaignEntity> campaigns);
    
    @Query("DELETE FROM campaign_entities WHERE id = :id")
    Completable deleteCampaignById(long id);
    
    @Query("DELETE FROM campaign_entities WHERE status = :status")
    Completable deleteCampaignsByStatus(String status);
    
    @Query("DELETE FROM campaign_entities WHERE createdAt < :timestamp")
    Completable deleteOldCampaigns(long timestamp);
    
    @Query("UPDATE campaign_entities SET status = :newStatus WHERE id = :id")
    Completable updateCampaignStatus(long id, String newStatus);
    
    @Query("UPDATE campaign_entities SET status = 'ACTIVE', startedAt = :startedAt WHERE id = :id")
    Completable startCampaign(long id, long startedAt);
    
    @Query("UPDATE campaign_entities SET status = 'PAUSED' WHERE id = :id")
    Completable pauseCampaign(long id);
    
    @Query("UPDATE campaign_entities SET status = 'ACTIVE' WHERE id = :id")
    Completable resumeCampaign(long id);
    
    @Query("UPDATE campaign_entities SET status = 'COMPLETED', completedAt = :completedAt WHERE id = :id")
    Completable completeCampaign(long id, long completedAt);
    
    @Query("UPDATE campaign_entities SET deliveredCount = deliveredCount + 1 WHERE id = :id")
    Completable incrementDeliveredCount(long id);
    
    @Query("UPDATE campaign_entities SET sentCount = sentCount + 1 WHERE id = :id")
    Completable incrementSentCount(long id);
    
    @Query("UPDATE campaign_entities SET failedCount = failedCount + 1 WHERE id = :id")
    Completable incrementFailedCount(long id);
    
    @Query("UPDATE campaign_entities SET sentCount = :sentCount, deliveredCount = :deliveredCount, failedCount = :failedCount WHERE id = :id")
    Completable updateCampaignCounts(long id, int sentCount, int deliveredCount, int failedCount);
    
    @Query("SELECT * FROM campaign_entities WHERE templateId = :templateId ORDER BY createdAt DESC")
    Single<List<CampaignEntity>> getCampaignsByTemplate(long templateId);
    
    @Query("SELECT * FROM campaign_entities WHERE scheduledAt BETWEEN :startTime AND :endTime ORDER BY scheduledAt ASC")
    Single<List<CampaignEntity>> getCampaignsScheduledBetween(long startTime, long endTime);
    
    @Query("SELECT * FROM campaign_entities WHERE status IN ('ACTIVE', 'PAUSED') ORDER BY createdAt DESC")
    Single<List<CampaignEntity>> getOngoingCampaigns();
    
    @Query("SELECT COUNT(*) FROM campaign_entities WHERE status = 'ACTIVE'")
    Flowable<Integer> getActiveCampaignsCount();
    
    @Query("SELECT COUNT(*) FROM campaign_entities WHERE status = 'DRAFT'")
    Flowable<Integer> getDraftCampaignsCount();
    
    @Query("SELECT COUNT(*) FROM campaign_entities WHERE scheduledAt IS NOT NULL AND scheduledAt > :currentTime AND status = 'DRAFT'")
    Single<Integer> getScheduledCampaignsCount(long currentTime);
    
    @Query("SELECT * FROM campaign_entities ORDER BY createdAt DESC LIMIT :limit")
    Single<List<CampaignEntity>> getRecentCampaigns(int limit);
    
    @Query("SELECT * FROM campaign_entities WHERE status = 'COMPLETED' ORDER BY completedAt DESC LIMIT :limit")
    Single<List<CampaignEntity>> getRecentlyCompletedCampaigns(int limit);
    
    @Query("SELECT * FROM campaign_entities WHERE recipientCount > :minRecipients ORDER BY recipientCount DESC")
    Single<List<CampaignEntity>> getLargestCampaigns(int minRecipients);
    
    @Query("SELECT * FROM campaign_entities WHERE sentCount > 0 AND (deliveredCount * 100.0 / sentCount) >= :minRate AND status = 'COMPLETED' ORDER BY (deliveredCount * 100.0 / sentCount) DESC")
    Single<List<CampaignEntity>> getBestPerformingCampaigns(double minRate);
}
