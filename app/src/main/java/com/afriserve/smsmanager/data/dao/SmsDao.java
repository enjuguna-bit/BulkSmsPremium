package com.afriserve.smsmanager.data.dao;

import androidx.lifecycle.LiveData;
import androidx.paging.PagingSource;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Update;
import androidx.room.Delete;
import androidx.room.Query;
import androidx.room.OnConflictStrategy;

import com.afriserve.smsmanager.data.entity.SmsEntity;

import java.util.List;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;

/**
 * Data Access Object for SMS entities
 */
@Dao
public interface SmsDao {

    @Query("SELECT * FROM sms_entities ORDER BY createdAt DESC")
    PagingSource<Integer, SmsEntity> getAllSmsPaged();

    @Query("SELECT * FROM sms_entities WHERE status = :status ORDER BY createdAt DESC")
    PagingSource<Integer, SmsEntity> getSmsByStatusPaged(String status);

    @Query("SELECT * FROM sms_entities WHERE campaignId = :campaignId ORDER BY createdAt DESC")
    PagingSource<Integer, SmsEntity> getSmsByCampaignPaged(long campaignId);

    @Query("SELECT * FROM sms_entities WHERE phoneNumber LIKE '%' || :phone || '%' ORDER BY createdAt DESC")
    PagingSource<Integer, SmsEntity> getSmsByPhonePaged(String phone);

    @Query("SELECT * FROM sms_entities WHERE message LIKE '%' || :query || '%' ORDER BY createdAt DESC")
    PagingSource<Integer, SmsEntity> searchSmsPaged(String query);

    @Query("SELECT * FROM sms_entities WHERE status = :status AND nextRetryAt <= :currentTime ORDER BY nextRetryAt ASC")
    List<SmsEntity> getPendingRetries(String status, long currentTime);

    @Query("SELECT * FROM sms_entities WHERE id = :id")
    Single<SmsEntity> getSmsById(long id);

    /**
     * Get SMS by Telephony provider deviceSmsId
     * Returns Single - will throw if not found (caller should catch)
     */
    @Query("SELECT * FROM sms_entities WHERE deviceSmsId = :deviceSmsId LIMIT 1")
    Single<SmsEntity> getSmsByDeviceSmsId(long deviceSmsId);

    /**
     * Find likely duplicate rows created before deviceSmsId was known.
     * Used by sync flow to merge unsynced receiver inserts with provider-backed rows.
     */
    @Query("SELECT * FROM sms_entities " +
           "WHERE deviceSmsId IS NULL " +
           "AND phoneNumber = :phoneNumber " +
           "AND message = :message " +
           "AND boxType = :boxType " +
           "AND ABS(createdAt - :timestamp) <= :windowMs " +
           "ORDER BY ABS(createdAt - :timestamp) ASC " +
           "LIMIT 1")
    Single<SmsEntity> findUnsyncedDuplicate(String phoneNumber, String message, int boxType, long timestamp, long windowMs);

    @Query("SELECT COUNT(*) FROM sms_entities WHERE campaignId = :campaignId")
    Single<Integer> getCountByCampaign(long campaignId);

    @Query("SELECT COUNT(*) FROM sms_entities WHERE createdAt >= :startTime AND createdAt <= :endTime")
    Single<Integer> getCountByTimeRange(long startTime, long endTime);

    @Query("SELECT COUNT(*) FROM sms_entities WHERE status = 'DELIVERED'")
    LiveData<Integer> getDeliveredCount();

    @Query("SELECT COUNT(*) FROM sms_entities WHERE status = 'DELIVERED'")
    Single<Integer> getDeliveredCountSingle();

    @Query("SELECT COUNT(*) FROM sms_entities WHERE status = 'FAILED'")
    LiveData<Integer> getFailedCount();

    @Query("SELECT COUNT(*) FROM sms_entities WHERE status = 'FAILED'")
    Single<Integer> getFailedCountSingle();

