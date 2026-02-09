package com.afriserve.smsmanager.data.repository;

/**
 * Sync result sealed class for proper error handling
 */
public sealed class SyncResult {
    public static final class Success extends SyncResult {
        public final int syncedCount;
        public final int skippedCount;
        
        public Success(int syncedCount, int skippedCount) {
            this.syncedCount = syncedCount;
            this.skippedCount = skippedCount;
        }
    }
    
    public static final class Error extends SyncResult {
        public final String message;
        public final Throwable cause;
        
        public Error(String message, Throwable cause) {
            this.message = message;
            this.cause = cause;
        }
        
        public Error(String message) {
            this.message = message;
            this.cause = null;
        }
    }
}
