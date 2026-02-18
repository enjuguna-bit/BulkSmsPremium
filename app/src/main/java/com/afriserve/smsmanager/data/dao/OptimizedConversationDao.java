package com.afriserve.smsmanager.data.dao;

import androidx.lifecycle.LiveData;
import androidx.paging.PagingSource;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Update;
import androidx.room.Delete;
import androidx.room.Query;
import androidx.room.OnConflictStrategy;

import com.afriserve.smsmanager.data.entity.ConversationEntity;

import java.util.List;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.core.Flowable;

/**
 * Optimized Data Access Object for Conversation entities
 * Includes performance optimizations and proper indexing
 */
@Dao
public interface OptimizedConversationDao {
    
    // Optimized insert operations with batch support
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    Completable insertConversation(ConversationEntity conversation);
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    Completable insertConversations(List<ConversationEntity> conversations);
    
    // Optimized update operations
    @Update
    Completable updateConversation(ConversationEntity conversation);
    
    @Delete
    Completable deleteConversation(ConversationEntity conversation);
    
    // Optimized queries with proper indexing hints
    @Query("SELECT * FROM conversations ORDER BY isPinned DESC, lastMessageTime DESC LIMIT :limit OFFSET :offset")
    PagingSource<Integer, ConversationEntity> getAllConversationsPaged();
    
    @Query("SELECT * FROM conversations WHERE isPinned = 1 ORDER BY lastMessageTime DESC LIMIT :limit OFFSET :offset")
    PagingSource<Integer, ConversationEntity> getPinnedConversationsPaged();
    
    @Query("SELECT * FROM conversations WHERE isArchived = 0 ORDER BY isPinned DESC, lastMessageTime DESC LIMIT :limit OFFSET :offset")
    PagingSource<Integer, ConversationEntity> getActiveConversationsPaged();
    
    @Query("SELECT * FROM conversations WHERE isArchived = 1 ORDER BY isPinned DESC, lastMessageTime DESC LIMIT :limit OFFSET :offset")
    PagingSource<Integer, ConversationEntity> getArchivedConversationsPaged();
    
    @Query("SELECT * FROM conversations WHERE unreadCount > 0 ORDER BY isPinned DESC, lastMessageTime DESC LIMIT :limit OFFSET :offset")
    PagingSource<Integer, ConversationEntity> getUnreadConversationsPaged();

    @Query("SELECT * FROM conversations WHERE isArchived = 0 AND lastMessageType = 'SENT' ORDER BY isPinned DESC, lastMessageTime DESC LIMIT :limit OFFSET :offset")
    PagingSource<Integer, ConversationEntity> getSentConversationsPaged();
    
    // Optimized single entity queries with indexes
    @Query("SELECT * FROM conversations WHERE phoneNumber = :phoneNumber LIMIT 1")
    Single<ConversationEntity> getConversationByPhoneNumber(String phoneNumber);

    @Query("SELECT * FROM conversations WHERE phoneNumber = :phoneNumber")
    LiveData<ConversationEntity> getConversationByPhoneNumberLive(String phoneNumber);

    @Query("SELECT * FROM conversations WHERE threadId = :threadId LIMIT 1")
    Single<ConversationEntity> getConversationByThreadId(long threadId);
    
    @Query("SELECT * FROM conversations WHERE id = :conversationId LIMIT 1")
    Single<ConversationEntity> getConversationById(long conversationId);
    
    // Optimized count queries with indexes
    @Query("SELECT COUNT(*) FROM conversations WHERE unreadCount > 0")
    Single<Integer> getUnreadConversationsCount();

    @Query("SELECT COUNT(*) FROM conversations WHERE unreadCount > 0")
    LiveData<Integer> getUnreadConversationsCountLive();
    
    @Query("SELECT COUNT(*) FROM conversations")
    Single<Integer> getTotalConversationsCount();

    @Query("SELECT COUNT(*) FROM conversations")
    LiveData<Integer> getTotalConversationsCountLive();
    
    @Query("SELECT SUM(messageCount) FROM conversations")
    Single<Integer> getTotalMessagesCount();
    
    @Query("SELECT SUM(unreadCount) FROM conversations")
    Single<Integer> getTotalUnreadMessagesCount();
    
    // Optimized update operations with indexes
    @Query("UPDATE conversations SET unreadCount = unreadCount + 1 WHERE phoneNumber = :phoneNumber")
    Completable incrementUnreadCount(String phoneNumber);
    