    @Query("SELECT COUNT(*) FROM sms_entities WHERE status = 'PENDING'")
    LiveData<Integer> getPendingCount();

    @Query("SELECT COUNT(*) FROM sms_entities WHERE status = 'PENDING'")
    Single<Integer> getPendingCountSingle();

    @Query("SELECT COUNT(*) FROM sms_entities WHERE createdAt >= :startTime AND createdAt < :endTime AND status IN ('PENDING', 'SENT', 'DELIVERED', 'FAILED')")
    Single<Integer> getOutgoingCountInRange(long startTime, long endTime);

    @Query("SELECT COUNT(*) FROM sms_entities WHERE createdAt >= :startTime AND createdAt < :endTime AND status = 'DELIVERED'")
    Single<Integer> getDeliveredCountInRange(long startTime, long endTime);

    @Query("SELECT COUNT(*) FROM sms_entities WHERE createdAt >= :startTime AND createdAt < :endTime AND status = 'FAILED'")
    Single<Integer> getFailedCountInRange(long startTime, long endTime);

    @Query("SELECT COUNT(*) FROM sms_entities WHERE createdAt >= :startTime AND createdAt < :endTime AND status = 'PENDING'")
    Single<Integer> getPendingCountInRange(long startTime, long endTime);

    @Query("SELECT * FROM sms_entities WHERE createdAt >= :startTime AND createdAt < :endTime ORDER BY createdAt DESC LIMIT :limit")
    Single<List<SmsEntity>> getRecentSmsInRange(long startTime, long endTime, int limit);

    @Query("SELECT * FROM sms_entities WHERE status = :status ORDER BY createdAt DESC LIMIT :limit")
    Single<List<SmsEntity>> getRecentSmsByStatus(String status, int limit);

    @Query("SELECT * FROM sms_entities ORDER BY createdAt DESC LIMIT :limit")
    Single<List<SmsEntity>> getAllRecentSms(int limit);

    @Query("SELECT * FROM sms_entities ORDER BY createdAt DESC")
    Single<List<SmsEntity>> getAllSms();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    Single<Long> insertSms(SmsEntity sms);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    Completable insertSmsList(List<SmsEntity> smsList);

    @Update
    Completable updateSms(SmsEntity sms);

    @Update
    Completable updateSmsList(List<SmsEntity> smsList);

    @Delete
    Completable deleteSms(SmsEntity sms);

    @Delete
    Completable deleteSmsList(List<SmsEntity> smsList);

    @Query("DELETE FROM sms_entities WHERE id = :id")
    Completable deleteSmsById(long id);

    @Query("DELETE FROM sms_entities WHERE campaignId = :campaignId")
    Completable deleteSmsByCampaign(long campaignId);

    @Query("DELETE FROM sms_entities WHERE status = :status")
    Completable deleteSmsByStatus(String status);

    @Query("DELETE FROM sms_entities WHERE createdAt < :timestamp")
    Completable deleteOldSms(long timestamp);

    @Query("UPDATE sms_entities SET status = :newStatus WHERE id = :id")
    Completable updateSmsStatus(long id, String newStatus);

    @Query("UPDATE sms_entities SET status = :newStatus, sentAt = :sentAt WHERE id = :id")
    Completable markAsSent(long id, String newStatus, long sentAt);

    @Query("UPDATE sms_entities SET status = :newStatus, deliveredAt = :deliveredAt WHERE id = :id")
    Completable markAsDelivered(long id, String newStatus, long deliveredAt);

    @Query("UPDATE sms_entities SET status = :newStatus, errorCode = :errorCode, errorMessage = :errorMessage, retryCount = retryCount + 1, nextRetryAt = :nextRetryAt WHERE id = :id")
    Completable markAsFailed(long id, String newStatus, String errorCode, String errorMessage, long nextRetryAt);

    @Query("SELECT * FROM sms_entities WHERE phoneNumber = :phoneNumber ORDER BY createdAt ASC")
    Single<List<SmsEntity>> getMessagesByPhoneNumber(String phoneNumber);

