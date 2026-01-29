package com.bulksms.smsmanager.data.dao;

import androidx.paging.PagingSource;
import androidx.room.Dao;
import androidx.room.Query;
import androidx.room.Insert;
import androidx.room.Delete;

import com.bulksms.smsmanager.data.entity.SmsFtsEntity;
import com.bulksms.smsmanager.data.entity.SmsEntity;

import java.util.List;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;

/**
 * Data Access Object for Full-Text Search
 */
@Dao
public interface SmsSearchDao {
    
    @Insert
    Completable insertFtsEntity(SmsFtsEntity ftsEntity);
    
    @Insert
    Completable insertFtsEntities(List<SmsFtsEntity> ftsEntities);
    
    @Delete
    Completable deleteFtsEntity(SmsFtsEntity ftsEntity);
    
    /**
     * Full-text search using MATCH operator
     * This is much more efficient than LIKE for large datasets
     */
    @Query("SELECT sms_entities.* FROM sms_entities JOIN sms_fts ON sms_entities.id = sms_fts.rowid " +
           "WHERE sms_fts MATCH :query " +
           "ORDER BY sms_entities.createdAt DESC")
    PagingSource<Integer, SmsEntity> searchMessages(String query);
    
    /**
     * Search with additional filters
     */
    @Query("SELECT sms_entities.* FROM sms_entities JOIN sms_fts ON sms_entities.id = sms_fts.rowid " +
           "WHERE sms_fts MATCH :query " +
           "AND sms_entities.boxType = :messageType " +
           "ORDER BY sms_entities.createdAt DESC")
    PagingSource<Integer, SmsEntity> searchMessagesByType(String query, int messageType);
    
    /**
     * Search unread messages
     */
    @Query("SELECT sms_entities.* FROM sms_entities JOIN sms_fts ON sms_entities.id = sms_fts.rowid " +
           "WHERE sms_fts MATCH :query " +
           "AND sms_entities.isRead = 0 " +
           "ORDER BY sms_entities.createdAt DESC")
    PagingSource<Integer, SmsEntity> searchUnreadMessages(String query);
    
    /**
     * Search by phone number specifically
     */
    @Query("SELECT sms_entities.* FROM sms_entities JOIN sms_fts ON sms_entities.id = sms_fts.rowid " +
           "WHERE sms_fts MATCH :phoneNumber " +
           "ORDER BY sms_entities.createdAt DESC")
    PagingSource<Integer, SmsEntity> searchByPhoneNumber(String phoneNumber);
    
    /**
     * Advanced search with boolean operators
     */
    @Query("SELECT sms_entities.* FROM sms_entities JOIN sms_fts ON sms_entities.id = sms_fts.rowid " +
           "WHERE sms_fts MATCH :query " +
           "AND sms_entities.createdAt >= :startDate " +
           "AND sms_entities.createdAt <= :endDate " +
           "ORDER BY sms_entities.createdAt DESC")
    PagingSource<Integer, SmsEntity> searchMessagesInDateRange(String query, long startDate, long endDate);
    
    /**
     * Get search suggestions based on partial query
     */
    @Query("SELECT DISTINCT substr(message, 1, 50) as suggestion " +
           "FROM sms_fts " +
           "WHERE sms_fts MATCH :query || '*' " +
           "LIMIT 10")
    Single<List<String>> getSearchSuggestions(String query);
    
    /**
     * Count search results
     */
    @Query("SELECT COUNT(*) FROM sms_entities JOIN sms_fts ON sms_entities.id = sms_fts.rowid " +
           "WHERE sms_fts MATCH :query")
    Single<Integer> getSearchResultsCount(String query);
    
    /**
     * Rebuild FTS index
     * This should be called when SMS data is significantly updated
     */
    @Query("INSERT INTO sms_fts(sms_fts) VALUES('rebuild')")
    Completable rebuildFtsIndex();
    
    /**
     * Optimize FTS index
     */
    @Query("INSERT INTO sms_fts(sms_fts) VALUES('optimize')")
    Completable optimizeFtsIndex();
    
    /**
     * Delete all FTS entries
     */
    @Query("DELETE FROM sms_fts")
    Completable deleteAllFtsEntries();
    
    /**
     * Get FTS statistics
     */
    @Query("SELECT COUNT(*) FROM sms_fts")
    Single<Integer> getFtsEntryCount();
}