    @Query("UPDATE conversations SET unreadCount = 0 WHERE phoneNumber = :phoneNumber")
    Completable markConversationAsRead(String phoneNumber);

    @Query("UPDATE conversations SET unreadCount = 0 WHERE threadId = :threadId")
    Completable markConversationAsReadByThreadId(long threadId);

    @Query("UPDATE conversations SET unreadCount = :count WHERE phoneNumber = :phoneNumber")
    Completable setConversationUnreadCount(String phoneNumber, int count);

    @Query("UPDATE conversations SET unreadCount = :count WHERE threadId = :threadId")
    Completable setConversationUnreadCountByThreadId(long threadId, int count);
    
    @Query("UPDATE conversations SET isPinned = :isPinned WHERE id = :conversationId")
    Completable updatePinStatus(long conversationId, boolean isPinned);
    
    @Query("UPDATE conversations SET isArchived = :isArchived WHERE id = :conversationId")
    Completable updateArchiveStatus(long conversationId, boolean isArchived);
    
    // Optimized search with full-text search support
    @Query("SELECT * FROM conversations WHERE " +
           "contactName LIKE '%' || :query || '%' OR " +
           "phoneNumber LIKE '%' || :query || '%' OR " +
           "lastMessagePreview LIKE '%' || :query || '%' " +
           "ORDER BY lastMessageTime DESC LIMIT :limit OFFSET :offset")
    PagingSource<Integer, ConversationEntity> searchConversations(String query);
    
    // Performance optimization queries
    @Query("SELECT COUNT(*) FROM conversations WHERE isPinned = 1")
    Single<Integer> getPinnedConversationsCount();
    
    @Query("SELECT COUNT(*) FROM conversations WHERE isArchived = 0")
    Single<Integer> getActiveConversationsCount();
    
    @Query("SELECT COUNT(*) FROM conversations WHERE isArchived = 1")
    Single<Integer> getArchivedConversationsCount();
    
    // Bulk operations for performance
    @Query("UPDATE conversations SET isArchived = 1 WHERE id IN (:conversationIds)")
    Completable archiveConversations(List<Long> conversationIds);
    
    @Query("UPDATE conversations SET isPinned = 1 WHERE id IN (:conversationIds)")
    Completable pinConversations(List<Long> conversationIds);
    
    @Query("UPDATE conversations SET isPinned = 0 WHERE id IN (:conversationIds)")
    Completable unpinConversations(List<Long> conversationIds);
    
    // Performance monitoring queries
    @Query("SELECT AVG(messageCount) FROM conversations")
    Single<Double> getAverageMessageCount();
    
    @Query("SELECT MAX(messageCount) FROM conversations")
    Single<Integer> getMaxMessageCount();
    
    @Query("SELECT MIN(messageCount) FROM conversations")
    Single<Integer> getMinMessageCount();
    
    // Cleanup operations
    @Query("DELETE FROM conversations WHERE phoneNumber = :phoneNumber")
    Completable deleteConversationByPhoneNumber(String phoneNumber);
    
    @Query("DELETE FROM conversations WHERE id IN (:conversationIds)")
    Completable deleteConversationsById(List<Long> conversationIds);
    
    @Query("DELETE FROM conversations")
    Completable deleteAllConversations();
    
    // Index maintenance queries
    @Query("REINDEX conversations")
    Completable reindexConversations();
    
    @Query("ANALYZE conversations")
    Completable analyzeConversations();
    
    // Performance statistics
    @Query("SELECT " +
           "COUNT(*) as total, " +
           "SUM(CASE WHEN isPinned = 1 THEN 1 ELSE 0 END) as pinned, " +
           "SUM(CASE WHEN isArchived = 1 THEN 1 ELSE 0 END) as archived, " +
           "SUM(CASE WHEN unreadCount > 0 THEN 1 ELSE 0 END) as unread, " +
           "AVG(messageCount) as avgMessages, " +
           "MAX(lastMessageTime) as latestMessageTime " +
           "FROM conversations")
    Single<ConversationStats> getConversationStatistics();
    
    // Conversation statistics data class
    class ConversationStats {
        public int total;
        public int pinned;
        public int archived;
        public int unread;
        public double avgMessages;
        public long latestMessageTime;
    }
}