    @Query("SELECT * FROM sms_entities WHERE threadId = :threadId ORDER BY createdAt ASC")
    Single<List<SmsEntity>> getMessagesByThreadId(long threadId);

    @Query("SELECT * FROM sms_entities WHERE phoneNumber LIKE '%' || :phoneNumber || '%' ORDER BY createdAt ASC")
    Single<List<SmsEntity>> getMessagesByPhoneNumberLike(String phoneNumber);

    @Query("SELECT * FROM sms_entities WHERE phoneNumber = :phoneNumber ORDER BY createdAt DESC LIMIT 1")
    Single<SmsEntity> getLastSmsForPhone(String phoneNumber);

    @Query("SELECT COUNT(*) FROM sms_entities WHERE phoneNumber = :phoneNumber AND status = 'DELIVERED'")
    Single<Integer> getDeliveredCountForPhone(String phoneNumber);

    @Query("SELECT COUNT(*) FROM sms_entities WHERE status = 'PENDING'")
    Flowable<Integer> getPendingSmsCount();

    @Query("SELECT COUNT(*) FROM sms_entities WHERE status = 'FAILED' AND retryCount < 3")
    Flowable<Integer> getRetryableFailedSmsCount();

    // LiveData queries for ViewModels
    @Query("SELECT COUNT(*) FROM sms_entities")
    LiveData<Integer> getTotalCount();

    @Query("SELECT COUNT(*) FROM sms_entities")
    Single<Integer> getTotalCountSingle();

    @Query("SELECT COUNT(*) FROM sms_entities WHERE (isRead = 0 OR (isRead IS NULL AND (status = 'PENDING' OR status = 'SENT')))")
    LiveData<Integer> getUnreadCount();

    /**
     * Get unread messages - using isRead field or status fallback
     */
    @Query("SELECT * FROM sms_entities WHERE (isRead = 0 OR (isRead IS NULL AND (status = 'PENDING' OR status = 'SENT'))) ORDER BY createdAt DESC")
    PagingSource<Integer, SmsEntity> getUnreadMessagesPaged();

    /**
     * Get sent messages - using boxType field or status fallback
     */
    @Query("SELECT * FROM sms_entities WHERE (boxType = 2 OR (boxType IS NULL AND (status = 'SENT' OR status = 'DELIVERED'))) ORDER BY createdAt DESC")
    PagingSource<Integer, SmsEntity> getSentMessagesPaged();

    /**
     * Get inbox messages - using boxType field
     */
    @Query("SELECT * FROM sms_entities WHERE boxType = 1 ORDER BY createdAt DESC")
    PagingSource<Integer, SmsEntity> getInboxMessagesPaged();

    @Query("SELECT * FROM sms_entities WHERE (phoneNumber LIKE '%' || :query || '%' OR message LIKE '%' || :query || '%') ORDER BY createdAt DESC")
    PagingSource<Integer, SmsEntity> searchMessagesPaged(String query);

    /**
     * Get the timestamp of the most recent message for incremental sync
     */
    @Query("SELECT MAX(createdAt) FROM sms_entities")
    Single<Long> getLatestMessageTimestamp();

    /**
     * Get sent messages that don't have deviceSmsId (need sync to ContentProvider)
     */
    @Query("SELECT * FROM sms_entities WHERE deviceSmsId IS NULL AND (boxType = 2 OR status IN ('SENT', 'DELIVERED', 'FAILED')) ORDER BY createdAt ASC")
    io.reactivex.rxjava3.core.Single<List<SmsEntity>> getSentMessagesWithoutDeviceId();

    /**
     * Get inbox messages that don't have deviceSmsId (need sync to ContentProvider)
     */
    @Query("SELECT * FROM sms_entities WHERE deviceSmsId IS NULL AND (boxType = 1 OR status = 'RECEIVED') ORDER BY createdAt ASC")
    io.reactivex.rxjava3.core.Single<List<SmsEntity>> getInboxMessagesWithoutDeviceId();
}
