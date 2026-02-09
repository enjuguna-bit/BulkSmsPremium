package com.afriserve.smsmanager.data.repository;

import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.paging.PagingSource;

import com.afriserve.smsmanager.data.dao.SmsDao;
import com.afriserve.smsmanager.data.dao.SmsSearchDao;
import com.afriserve.smsmanager.data.entity.SmsEntity;
import com.afriserve.smsmanager.data.entity.SmsFtsEntity;
import com.afriserve.smsmanager.data.contacts.ContactResolver;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.inject.Inject;
import javax.inject.Singleton;

import dagger.hilt.android.qualifiers.ApplicationContext;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;

import android.content.Context;

/**
 * Repository for advanced SMS search functionality
 * Handles full-text search and search indexing
 */
@Singleton
public class SmsSearchRepository {
    
    private static final String TAG = "SmsSearchRepository";
    
    private final SmsDao smsDao;
    private final SmsSearchDao searchDao;
    private final ContactResolver contactResolver;
    private final Context context;
    private final ExecutorService executor = Executors.newFixedThreadPool(2);
    
    // Search state
    private final MutableLiveData<SearchState> _searchState = new MutableLiveData<>();
    public final LiveData<SearchState> searchState = _searchState;
    
    // Error states (consistent pattern across repositories)
    private final MutableLiveData<String> _errorState = new MutableLiveData<>();
    public final LiveData<String> errorState = _errorState;
    
    // Sync result state
    private final MutableLiveData<SearchSyncResult> _syncResult = new MutableLiveData<>();
    public final LiveData<SearchSyncResult> syncResult = _syncResult;
    
    @Inject
    public SmsSearchRepository(
        SmsDao smsDao,
        SmsSearchDao searchDao,
        ContactResolver contactResolver,
        @ApplicationContext Context context
    ) {
        this.smsDao = smsDao;
        this.searchDao = searchDao;
        this.contactResolver = contactResolver;
        this.context = context.getApplicationContext();
    }
    
    /**
     * Search messages using full-text search
     */
    public PagingSource<Integer, SmsEntity> searchMessages(String query) {
        if (query == null || query.trim().isEmpty()) {
            return smsDao.getAllSmsPaged();
        }
        
        // Clean and prepare query for FTS
        String cleanQuery = prepareSearchQuery(query);
        Log.d(TAG, "Searching for: " + cleanQuery);
        
        return searchDao.searchMessages(cleanQuery);
    }
    
    /**
     * Search messages with type filter
     */
    public PagingSource<Integer, SmsEntity> searchMessagesByType(String query, int messageType) {
        if (query == null || query.trim().isEmpty()) {
            switch (messageType) {
                case 1: // Inbox
                    return smsDao.getInboxMessagesPaged();
                case 2: // Sent
                    return smsDao.getSentMessagesPaged();
                default:
                    return smsDao.getAllSmsPaged();
            }
        }
        
        String cleanQuery = prepareSearchQuery(query);
        return searchDao.searchMessagesByType(cleanQuery, messageType);
    }
    
    /**
     * Search unread messages
     */
    public PagingSource<Integer, SmsEntity> searchUnreadMessages(String query) {
        if (query == null || query.trim().isEmpty()) {
            return smsDao.getUnreadMessagesPaged();
        }
        
        String cleanQuery = prepareSearchQuery(query);
        return searchDao.searchUnreadMessages(cleanQuery);
    }
    
    /**
     * Search by phone number
     */
    public PagingSource<Integer, SmsEntity> searchByPhoneNumber(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.trim().isEmpty()) {
            return smsDao.getAllSmsPaged();
        }
        
