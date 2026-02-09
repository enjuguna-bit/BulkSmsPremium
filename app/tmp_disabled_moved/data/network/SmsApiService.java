package com.bulksms.smsmanager.data.network;

import retrofit2.Call;
import retrofit2.http.*;
import java.util.List;
import java.util.Map;

/**
 * SMS API service with pagination, ETag support, and network optimizations
 * Designed for offline-first architecture with efficient data synchronization
 */
public interface SmsApiService {
    
    /**
     * Get paginated messages with optional filtering
     * @param page Page number (0-based)
     * @param pageSize Number of items per page
     * @param lastModifiedSince Return only messages modified since this timestamp
     * @return Paginated response with messages
     */
    @GET("messages")
    Call<PaginatedResponse<SmsNetworkEntity>> getMessagesPaginated(
        @Query("page") int page,
        @Query("pageSize") int pageSize,
        @Query("lastModifiedSince") String lastModifiedSince
    );
    
    /**
     * Get paginated messages with advanced filtering
     */
    @GET("messages")
    Call<PaginatedResponse<SmsNetworkEntity>> getMessagesPaginatedAdvanced(
        @QueryMap Map<String, String> filters
    );
    
    /**
     * Get single message by ID with ETag support
     */
    @GET("messages/{id}")
    Call<SmsNetworkEntity> getMessage(
        @Path("id") String id,
        @Header("If-None-Match") String eTag
    );
    
    /**
     * Upload new message with GZIP compression
     */
    @POST("messages")
    Call<SmsNetworkEntity> uploadMessage(
        @Body SmsNetworkEntity message,
        @Header("Content-Encoding") String contentEncoding
    );
    
    /**
     * Update existing message
     */
    @PUT("messages/{id}")
    Call<SmsNetworkEntity> updateMessage(
        @Path("id") String id,
        @Body SmsNetworkEntity message
    );
    
    /**
     * Delete message
     */
    @DELETE("messages/{id}")
    Call<Void> deleteMessage(@Path("id") String id);
    
    /**
     * Sync messages with ETag and If-Modified-Since support
     */
    @GET("messages/sync")
    Call<PaginatedResponse<SmsNetworkEntity>> syncMessages(
        @Header("If-None-Match") String eTag,
        @Header("If-Modified-Since") String ifModifiedSince,
        @Query("limit") int limit
    );
    
    /**
     * Batch sync operations for efficiency
     */
    @POST("messages/sync/batch")
    Call<BatchSyncResponse> batchSync(
        @Body BatchSyncRequest request
    );
    
    /**
     * Get conversations with pagination
     */
    @GET("conversations")
    Call<PaginatedResponse<ConversationNetworkEntity>> getConversationsPaginated(
        @Query("page") int page,
        @Query("pageSize") int pageSize,
        @Query("lastModifiedSince") String lastModifiedSince
    );
    
    /**
     * Get single conversation with messages
     */
    @GET("conversations/{id}")
    Call<ConversationNetworkEntity> getConversation(
        @Path("id") String id,
        @Query("includeMessages") boolean includeMessages,
        @Query("messageLimit") int messageLimit
    );
    
    /**
     * Mark conversation as read
     */
    @PUT("conversations/{id}/read")
    Call<Void> markConversationAsRead(@Path("id") String id);
    
    /**
     * Search messages with pagination
     */
    @GET("messages/search")
    Call<PaginatedResponse<SmsNetworkEntity>> searchMessages(
        @Query("q") String query,
        @Query("page") int page,
        @Query("pageSize") int pageSize,
        @Query("filters") String filters
    );
    
    /**
     * Get sync status for multiple entities
     */
    @POST("sync/status")
    Call<SyncStatusResponse> getSyncStatus(
        @Body List<String> entityIds
    );
    
    /**
     * Resolve sync conflicts
     */
    @POST("sync/resolve")
    Call<ConflictResolutionResponse> resolveConflicts(
        @Body List<ConflictResolutionRequest> conflicts
    );
    
    /**
     * Paginated response wrapper
     */
    class PaginatedResponse<T> {
        public List<T> data;
        public Pagination pagination;
        public String eTag;
        public long lastModified;
        
        public static class Pagination {
            public int page;
            public int pageSize;
            public int totalItems;
            public int totalPages;
            public boolean hasNext;
            public boolean hasPrevious;
        }
    }
    
    /**
     * SMS network entity for API communication
     */
    class SmsNetworkEntity {
        public String id;
        public String phoneNumber;
        public String message;
        public long createdAt;
        public long updatedAt;
        public String status;
        public String boxType;
        public boolean isRead;
        public String deviceSmsId;
        public String conversationId;
        public long syncVersion;
        public Map<String, Object> metadata;
    }
    
    /**
     * Conversation network entity for API communication
     */
    class ConversationNetworkEntity {
        public String id;
        public String phoneNumber;
        public String contactName;
        public String lastMessage;
        public long lastMessageTime;
        public int messageCount;
        public int unreadCount;
        public boolean isPinned;
        public boolean isArchived;
        public long createdAt;
        public long updatedAt;
        public long syncVersion;
        public List<SmsNetworkEntity> messages;
        public Map<String, Object> metadata;
    }
    
    /**
     * Batch sync request
     */
    class BatchSyncRequest {
        public List<String> entityIds;
        public String entityType;
        public long lastSyncTime;
        public String eTag;
        public int limit;
    }
    
    /**
     * Batch sync response
     */
    class BatchSyncResponse {
        public List<SmsNetworkEntity> updatedMessages;
        public List<ConversationNetworkEntity> updatedConversations;
        public List<String> deletedIds;
        public String newETag;
        public long newLastModified;
        public boolean hasMore;
    }
    
    /**
     * Sync status response
     */
    class SyncStatusResponse {
        public Map<String, EntitySyncStatus> statuses;
        public long serverTime;
    }
    
    /**
     * Individual entity sync status
     */
    class EntitySyncStatus {
        public String entityId;
        public String entityType;
        public long lastModified;
        public String eTag;
        public long syncVersion;
        public boolean needsUpdate;
    }
    
    /**
     * Conflict resolution request
     */
    class ConflictResolutionRequest {
        public String entityId;
        public String entityType;
        public String resolution; // "local", "remote", "merge"
        public SmsNetworkEntity localData;
        public SmsNetworkEntity remoteData;
    }
    
    /**
     * Conflict resolution response
     */
    class ConflictResolutionResponse {
        public List<String> resolvedIds;
        public List<String> failedIds;
        public Map<String, String> errors;
    }
}
