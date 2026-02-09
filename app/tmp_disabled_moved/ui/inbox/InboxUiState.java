package com.bulksms.smsmanager.ui.inbox;

public class InboxUiState {
    public static final InboxUiState LOADING = new InboxUiState(Type.LOADING, null, null);
    public static final InboxUiState SYNCING = new InboxUiState(Type.SYNCING, null, null);
    
    public final Type type;
    public final String message;
    public final MessageStatistics stats;
    
    private InboxUiState(Type type, String message, MessageStatistics stats) {
        this.type = type;
        this.message = message;
        this.stats = stats;
    }
    
    public static InboxUiState success(String message) {
        return new InboxUiState(Type.SUCCESS, message, null);
    }
    
    public static InboxUiState error(String message) {
        return new InboxUiState(Type.ERROR, message, null);
    }
    
    public static InboxUiState statsLoaded(MessageStatistics stats) {
        return new InboxUiState(Type.STATS_LOADED, null, stats);
    }
    
    public enum Type {
        LOADING,
        SYNCING,
        SUCCESS,
        ERROR,
        STATS_LOADED
    }
}