        String cleanQuery = prepareSearchQuery(phoneNumber);
        return searchDao.searchByPhoneNumber(cleanQuery);
    }
    
    /**
     * Advanced search with date range
     */
    public PagingSource<Integer, SmsEntity> searchMessagesInDateRange(
        String query, long startDate, long endDate) {
        
        if (query == null || query.trim().isEmpty()) {
            // Return all messages in date range (would need additional DAO method)
            return smsDao.getAllSmsPaged();
        }
        
        String cleanQuery = prepareSearchQuery(query);
        return searchDao.searchMessagesInDateRange(cleanQuery, startDate, endDate);
    }
    
    /**
     * Get search suggestions
     */
    public Single<List<String>> getSearchSuggestions(String query) {
        if (query == null || query.trim().isEmpty()) {
            return Single.just(List.of());
        }
        
        String cleanQuery = prepareSearchQuery(query);
        return searchDao.getSearchSuggestions(cleanQuery)
            .subscribeOn(Schedulers.io());
    }
    
    /**
     * Get search results count
     */
    public Single<Integer> getSearchResultsCount(String query) {
        if (query == null || query.trim().isEmpty()) {
            return Single.just(0);
        }
        
        String cleanQuery = prepareSearchQuery(query);
        return searchDao.getSearchResultsCount(cleanQuery)
            .subscribeOn(Schedulers.io());
    }
    
    /**
     * Build and maintain FTS index
     */
    public Completable buildSearchIndex() {
        return Completable.fromAction(() -> {
            try {
                _searchState.postValue(SearchState.INDEXING);
                
                // Clear existing index
                searchDao.deleteAllFtsEntries().blockingAwait();
                
                // Get all SMS messages
                List<SmsEntity> allMessages = new java.util.ArrayList<>();
                try {
                    allMessages.addAll(smsDao.getRecentSmsByStatus("DELIVERED", 10000).blockingGet());
                    allMessages.addAll(smsDao.getRecentSmsByStatus("SENT", 10000).blockingGet());
                    allMessages.addAll(smsDao.getRecentSmsByStatus("PENDING", 10000).blockingGet());
                } catch (Exception e) {
                    Log.w(TAG, "Could not fetch all messages", e);
                }
                
                // Create FTS entities
                List<SmsFtsEntity> ftsEntities = new java.util.ArrayList<>();
                for (SmsEntity message : allMessages) {
                    SmsFtsEntity ftsEntity = createFtsEntity(message);
                    if (ftsEntity != null) {
                        ftsEntities.add(ftsEntity);
                    }
                }
                
                // Insert in batches to avoid memory issues
                int batchSize = 100;
                for (int i = 0; i < ftsEntities.size(); i += batchSize) {
                    int end = Math.min(i + batchSize, ftsEntities.size());
                    List<SmsFtsEntity> batch = ftsEntities.subList(i, end);
                    searchDao.insertFtsEntities(batch).blockingAwait();
                }
                
                // Rebuild and optimize index
                searchDao.rebuildFtsIndex().blockingAwait();
                searchDao.optimizeFtsIndex().blockingAwait();
                
                _searchState.postValue(SearchState.READY);
                Log.d(TAG, "Search index built with " + ftsEntities.size() + " entries");
                
            } catch (Exception error) {
                Log.e(TAG, "Failed to build search index", error);
                _errorState.postValue("Failed to build search index: " + error.getMessage());
                _searchState.postValue(SearchState.ERROR);
                throw new RuntimeException(error);
            }
        }).subscribeOn(Schedulers.io());
    }
    
    /**
     * Add message to search index
     */
    public Completable indexMessage(SmsEntity message) {
        return Completable.fromAction(() -> {
            try {
                SmsFtsEntity ftsEntity = createFtsEntity(message);
                if (ftsEntity != null) {
                    searchDao.insertFtsEntity(ftsEntity).blockingAwait();
                    Log.d(TAG, "Indexed message: " + message.id);
                }
            } catch (Exception error) {
                Log.e(TAG, "Failed to index message: " + message.id, error);
                _errorState.postValue("Failed to index message: " + error.getMessage());
                throw new RuntimeException(error);
            }
        }).subscribeOn(Schedulers.io());
    }
    
    /**
     * Remove message from search index
     */
    public Completable removeMessageFromIndex(SmsEntity message) {
        return Completable.fromAction(() -> {
            try {
                SmsFtsEntity ftsEntity = createFtsEntity(message);
                if (ftsEntity != null) {
                    searchDao.deleteFtsEntity(ftsEntity).blockingAwait();
                    Log.d(TAG, "Removed from index: " + message.id);
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to remove message from index", e);
                _errorState.postValue("Failed to remove from index: " + e.getMessage());
                throw new RuntimeException(e);
            }
        }).subscribeOn(Schedulers.io());
    }
    
    /**
     * Update message in search index
     */
    public Completable updateMessageIndex(SmsEntity message) {
        return removeMessageFromIndex(message)
            .andThen(indexMessage(message))
            .subscribeOn(Schedulers.io());
    }
    
    /**
     * Get search index statistics
     */
    public Single<SearchStats> getSearchStats() {
        return searchDao.getFtsEntryCount()
            .zipWith(
                Single.fromCallable(() -> {
                    Integer count = smsDao.getTotalCount().getValue();
                    return count != null ? count : 0;
                }),
                (ftsCount, totalSms) -> new SearchStats(ftsCount, totalSms)
            ).subscribeOn(Schedulers.io());
    }
    
    /**
     * Helper methods
     */
    private String prepareSearchQuery(String query) {
        if (query == null) return "";
        
        // Remove special characters that might break FTS
        String cleanQuery = query.replaceAll("[^\\w\\s\\-\\*]", " ");
        
        // Trim and collapse multiple spaces
        cleanQuery = cleanQuery.trim().replaceAll("\\s+", " ");
        
        // Add FTS operators for better search
        if (cleanQuery.contains(" ")) {
            // For multi-word queries, use NEAR operator for proximity search
            cleanQuery = cleanQuery.replace(" ", " NEAR ");
        }
        
        return cleanQuery;
    }
    
    private SmsFtsEntity createFtsEntity(SmsEntity message) {
        try {
            if (message.id <= 0) {
                Log.w(TAG, "Invalid message ID for FTS entity: " + message.id);
                return null;
            }
            
            SmsFtsEntity ftsEntity = new SmsFtsEntity();
            ftsEntity.rowid = message.id; // Use message ID as rowid to prevent duplicates
            ftsEntity.phoneNumber = message.getAddress();
            ftsEntity.message = message.getBody();
            
            // Note: SmsFtsEntity stores rowid, phoneNumber and message
            
            return ftsEntity;
            
        } catch (Exception e) {
            Log.w(TAG, "Failed to create FTS entity for message: " + message.id, e);
            return null;
        }
    }
    
    private String extractSearchKeywords(SmsEntity message) {
        StringBuilder keywords = new StringBuilder();
        
        // Add phone number variations
        String phone = message.getAddress();
        if (phone != null) {
            keywords.append(phone).append(" ");
            
            // Add phone number without special characters
            String cleanPhone = phone.replaceAll("[^0-9+]", "");
            keywords.append(cleanPhone).append(" ");
            
            // Add last 4 digits
            if (cleanPhone.length() >= 4) {
                keywords.append(cleanPhone.substring(cleanPhone.length() - 4)).append(" ");
            }
        }
        
        // Add message content keywords
        String messageBody = message.getBody();
        if (messageBody != null) {
            // Extract common words and patterns
            String[] words = messageBody.toLowerCase().split("\\s+");
            for (String word : words) {
                if (word.length() >= 3) { // Only include words with 3+ characters
                    keywords.append(word).append(" ");
                }
            }
        }
        
        // Add date keywords
        long date = message.getDate();
        keywords.append(" ").append(getDateKeywords(date));
        
        return keywords.toString().trim();
    }
    
    private String getDateKeywords(long timestamp) {
        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.setTimeInMillis(timestamp);
        
        StringBuilder dateKeywords = new StringBuilder();
        
        // Add year, month, day
        dateKeywords.append(cal.get(java.util.Calendar.YEAR)).append(" ");
        dateKeywords.append(cal.get(java.util.Calendar.MONTH) + 1).append(" ");
        dateKeywords.append(cal.get(java.util.Calendar.DAY_OF_MONTH)).append(" ");
        
        // Add day of week
        String[] days = {"sunday", "monday", "tuesday", "wednesday", "thursday", "friday", "saturday"};
        dateKeywords.append(days[cal.get(java.util.Calendar.DAY_OF_WEEK) - 1]).append(" ");
        
        // Add time keywords
        int hour = cal.get(java.util.Calendar.HOUR_OF_DAY);
        if (hour < 12) {
            dateKeywords.append("morning");
        } else if (hour < 17) {
            dateKeywords.append("afternoon");
        } else {
            dateKeywords.append("evening");
        }
        
        return dateKeywords.toString();
    }
    
    /**
     * Search state enum
     */
    public enum SearchState {
        IDLE,
        INDEXING,
        READY,
        ERROR
    }
    
    /**
     * Search statistics data class
     */
    public static class SearchStats {
        public final int indexedMessages;
        public final int totalMessages;
        public final double indexCoverage;
        
        public SearchStats(int indexedMessages, int totalMessages) {
            this.indexedMessages = indexedMessages;
            this.totalMessages = totalMessages;
            this.indexCoverage = totalMessages > 0 ? (double) indexedMessages / totalMessages : 0.0;
        }
    }
    
    /**
     * Search sync result data class
     */
    public static class SearchSyncResult {
        public final int indexedCount;
        public final int errorCount;
        public final String errorMessage;
        
        private SearchSyncResult(int indexedCount, int errorCount, String errorMessage) {
            this.indexedCount = indexedCount;
            this.errorCount = errorCount;
            this.errorMessage = errorMessage;
        }
        
        public static SearchSyncResult Success(int indexedCount) {
            return new SearchSyncResult(indexedCount, 0, null);
        }
        
        public static SearchSyncResult Error(String errorMessage) {
            return new SearchSyncResult(0, 1, errorMessage);
        }
        
        public boolean hasErrors() {
            return errorCount > 0;
        }
    }
}
