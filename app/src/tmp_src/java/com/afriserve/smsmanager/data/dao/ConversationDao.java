package com.afriserve.smsmanager.data.dao;

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
 * Data Access Object for Conversation entities
 */
@Dao
public interface ConversationDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    Completable insertConversation(ConversationEntity conversation);
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    Completable insertConversations(List<ConversationEntity> conversations);
    
    @Update
    Completable updateConversation(ConversationEntity conversation);
    
    @Delete
    Completable deleteConversation(ConversationEntity conversation);
    
    @Query("SELECT * FROM conversations ORDER BY lastMessageTime DESC")
    PagingSource<Integer, ConversationEntity> getAllConversationsPaged();
    
    @Query("SELECT * FROM conversations WHERE isPinned = 1 ORDER BY lastMessageTime DESC")
    PagingSource<Integer, ConversationEntity> getPinnedConversationsPaged();
    
    @Query("SELECT * FROM conversations WHERE isArchived = 0 ORDER BY lastMessageTime DESC")
    PagingSource<Integer, ConversationEntity> getActiveConversationsPaged();
    
    @Query("SELECT * FROM conversations WHERE isArchived = 1 ORDER BY lastMessageTime DESC")
    PagingSource<Integer, ConversationEntity> getArchivedConversationsPaged();
    
    @Query("SELECT * FROM conversations WHERE unreadCount > 0 ORDER BY lastMessageTime DESC")
    PagingSource<Integer, ConversationEntity> getUnreadConversationsPaged();
    
    @Query("SELECT * FROM conversations WHERE phoneNumber = :phoneNumber LIMIT 1")
    Single<ConversationEntity> getConversationByPhoneNumber(String phoneNumber);
    
    @Query("SELECT * FROM conversations WHERE id = :conversationId LIMIT 1")
    Single<ConversationEntity> getConversationById(long conversationId);
    
    @Query("SELECT COUNT(*) FROM conversations WHERE unreadCount > 0")
    Single<Integer> getUnreadConversationsCount();
    
    @Query("SELECT COUNT(*) FROM conversations")
    Single<Integer> getTotalConversationsCount();
    
    @Query("SELECT SUM(messageCount) FROM conversations")
    Single<Integer> getTotalMessagesCount();
    
    @Query("SELECT SUM(unreadCount) FROM conversations")
    Single<Integer> getTotalUnreadMessagesCount();
    
    @Query("UPDATE conversations SET unreadCount = unreadCount + 1 WHERE phoneNumber = :phoneNumber")
    Completable incrementUnreadCount(String phoneNumber);
    
    @Query("UPDATE conversations SET unreadCount = 0 WHERE phoneNumber = :phoneNumber")
    Completable markConversationAsRead(String phoneNumber);
    
    @Query("UPDATE conversations SET isPinned = :isPinned WHERE id = :conversationId")
    Completable updatePinStatus(long conversationId, boolean isPinned);
    
    @Query("UPDATE conversations SET isArchived = :isArchived WHERE id = :conversationId")
    Completable updateArchiveStatus(long conversationId, boolean isArchived);
    
    @Query("UPDATE conversations SET " +
           "lastMessageTime = :lastMessageTime, " +
           "lastMessagePreview = :lastMessagePreview, " +
           "lastMessageType = :lastMessageType, " +
           "messageCount = messageCount + 1, " +
           "unreadCount = CASE WHEN :isIncoming = 1 THEN unreadCount + 1 ELSE unreadCount END, " +
           "updatedAt = :updatedAt " +
           "WHERE phoneNumber = :phoneNumber")
    Completable updateConversationWithNewMessage(
        String phoneNumber,
        long lastMessageTime,
        String lastMessagePreview,
        String lastMessageType,
        boolean isIncoming,
        long updatedAt
    );
    
    @Query("DELETE FROM conversations WHERE phoneNumber = :phoneNumber")
    Completable deleteConversationByPhoneNumber(String phoneNumber);
    
    @Query("DELETE FROM conversations")
    Completable deleteAllConversations();
    
    @Query("SELECT * FROM conversations WHERE " +
           "contactName LIKE '%' || :query || '%' OR " +
           "phoneNumber LIKE '%' || :query || '%' OR " +
           "lastMessagePreview LIKE '%' || :query || '%' " +
           "ORDER BY lastMessageTime DESC")
    PagingSource<Integer, ConversationEntity> searchConversations(String query);
}
