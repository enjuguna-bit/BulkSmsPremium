package com.afriserve.smsmanager.domain;

import android.util.Log;

import com.afriserve.smsmanager.data.parser.ExcelParser;
import com.afriserve.smsmanager.data.persistence.UploadPersistenceService;
import com.afriserve.smsmanager.data.persistence.UploadPersistenceService.UploadSession;
import com.afriserve.smsmanager.models.Recipient;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Manages the lifecycle of upload sessions: creation, persistence, resume, and discard.
 * Extracted from BulkSmsViewModel to adhere to the Single Responsibility Principle.
 */
@Singleton
public class CampaignSessionManager {
    private static final String TAG = "CampaignSessionManager";

    private final UploadPersistenceService uploadPersistence;

    private UploadSession currentSession;
    private UploadSession pendingSession;

    @Inject
    public CampaignSessionManager(UploadPersistenceService uploadPersistence) {
        this.uploadPersistence = uploadPersistence;
    }

    public UploadSession getCurrentSession() {
        return currentSession;
    }

    public void setCurrentSession(UploadSession session) {
        this.currentSession = session;
    }

    public UploadSession getPendingSession() {
        return pendingSession;
    }

    public void setPendingSession(UploadSession session) {
        this.pendingSession = session;
    }

    /**
     * Check if there is a resumable session from a previous run.
     */
    public void checkForResumeSession(ResumeCallback callback) {
        uploadPersistence.loadCurrentUpload(session -> {
            if (session != null && session.isActive) {
                pendingSession = session;
                callback.onResumeAvailable(true);
            }
        });
    }

    /**
     * Initialize a new session from a CSV/Excel import result.
     */
    public UploadSession initSessionFromImport(String fileName, ExcelParser.ParseResult res,
                                                String currentTemplate, int sendSpeed, int simSlot) {
        UploadSession session = new UploadSession();
        session.fileId = UUID.randomUUID().toString();
        session.fileName = fileName;
        session.recipients = res.recipients;
        session.totalRecords = res.recipients != null ? res.recipients.size() : 0;
        session.validRecords = session.totalRecords;
        session.invalidRecords = 0;
        session.processingStatus = "ready";
        session.template = currentTemplate;
        session.sendSpeed = sendSpeed;
        session.simSlot = simSlot;
        session.campaignName = "Bulk Campaign";
        session.campaignType = "BULK_MARKETING";

        session.columnMapping = new HashMap<>();
        if (res.mapping != null) {
            if (res.mapping.name != null) session.columnMapping.put("name", res.mapping.name);
            if (res.mapping.phone != null) session.columnMapping.put("phone", res.mapping.phone);
            if (res.mapping.amount != null) session.columnMapping.put("amount", res.mapping.amount);
        }

        currentSession = session;
        pendingSession = null;
        uploadPersistence.saveCurrentUploadSync(session);
        return session;
    }

    /**
     * Prepare a session for sending (either new or existing).
     */
    public UploadSession prepareSessionForSend(List<Recipient> currentData, String currentTemplate,
                                                int simSlot, int sendSpeed, boolean resetProgress) {
        UploadSession session = currentSession;
        if (session == null) {
            session = new UploadSession();
            session.fileId = UUID.randomUUID().toString();
            session.fileName = "Bulk Campaign";
        }

        session.recipients = new ArrayList<>(currentData);
        session.totalRecords = currentData.size();
        session.validRecords = currentData.size();
        session.invalidRecords = 0;
        session.template = currentTemplate;
        session.simSlot = simSlot;
        session.sendSpeed = sendSpeed;
        session.campaignName = session.campaignName != null ? session.campaignName : "Bulk Campaign";
        session.campaignType = session.campaignType != null ? session.campaignType : "BULK_MARKETING";
        session.isActive = true;
        session.isPaused = false;
        session.isStopped = false;
        session.processingStatus = "sending";

        if (resetProgress) {
            session.lastProcessedIndex = 0;
            session.sentCount = 0;
            session.failedCount = 0;
            session.skippedCount = 0;
        }

        currentSession = session;
        pendingSession = null;
        uploadPersistence.saveCurrentUpload(session);
        return session;
    }

    /**
     * Resume a previously saved session.
     */
    public UploadSession resumeSession() {
        UploadSession toResume = currentSession;
        if (toResume == null) {
            toResume = uploadPersistence.loadCurrentUploadSync();
        }
        if (toResume == null) {
            toResume = pendingSession;
        }
        if (toResume == null) {
            return null;
        }

        currentSession = toResume;
        pendingSession = null;
        return toResume;
    }

    /**
     * Discard any pending or current session.
     */
    public void discardSession() {
        pendingSession = null;
        currentSession = null;
        uploadPersistence.clearCurrentUpload();
    }

    /**
     * Save the template to the current session.
     */
    public void saveTemplateToSession(String template) {
        if (currentSession != null) {
            currentSession.template = template;
            uploadPersistence.saveCurrentUpload(currentSession);
        }
    }

    /**
     * Persist current session state (e.g. after speed/slot changes).
     */
    public void persistSession() {
        if (currentSession != null) {
            uploadPersistence.saveCurrentUpload(currentSession);
        }
    }

    /**
     * Mark the current session as paused.
     */
    public void markPaused() {
        if (currentSession != null) {
            currentSession.isPaused = true;
            currentSession.processingStatus = "paused";
            uploadPersistence.saveCurrentUploadSync(currentSession);
        }
    }

    /**
     * Mark the current session as stopped.
     */
    public void markStopped() {
        if (currentSession != null) {
            currentSession.isStopped = true;
            currentSession.isPaused = false;
            currentSession.processingStatus = "stopped";
            uploadPersistence.saveCurrentUploadSync(currentSession);
        }
    }

    /**
     * Mark the current session as resumed for sending.
     */
    public void markResumedForSend() {
        if (currentSession != null) {
            currentSession.isPaused = false;
            currentSession.isStopped = false;
            currentSession.processingStatus = "sending";
            uploadPersistence.saveCurrentUploadSync(currentSession);
        }
    }

    /**
     * Update recipients in the session.
     */
    public void updateRecipients(List<Recipient> recipients) {
        if (currentSession != null) {
            currentSession.recipients = recipients;
            currentSession.totalRecords = recipients.size();
            currentSession.validRecords = recipients.size();
            uploadPersistence.saveCurrentUpload(currentSession);
        }
    }

    public interface ResumeCallback {
        void onResumeAvailable(boolean available);
    }
}
