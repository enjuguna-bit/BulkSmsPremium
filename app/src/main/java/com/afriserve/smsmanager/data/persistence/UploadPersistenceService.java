package com.afriserve.smsmanager.data.persistence;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import com.afriserve.smsmanager.models.Recipient;
import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.UUID;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Session persistence service for Excel upload data.
 * Manages upload sessions across app restarts with timeout handling.
 *
 * Changes v2:
 *  - Added {@link SessionStats} inner class: a lightweight snapshot of key
 *    session metrics that can be queried without deserialising the full
 *    recipient list, making it safe for main-thread list/history UIs.
 *  - Added {@link #getSessionStats(String)} and {@link #getSessionStatsAsync}.
 */
@Singleton
public class UploadPersistenceService {
    private static final String TAG = "UploadPersistence";
    private static final String PREFS_NAME = "bulk_sms_upload_persistence";
    private static final String KEY_CURRENT_UPLOAD = "current_upload";
    private static final String KEY_UPLOAD_HISTORY = "upload_history";
    private static final String KEY_PREFERENCES = "upload_preferences";
    private static final String KEY_SESSION_PREFIX = "upload_session_";
    private static final String KEY_ACTIVE_SESSION_ID = "active_session_id";
    private static final long DEFAULT_SESSION_TIMEOUT_MS = 24 * 60 * 60 * 1000; // 24 hours

    private final SharedPreferences preferences;
    private final Gson gson;
    private final ExecutorService executor;
    private UploadPreferences uploadPreferences;

    @Inject
    public UploadPersistenceService(@dagger.hilt.android.qualifiers.ApplicationContext Context context) {
        this.preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.gson = new Gson();
        this.executor = Executors.newSingleThreadExecutor();
        this.uploadPreferences = loadPreferences();
    }

    // ── Inner data classes ────────────────────────────────────────────────────

    /**
     * Data class for upload session information.
     */
    public static class UploadSession {
        public String fileId;
        public String fileName;
        public long uploadTimestamp;
        public long lastAccessed;
        public List<Recipient> recipients;
        public Map<String, String> columnMapping;
        public List<String> headers;
        public int totalRecords;
        public int validRecords;
        public int invalidRecords;
        public boolean isActive;
        public String processingStatus;
        public String template;
        public int simSlot = 0;
        public int sendSpeed = 300;
        public String campaignName;
        public String campaignType;
        public String arrearsFilter;
        public String source;
        public long campaignId;
        public int lastProcessedIndex;
        public int sentCount;
        public int failedCount;
        public int skippedCount;
        public boolean isPaused;
        public boolean isStopped;
        public Long scheduledAt;

        public UploadSession() {
            this.uploadTimestamp = System.currentTimeMillis();
            this.lastAccessed = System.currentTimeMillis();
            this.recipients = new ArrayList<>();
            this.columnMapping = new HashMap<>();
            this.headers = new ArrayList<>();
            this.isActive = true;
            this.processingStatus = "ready";
        }
    }

    /**
     * Data class for upload preferences.
     */
    public static class UploadPreferences {
        public boolean autoSave = true;
        public long sessionTimeoutHours = 24;
        public boolean keepHistory = true;
        public int maxHistoryEntries = 10;

        public UploadPreferences() {}
    }

    /**
     * Data class for upload history entry.
     */
    public static class UploadHistoryEntry {
        public String fileId;
        public String fileName;
        public long uploadTimestamp;
        public int totalRecords;
        public int validRecords;
        public boolean completed;
        public Long completedAt;
        public int sentCount;
        public int failedCount;
        public boolean archived;

        public UploadHistoryEntry() {}
    }

    /**
     * Lightweight snapshot of key metrics for a single upload session.
     * Returned by {@link #getSessionStats(String)} for display in history/list UIs
     * without needing to deserialize the full recipient list.
     */
    public static final class SessionStats {
        public final String  fileId;
        public final String  fileName;
        public final int     totalRecords;
        public final int     validRecords;
        public final int     sentCount;
        public final int     failedCount;
        public final int     skippedCount;
        public final int     lastProcessedIndex;
        public final boolean isPaused;
        public final boolean isStopped;
        public final boolean isActive;
        public final long    uploadTimestamp;
        public final long    lastAccessed;
        /** Percentage of valid records successfully sent (0–100). */
        public final int deliveryPct;
        /** Percentage of the send run completed so far (0–100). */
        public final int progressPct;

        private SessionStats(UploadSession s) {
            this.fileId             = s.fileId;
            this.fileName           = s.fileName;
            this.totalRecords       = s.totalRecords;
            this.validRecords       = s.validRecords;
            this.sentCount          = s.sentCount;
            this.failedCount        = s.failedCount;
            this.skippedCount       = s.skippedCount;
            this.lastProcessedIndex = s.lastProcessedIndex;
            this.isPaused           = s.isPaused;
            this.isStopped          = s.isStopped;
            this.isActive           = s.isActive;
            this.uploadTimestamp    = s.uploadTimestamp;
            this.lastAccessed       = s.lastAccessed;
            int processed = s.sentCount + s.failedCount + s.skippedCount;
            this.deliveryPct = (s.validRecords > 0)
                    ? Math.min(100, s.sentCount * 100 / s.validRecords) : 0;
            this.progressPct = (s.validRecords > 0)
                    ? Math.min(100, processed * 100 / s.validRecords) : 0;
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private String sessionKey(String sessionId) {
        return KEY_SESSION_PREFIX + sessionId;
    }

    private void ensureSessionId(UploadSession session) {
        if (session.fileId == null || session.fileId.trim().isEmpty()) {
            session.fileId = UUID.randomUUID().toString();
        }
    }

    private boolean isSessionExpired(UploadSession session) {
        if (session == null) return true;
        long now = System.currentTimeMillis();
        if (session.scheduledAt != null && session.scheduledAt > now) return false;
        long maxAge = uploadPreferences.sessionTimeoutHours * 60 * 60 * 1000;
        return (now - session.uploadTimestamp) > maxAge;
    }

    private void saveSessionInternal(UploadSession session, String key, boolean updateHistory) {
        ensureSessionId(session);
        session.lastAccessed = System.currentTimeMillis();
        session.isActive = true;
        String json = gson.toJson(session);
        preferences.edit().putString(key, json).apply();
        if (updateHistory && uploadPreferences.keepHistory) {
            addToHistory(session);
        }
    }

    private UploadSession loadSessionInternal(String key, boolean touch) {
        String json = preferences.getString(key, null);
        if (json == null) return null;
        UploadSession session = gson.fromJson(json, UploadSession.class);
        if (session == null) return null;
        normalizeSession(session);
        if (isSessionExpired(session)) {
            preferences.edit().remove(key).apply();
            if (KEY_CURRENT_UPLOAD.equals(key)) {
                preferences.edit().remove(KEY_ACTIVE_SESSION_ID).apply();
            }
            return null;
        }
        if (touch) {
            session.lastAccessed = System.currentTimeMillis();
            saveSessionInternal(session, key, false);
        }
        return session;
    }

    // ── Public session API ────────────────────────────────────────────────────

    public void saveSession(UploadSession session) {
        executor.execute(() -> {
            try {
                saveSessionInternal(session, sessionKey(session.fileId), false);
            } catch (Exception e) {
                Log.e(TAG, "Failed to save upload session", e);
            }
        });
    }

    public void saveSessionSync(UploadSession session) {
        try {
            saveSessionInternal(session, sessionKey(session.fileId), false);
        } catch (Exception e) {
            Log.e(TAG, "Failed to save upload session", e);
        }
    }

    public UploadSession loadSessionSync(String sessionId) {
        try {
            return loadSessionInternal(sessionKey(sessionId), false);
        } catch (Exception e) {
            Log.e(TAG, "Failed to load upload session", e);
            return null;
        }
    }

    public UploadSession loadCurrentUploadSync() {
        try {
            UploadSession session = loadSessionInternal(KEY_CURRENT_UPLOAD, true);
            if (session != null) setActiveSessionId(session.fileId);
            return session;
        } catch (Exception e) {
            Log.e(TAG, "Failed to load current upload session", e);
            return null;
        }
    }

    public void setActiveSessionId(String sessionId) {
        preferences.edit().putString(KEY_ACTIVE_SESSION_ID, sessionId).apply();
    }

    public String getActiveSessionId() {
        return preferences.getString(KEY_ACTIVE_SESSION_ID, null);
    }

    public boolean isActiveSession(String sessionId) {
        String activeId = getActiveSessionId();
        return activeId != null && activeId.equals(sessionId);
    }

    public void clearSession(String sessionId) {
        executor.execute(() -> {
            try {
                preferences.edit().remove(sessionKey(sessionId)).apply();
            } catch (Exception e) {
                Log.e(TAG, "Failed to clear session", e);
            }
        });
    }

    /** Save current upload session (async). */
    public void saveCurrentUpload(UploadSession session) {
        executor.execute(() -> {
            try {
                saveSessionInternal(session, KEY_CURRENT_UPLOAD, true);
                saveSessionInternal(session, sessionKey(session.fileId), false);
                setActiveSessionId(session.fileId);
                Log.d(TAG, "Saved upload session: " + session.fileName +
                        " (" + session.validRecords + " contacts)");
            } catch (Exception e) {
                Log.e(TAG, "Failed to save upload session", e);
            }
        });
    }

    public void saveCurrentUploadSync(UploadSession session) {
        try {
            saveSessionInternal(session, KEY_CURRENT_UPLOAD, false);
            saveSessionInternal(session, sessionKey(session.fileId), false);
            setActiveSessionId(session.fileId);
        } catch (Exception e) {
            Log.e(TAG, "Failed to save upload session", e);
        }
    }

    /** Load current upload session asynchronously. Returns null if no session or expired. */
    public void loadCurrentUpload(Callback<UploadSession> callback) {
        executor.execute(() -> {
            try {
                UploadSession session = loadSessionInternal(KEY_CURRENT_UPLOAD, true);
                if (session == null) { callback.onResult(null); return; }
                setActiveSessionId(session.fileId);
                Log.d(TAG, "Loaded session: " + session.fileName);
                callback.onResult(session);
            } catch (Exception e) {
                Log.e(TAG, "Failed to load upload session", e);
                callback.onResult(null);
            }
        });
    }

    /** Clear current upload session. */
    public void clearCurrentUpload() {
        executor.execute(() -> {
            try {
                String activeId = getActiveSessionId();
                SharedPreferences.Editor editor = preferences.edit()
                        .remove(KEY_CURRENT_UPLOAD)
                        .remove(KEY_ACTIVE_SESSION_ID);
                if (activeId != null) editor.remove(sessionKey(activeId));
                editor.apply();
                Log.d(TAG, "Cleared current upload session");
            } catch (Exception e) {
                Log.e(TAG, "Failed to clear upload session", e);
            }
        });
    }

    // ── Session statistics ────────────────────────────────────────────────────

    /**
     * Returns a lightweight {@link SessionStats} snapshot for the given session,
     * or {@code null} if the session is not found or has expired.
     *
     * <p>Unlike {@link #loadSessionSync(String)}, this call does <em>not</em>
     * re-deserialize the full recipient list, so it is safe to call from the
     * main thread for history/list display.</p>
     */
    public SessionStats getSessionStats(String sessionId) {
        try {
            UploadSession session = loadSessionInternal(sessionKey(sessionId), false);
            return session != null ? new SessionStats(session) : null;
        } catch (Exception e) {
            Log.e(TAG, "Failed to get session stats for " + sessionId, e);
            return null;
        }
    }

    /**
     * Async variant of {@link #getSessionStats(String)}.
     */
    public void getSessionStatsAsync(String sessionId, Callback<SessionStats> callback) {
        executor.execute(() -> {
            try {
                callback.onResult(getSessionStats(sessionId));
            } catch (Exception e) {
                Log.e(TAG, "Failed to get session stats async", e);
                callback.onResult(null);
            }
        });
    }

    // ── History API ───────────────────────────────────────────────────────────

    private void addToHistory(UploadSession session) {
        try {
            List<UploadHistoryEntry> history = getUploadHistory();
            UploadHistoryEntry entry = new UploadHistoryEntry();
            entry.fileId = session.fileId;
            entry.fileName = session.fileName;
            entry.uploadTimestamp = session.uploadTimestamp;
            entry.totalRecords = session.totalRecords;
            entry.validRecords = session.validRecords;
            entry.completed = false;
            entry.archived = false;
            history.removeIf(e -> e != null && session.fileId != null && session.fileId.equals(e.fileId));
            history.add(0, entry);
            while (history.size() > uploadPreferences.maxHistoryEntries) {
                history.remove(history.size() - 1);
            }
            preferences.edit().putString(KEY_UPLOAD_HISTORY, gson.toJson(history)).apply();
        } catch (Exception e) {
            Log.e(TAG, "Failed to add to history", e);
        }
    }

    public List<UploadHistoryEntry> getUploadHistory() {
        try {
            String json = preferences.getString(KEY_UPLOAD_HISTORY, null);
            if (json == null) return new ArrayList<>();
            UploadHistoryEntry[] entries = gson.fromJson(json, UploadHistoryEntry[].class);
            List<UploadHistoryEntry> history = new ArrayList<>();
            if (entries != null) {
                for (UploadHistoryEntry entry : entries) {
                    if (entry != null) history.add(entry);
                }
            }
            return history;
        } catch (Exception e) {
            Log.e(TAG, "Failed to get upload history", e);
            return new ArrayList<>();
        }
    }

    public void markUploadCompleted(String fileId, int sentCount, int failedCount) {
        executor.execute(() -> {
            try {
                List<UploadHistoryEntry> history = getUploadHistory();
                boolean updated = false;
                for (UploadHistoryEntry entry : history) {
                    if (entry.fileId.equals(fileId)) {
                        entry.completed = true;
                        entry.completedAt = System.currentTimeMillis();
                        entry.sentCount = sentCount;
                        entry.failedCount = failedCount;
                        updated = true;
                        break;
                    }
                }
                if (updated) {
                    preferences.edit().putString(KEY_UPLOAD_HISTORY, gson.toJson(history)).apply();
                    Log.d(TAG, "Marked upload " + fileId + " as completed (" +
                            sentCount + " sent, " + failedCount + " failed)");
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to mark upload completed", e);
            }
        });
    }

    public void clearHistory() {
        executor.execute(() -> {
            try {
                preferences.edit().remove(KEY_UPLOAD_HISTORY).apply();
                Log.d(TAG, "Cleared upload history");
            } catch (Exception e) {
                Log.e(TAG, "Failed to clear history", e);
            }
        });
    }

    // ── Preferences API ───────────────────────────────────────────────────────

    public UploadPreferences getPreferences() { return uploadPreferences; }

    public void updatePreferences(UploadPreferences preferences) {
        executor.execute(() -> {
            try {
                this.uploadPreferences = preferences;
                this.preferences.edit()
                        .putString(KEY_PREFERENCES, gson.toJson(preferences))
                        .apply();
                Log.d(TAG, "Updated upload preferences");
            } catch (Exception e) {
                Log.e(TAG, "Failed to update preferences", e);
            }
        });
    }

    public void hasActiveSession(Callback<Boolean> callback) {
        loadCurrentUpload(session -> callback.onResult(session != null && session.isActive));
    }

    // ── Cleanup ───────────────────────────────────────────────────────────────

    public void cleanupExpiredSessions() {
        executor.execute(() -> {
            try {
                // The loadCurrentUpload call will automatically clear expired sessions.
                loadCurrentUpload(session -> {});

                // Also purge history entries older than 30 days.
                List<UploadHistoryEntry> history = getUploadHistory();
                long thirtyDaysAgo = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000);
                history.removeIf(entry -> entry.uploadTimestamp < thirtyDaysAgo);
                preferences.edit().putString(KEY_UPLOAD_HISTORY, gson.toJson(history)).apply();
                Log.d(TAG, "Cleaned up expired sessions");
            } catch (Exception e) {
                Log.e(TAG, "Failed to cleanup expired sessions", e);
            }
        });
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    private UploadPreferences loadPreferences() {
        try {
            String json = preferences.getString(KEY_PREFERENCES, null);
            if (json != null) return gson.fromJson(json, UploadPreferences.class);
        } catch (Exception e) {
            Log.e(TAG, "Failed to load preferences", e);
        }
        return new UploadPreferences();
    }

    private void normalizeSession(UploadSession session) {
        if (session.recipients == null) {
            session.recipients = new ArrayList<>();
            return;
        }
        List<Recipient> normalized = new ArrayList<>();
        for (Object item : session.recipients) {
            if (item == null) continue;
            if (item instanceof Recipient) {
                normalized.add((Recipient) item);
                continue;
            }
            try {
                Recipient recipient = gson.fromJson(gson.toJson(item), Recipient.class);
                if (recipient != null && recipient.getPhone() != null && !recipient.getPhone().trim().isEmpty()) {
                    normalized.add(recipient);
                }
            } catch (Exception e) {
                Log.w(TAG, "Skipping invalid recipient during session normalization", e);
            }
        }
        session.recipients = normalized;
        if (session.totalRecords < normalized.size()) session.totalRecords = normalized.size();
        if (session.validRecords < normalized.size()) session.validRecords = normalized.size();
    }

    // ── Callback interface ────────────────────────────────────────────────────

    /** Callback interface for async operations. */
    public interface Callback<T> {
        void onResult(T result);
    }
}
