package com.afriserve.smsmanager.data.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Update;
import androidx.room.Delete;
import androidx.room.Query;
import androidx.room.OnConflictStrategy;

import com.afriserve.smsmanager.data.entity.SmsQueueEntity;
import com.afriserve.smsmanager.data.queue.SmsQueueManager.QueueStatistics;

import java.util.List;

// RxJava3 types will be available at runtime from androidx.room:room-rxjava3
// import io.reactivex.rxjava3.Completable;
// import io.reactivex.rxjava3.Single;

/**
 * Data Access Object for SMS Queue entities
 */
@Dao
public interface SmsQueueDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insertQueueItem(SmsQueueEntity queueItem);
    
    @Update
    void updateQueueItem(SmsQueueEntity queueItem);
    
    @Delete
    void deleteQueueItem(SmsQueueEntity queueItem);
    
    @Query("DELETE FROM sms_queue WHERE id = :id")
    void deleteQueueItemById(long id);
    
    @Query("SELECT * FROM sms_queue WHERE status = 'PENDING' AND nextRetryAt <= :currentTime ORDER BY nextRetryAt ASC")
    List<SmsQueueEntity> getPendingItems(long currentTime);
    
    @Query("SELECT * FROM sms_queue WHERE status = 'PENDING' ORDER BY nextRetryAt ASC")
    List<SmsQueueEntity> getAllPendingItems();
    
    @Query("SELECT * FROM sms_queue WHERE status = 'PROCESSING' ORDER BY createdAt ASC")
    List<SmsQueueEntity> getProcessingItems();
    
    @Query("SELECT * FROM sms_queue WHERE status = 'FAILED' ORDER BY lastFailureAt DESC")
    List<SmsQueueEntity> getFailedItems();
    
    @Query("SELECT * FROM sms_queue WHERE status = 'EXHAUSTED' ORDER BY lastFailureAt DESC")
    List<SmsQueueEntity> getExhaustedItems();
    
    @Query("SELECT * FROM sms_queue WHERE phoneNumber = :phoneNumber ORDER BY createdAt DESC")
    List<SmsQueueEntity> getItemsByPhoneNumber(String phoneNumber);
    
    @Query("SELECT * FROM sms_queue WHERE originalSmsId = :originalSmsId")
    List<SmsQueueEntity> getItemsByOriginalSmsId(Long originalSmsId);
    
    @Query("SELECT COUNT(*) FROM sms_queue WHERE status = 'PENDING'")
    int getPendingCount();
    
    @Query("SELECT COUNT(*) FROM sms_queue WHERE status = 'PROCESSING'")
    int getProcessingCount();
    
    @Query("SELECT COUNT(*) FROM sms_queue WHERE status = 'FAILED'")
    int getFailedCount();
    
    @Query("SELECT COUNT(*) FROM sms_queue WHERE status = 'EXHAUSTED'")
    int getExhaustedCount();
    
    @Query("SELECT COUNT(*) FROM sms_queue WHERE retryCount >= :maxRetries AND status != 'EXHAUSTED'")
    int getOverdueRetryCount(int maxRetries);
    
    @Query("DELETE FROM sms_queue WHERE status = 'EXHAUSTED'")
    int deleteExhaustedItems();
    
    @Query("DELETE FROM sms_queue WHERE createdAt < :beforeTime")
    int deleteOldItems(long beforeTime);
    
    @Query("DELETE FROM sms_queue")
    void deleteAllQueueItems();
    
    @Query("UPDATE sms_queue SET status = 'PENDING' WHERE status = 'PROCESSING' AND createdAt < :staleTime")
    int resetStaleProcessingItems(long staleTime);
    
    @Query("SELECT * FROM sms_queue ORDER BY createdAt DESC LIMIT :limit")
    List<SmsQueueEntity> getRecentItems(int limit);
    
    /**
     * Get comprehensive queue statistics
     */
    @Query("SELECT " +
           "(SELECT COUNT(*) FROM sms_queue WHERE status = 'PENDING') as pendingCount, " +
           "(SELECT COUNT(*) FROM sms_queue WHERE status = 'PROCESSING') as processingCount, " +
           "(SELECT COUNT(*) FROM sms_queue WHERE status = 'FAILED') as failedCount, " +
           "(SELECT COUNT(*) FROM sms_queue WHERE status = 'EXHAUSTED') as exhaustedCount, " +
           "(SELECT COUNT(*) FROM sms_queue) as totalCount")
    QueueStatistics getQueueStatistics();
}
