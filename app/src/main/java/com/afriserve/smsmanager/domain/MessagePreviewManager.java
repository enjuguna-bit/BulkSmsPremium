package com.afriserve.smsmanager.domain;

import android.util.Log;

import com.afriserve.smsmanager.data.parser.TemplateVariableExtractor;
import com.afriserve.smsmanager.models.Recipient;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Manages template variable extraction and message preview generation.
 * Owns the debounced preview executor. Extracted from BulkSmsViewModel.
 */
@Singleton
public class MessagePreviewManager {
    private static final String TAG = "MessagePreviewManager";
    private static final int PREVIEW_COUNT = 5;
    private static final int VARIABLE_SAMPLE_COUNT = 25;
    private static final long PREVIEW_DEBOUNCE_MS = 180L;

    private final ScheduledExecutorService previewExecutor = Executors.newSingleThreadScheduledExecutor();
    private ScheduledFuture<?> previewFuture;
    private final Object previewLock = new Object();
    private volatile int previewSequence = 0;
    private volatile int variableSequence = 0;

    @Inject
    public MessagePreviewManager() {
    }

    /**
     * Extract template variables from recipient data and post results to the callback.
     */
    public void updateTemplateVariables(List<Recipient> currentData, VariableCallback callback) {
        final int sequence = ++variableSequence;
        final List<Recipient> sample = getRecipientSample(currentData, VARIABLE_SAMPLE_COUNT);
        previewExecutor.execute(() -> {
            List<Map<String, String>> data = convertRecipientsToRowList(sample, VARIABLE_SAMPLE_COUNT);
            if (data.isEmpty()) {
                if (sequence == variableSequence) {
                    callback.onVariablesReady(new ArrayList<>());
                }
                return;
            }
            List<TemplateVariableExtractor.TemplateVariable> variables =
                TemplateVariableExtractor.extractVariables(data);
            if (sequence == variableSequence) {
                callback.onVariablesReady(variables);
            }
        });
    }

    /**
     * Generate message previews for the given template and post results to the callback.
     */
    public void updateMessagePreviews(String template, List<Recipient> currentData, PreviewCallback callback) {
        if (template == null || template.trim().isEmpty()) {
            synchronized (previewLock) {
                previewSequence++;
                if (previewFuture != null) {
                    previewFuture.cancel(false);
                    previewFuture = null;
                }
            }
            callback.onPreviewsReady(new ArrayList<>());
            return;
        }

        final int sequence;
        synchronized (previewLock) {
            if (previewFuture != null) {
                previewFuture.cancel(false);
            }
            sequence = ++previewSequence;
            final List<Recipient> sample = getRecipientSample(currentData, PREVIEW_COUNT);
            previewFuture = previewExecutor.schedule(() -> {
                List<Map<String, String>> data = convertRecipientsToRowList(sample, PREVIEW_COUNT);
                List<TemplateVariableExtractor.MessagePreview> previews =
                    TemplateVariableExtractor.generatePreview(template, data, PREVIEW_COUNT);
                if (sequence == previewSequence) {
                    callback.onPreviewsReady(previews);
                }
            }, PREVIEW_DEBOUNCE_MS, TimeUnit.MILLISECONDS);
        }
    }

    /**
     * Schedule a debounced template save.
     */
    public void scheduleTemplateSave(String template, long debounceMs, Runnable saveAction) {
        previewExecutor.schedule(saveAction, debounceMs, TimeUnit.MILLISECONDS);
    }

    /**
     * Cancel outstanding preview futures and clear state.
     */
    public void clearAll() {
        synchronized (previewLock) {
            previewSequence++;
            if (previewFuture != null) {
                previewFuture.cancel(false);
                previewFuture = null;
            }
        }
    }

    /**
     * Shut down the background executor.
     */
    public void shutdown() {
        previewExecutor.shutdown();
    }

    // ---- Internal helpers ----

    private List<Recipient> getRecipientSample(List<Recipient> currentData, int maxCount) {
        List<Recipient> sample = new ArrayList<>();
        if (currentData == null || currentData.isEmpty()) {
            return sample;
        }
        int count = Math.min(maxCount, currentData.size());
        for (int i = 0; i < count; i++) {
            sample.add(currentData.get(i));
        }
        return sample;
    }

    /**
     * Convert recipient objects into row maps for template variable processing.
     */
    public List<Map<String, String>> convertRecipientsToRowList(List<Recipient> recipients, int maxCount) {
        List<Map<String, String>> rows = new ArrayList<>();
        if (recipients == null || recipients.isEmpty()) return rows;

        int count = Math.min(maxCount, recipients.size());
        for (int i = 0; i < count; i++) {
            Recipient r = recipients.get(i);
            Map<String, String> row = new HashMap<>();
            row.put("Name", r.getName() != null ? r.getName() : "");
            row.put("PhoneNumber", r.getPhone() != null ? r.getPhone() : "");
            row.put("Phone", r.getPhone() != null ? r.getPhone() : "");
            row.put("Mobile", r.getPhone() != null ? r.getPhone() : "");
            row.put("Amount", r.getAmount() != null ? String.valueOf(r.getAmount()) : "");
            if (r.getFields() != null) row.putAll(r.getFields());
            rows.add(row);
        }

        return rows;
    }

    // ---- Callbacks ----

    public interface VariableCallback {
        void onVariablesReady(List<TemplateVariableExtractor.TemplateVariable> variables);
    }

    public interface PreviewCallback {
        void onPreviewsReady(List<TemplateVariableExtractor.MessagePreview> previews);
    }
}
