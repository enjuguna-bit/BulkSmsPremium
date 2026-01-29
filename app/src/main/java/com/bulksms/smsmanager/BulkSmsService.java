package com.bulksms.smsmanager;

import android.content.Context;
import android.content.pm.PackageManager;
import android.Manifest;
import android.net.Uri;
import android.telephony.SmsManager;
import android.telephony.SubscriptionManager;
import android.telephony.SubscriptionInfo;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import androidx.core.content.ContextCompat;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.Locale;
import com.bulksms.smsmanager.data.dao.CampaignDao;
import com.bulksms.smsmanager.data.dao.SmsDao;
import com.bulksms.smsmanager.data.entity.CampaignEntity;
import com.bulksms.smsmanager.data.entity.SmsEntity;
import com.bulksms.smsmanager.models.Recipient;
import com.bulksms.smsmanager.data.compliance.RateLimitManager;
import com.bulksms.smsmanager.data.compliance.ComplianceManager;
import com.bulksms.smsmanager.data.tracking.DeliveryTracker;
import com.bulksms.smsmanager.data.tracking.EnhancedDeliveryTracker;
import com.bulksms.smsmanager.data.parser.ExcelParser;
import com.bulksms.smsmanager.data.parser.CsvParser;
import com.bulksms.smsmanager.data.parser.TransactionParser;
import com.bulksms.smsmanager.data.parser.PhoneNormalizer;
import com.bulksms.smsmanager.data.parser.DateParser;
import com.bulksms.smsmanager.data.parser.RecordParser;
import dagger.hilt.android.qualifiers.ApplicationContext;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import android.app.PendingIntent;
import javax.inject.Inject;
import javax.inject.Singleton;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * Premium Bulk SMS Service - Complete implementation
 * Integrated with Room database for campaign and SMS tracking
 */
@Singleton
public class BulkSmsService {
    private static final String TAG = "BulkSmsService";
    private final Context context;
    private final SmsDao smsDao;
    private final CampaignDao campaignDao;
    private final RateLimitManager rateLimitManager;
    private final ComplianceManager complianceManager;
    private final DeliveryTracker deliveryTracker;
    private final ExecutorService executorService;
    private final Handler mainHandler;

    @Inject
    public BulkSmsService(
            @ApplicationContext @NonNull Context context,
            @NonNull SmsDao smsDao,
            @NonNull CampaignDao campaignDao,
            @NonNull RateLimitManager rateLimitManager,
            @NonNull ComplianceManager complianceManager,
            @NonNull DeliveryTracker deliveryTracker) {
        this.context = context;
        this.smsDao = smsDao;
        this.campaignDao = campaignDao;
        this.rateLimitManager = rateLimitManager;
        this.complianceManager = complianceManager;
        this.deliveryTracker = deliveryTracker;
        this.executorService = Executors.newFixedThreadPool(4);
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    public interface ProgressCallback {
        void onProgress(int sent, int total);
    }

    /**
     * Safely call ProgressCallback on main thread
     */
    private void updateProgressOnMainThread(@NonNull ProgressCallback callback, int sent, int total) {
        mainHandler.post(() -> callback.onProgress(sent, total));
    }

    /**
     * Get available SIM slots with their information
     * @return List of available SIM slot information
     */
    public List<SimSlotInfo> getAvailableSimSlots() {
        List<SimSlotInfo> slots = new ArrayList<>();
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
                SubscriptionManager sm = (SubscriptionManager) context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE);
                if (sm != null) {
                    try {
                        java.util.List<android.telephony.SubscriptionInfo> infos = sm.getActiveSubscriptionInfoList();
                        if (infos != null) {
                            for (android.telephony.SubscriptionInfo info : infos) {
                                int simSlot = info.getSimSlotIndex();
                                String carrierName = info.getCarrierName() != null ? info.getCarrierName().toString() : "Unknown";
                                String displayName = info.getDisplayName() != null ? info.getDisplayName().toString() : "SIM " + (simSlot + 1);
                                
                                slots.add(new SimSlotInfo(simSlot, carrierName, displayName));
                                Log.d(TAG, "Found SIM slot " + simSlot + ": " + carrierName + " (" + displayName + ")");
                            }
                        }
                    } catch (SecurityException e) {
                        Log.w(TAG, "No permission to access subscription info", e);
                    } catch (Exception e) {
                        Log.w(TAG, "Error getting subscription info", e);
                    }
                }
            } else {
                Log.w(TAG, "READ_PHONE_STATE permission not granted");
            }
        }
        
        if (slots.isEmpty()) {
            slots.add(new SimSlotInfo(0, "Default", "SIM 1"));
        }
        
        return slots;
    }

    /**
     * Get the correct SmsManager for the specified SIM slot
     * @param simSlot The SIM slot index (0 for first SIM, 1 for second SIM, etc.)
     * @return The appropriate SmsManager instance
     */
    private SmsManager getSmsManagerForSlot(int simSlot) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            SubscriptionManager subscriptionManager = (SubscriptionManager) context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE);
            if (subscriptionManager != null) {
                try {
                    java.util.List<android.telephony.SubscriptionInfo> activeSubs = subscriptionManager.getActiveSubscriptionInfoList();
                    if (activeSubs != null) {
                        for (android.telephony.SubscriptionInfo info : activeSubs) {
                            if (info.getSimSlotIndex() == simSlot) {
                                int subscriptionId = info.getSubscriptionId();
                                return SmsManager.getSmsManagerForSubscriptionId(subscriptionId);
                            }
                        }
                    }
                } catch (SecurityException e) {
                    Log.w(TAG, "No permission to access subscription info, falling back to default", e);
                } catch (Exception e) {
                    Log.w(TAG, "Error getting subscription info, falling back to default", e);
                }
            }
        }
        // Fallback to default for older Android versions or if SIM slot not found
        return SmsManager.getDefault();
    }

    /**
     * Send bulk SMS campaign asynchronously
     * Uses rate limiting and compliance checking
     */
    public Completable sendBulkSmsAsync(
            @NonNull List<Recipient> recipients,
            @NonNull String template,
            int simSlot,
            @Nullable String campaignName,
            @NonNull ProgressCallback progressCallback
    ) {
        return Completable.fromAction(() -> {
            try {
                int total = recipients.size();
                int sent = 0;
                int failed = 0;
                int skipped = 0;
                
                // Create campaign entity
                CampaignEntity campaign = new CampaignEntity();
                campaign.name = campaignName != null ? campaignName : "Bulk Campaign";
                campaign.description = "Bulk SMS campaign with " + total + " recipients";
                campaign.status = "ACTIVE";
                campaign.recipientCount = total;
                campaign.createdAt = System.currentTimeMillis();
                campaign.updatedAt = System.currentTimeMillis();
                campaign.startedAt = System.currentTimeMillis();
                
                // Insert campaign and get ID
                Long campaignId = campaignDao.insertCampaign(campaign).blockingGet();
                Log.d(TAG, "Created campaign with ID: " + campaignId);

                // Update initial progress
                updateProgressOnMainThread(progressCallback, 0, total);

                // Get the correct SmsManager for the specified SIM slot
                SmsManager smsManager = getSmsManagerForSlot(simSlot);
                Log.d(TAG, "Using SmsManager for SIM slot: " + simSlot);

                for (int i = 0; i < recipients.size(); i++) {
                    Recipient recipient = recipients.get(i);
                    
                    try {
                        // Check compliance before sending
                        ComplianceManager.ComplianceResult complianceResult = 
                            complianceManager.checkCompliance(recipient.getPhone(), "MARKETING").blockingGet();
                        
                        if (!complianceResult.isCompliant()) {
                            Log.w(TAG, "Skipping non-compliant number: " + recipient.getPhone() + 
                                  " - " + complianceResult.getReason());
                            skipped++;
                            updateProgressOnMainThread(progressCallback, sent + failed + skipped, total);
                            continue;
                        }
                        
                        // Apply rate limiting
                        long delay = rateLimitManager.getDelayBeforeNextSend(recipient.getPhone());
                        if (delay > 0) {
                            Log.d(TAG, "Rate limiting: waiting " + delay + "ms for " + recipient.getPhone());
                            Thread.sleep(delay);
                        }
                        
                        String message = formatMessage(template, recipient);
                        
                        // Track SMS entity ID and entity for potential failure handling
                        Long smsId = null;
                        SmsEntity smsEntity = null;
                        
                        try {
                            // Create SMS entity before sending
                            smsEntity = new SmsEntity();
                            smsEntity.phoneNumber = recipient.getPhone();
                            smsEntity.message = message;
                            smsEntity.status = "PENDING";
                            smsEntity.campaignId = campaignId;
                            smsEntity.createdAt = System.currentTimeMillis();
                            
                            // Save SMS entity before sending
                            smsId = smsDao.insertSms(smsEntity).blockingGet();
                            Log.d(TAG, "Created SMS entity with ID: " + smsId);
                            
                            // Create delivery tracking intents
                            DeliveryTracker.DeliveryIntents deliveryIntents = 
                                deliveryTracker.createDeliveryIntents(String.valueOf(smsId));
                            
                            // Send SMS with delivery tracking
                            ArrayList<String> parts = smsManager.divideMessage(message);
                            if (parts.size() == 1) {
                                // Single part message
                                smsManager.sendTextMessage(
                                    recipient.getPhone(), 
                                    null, 
                                    message, 
                                    deliveryIntents != null ? deliveryIntents.sentIntent : null,
                                    deliveryIntents != null ? deliveryIntents.deliveredIntent : null
                                );
                            } else {
                                // Multipart message - create proper intent arrays
                                ArrayList<PendingIntent> sentIntents = null;
                                ArrayList<PendingIntent> deliveredIntents = null;
                                
                                if (deliveryIntents != null) {
                                    sentIntents = new ArrayList<>(parts.size());
                                    deliveredIntents = new ArrayList<>(parts.size());
                                    
                                    // Use the same intents for all parts (common pattern)
                                    for (int j = 0; j < parts.size(); j++) {
                                        sentIntents.add(deliveryIntents.sentIntent);
                                        deliveredIntents.add(deliveryIntents.deliveredIntent);
                                    }
                                }
                                
                                smsManager.sendMultipartTextMessage(
                                    recipient.getPhone(), 
                                    null, 
                                    parts, 
                                    sentIntents,
                                    deliveredIntents
                                );
                            }
                            
                            // Record send for rate limiting
                            rateLimitManager.recordSend(recipient.getPhone());
                            
                            // Update SMS status to SENT
                            smsEntity.status = "SENT";
                            smsEntity.sentAt = System.currentTimeMillis();
                            smsDao.updateSms(smsEntity).blockingAwait();
                            
                            // Update campaign counts
                            campaignDao.incrementSentCount(campaignId).blockingAwait();
                            
                            sent++;
                            Log.d(TAG, "✅ SMS sent to: " + recipient.getPhone());
                            
                        } catch (Exception e) {
                            failed++;
                            
                            // Update existing entity if we have one, otherwise create new
                            if (smsId != null && smsEntity != null) {
                                // Update the existing PENDING entity to FAILED
                                smsEntity.status = "FAILED";
                                smsEntity.errorMessage = e.getMessage();
                                smsEntity.errorCode = "SEND_ERROR";
                                smsEntity.sentAt = System.currentTimeMillis();
                                smsDao.updateSms(smsEntity).blockingAwait();
                            } else {
                                // Create new FAILED entity (fallback case)
                                SmsEntity failedEntity = new SmsEntity();
                                failedEntity.phoneNumber = recipient.getPhone();
                                failedEntity.message = message;
                                failedEntity.status = "FAILED";
                                failedEntity.errorMessage = e.getMessage();
                                failedEntity.errorCode = "SEND_ERROR";
                                failedEntity.campaignId = campaignId;
                                failedEntity.createdAt = System.currentTimeMillis();
                                failedEntity.sentAt = System.currentTimeMillis();
                                
                                try {
                                    smsDao.insertSms(failedEntity).blockingGet();
                                } catch (Exception insertError) {
                                    Log.e(TAG, "Failed to save failed SMS", insertError);
                                }
                            }
                            
                            // Update campaign counts
                            try {
                                campaignDao.incrementFailedCount(campaignId).blockingAwait();
                            } catch (Exception updateError) {
                                Log.e(TAG, "Failed to update campaign counts", updateError);
                            }
                            
                            Log.e(TAG, "❌ Failed to send SMS to: " + recipient.getPhone(), e);
                        }
                        
                    } catch (Exception e) {
                        // Handle outer-level exceptions (compliance, rate limiting, etc.)
                        failed++;
                        Log.e(TAG, "❌ Failed to process recipient: " + recipient.getPhone(), e);
                    }

                    // Update progress
                    updateProgressOnMainThread(progressCallback, sent + failed + skipped, total);
                }
                
                // Update campaign status to COMPLETED
                try {
                    CampaignEntity updatedCampaign = campaignDao.getCampaignById(campaignId).blockingGet();
                    if (updatedCampaign != null) {
                        updatedCampaign.status = "COMPLETED";
                        updatedCampaign.completedAt = System.currentTimeMillis();
                        updatedCampaign.updatedAt = System.currentTimeMillis();
                        updatedCampaign.sentCount = sent;
                        updatedCampaign.failedCount = failed;
                        updatedCampaign.skippedCount = skipped;
                        campaignDao.updateCampaign(updatedCampaign).blockingAwait();
                        Log.d(TAG, "Campaign status updated to COMPLETED");
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Failed to update campaign status", e);
                }

                Log.d(TAG, "📊 Campaign completed - Sent: " + sent + ", Failed: " + failed + ", Skipped: " + skipped);

            } catch (Exception e) {
                Log.e(TAG, "❌ Bulk SMS campaign failed", e);
                throw new RuntimeException(e);
            }
        }).subscribeOn(Schedulers.io());
    }

    /**
     * Format message with recipient variables
     */
    @NonNull
    public String formatMessage(@NonNull String template, @NonNull Recipient recipient) {
        String formatted = template;

        // Replace placeholders
        if (recipient.getName() != null) {
            formatted = formatted.replace("{name}", recipient.getName());
        }
        formatted = formatted.replace("{phone}", recipient.getPhone());

        if (recipient.getAmount() != null) {
            formatted = formatted.replace("{amount}", recipient.getAmount().toString());
        }

        // Replace custom fields
        if (recipient.getFields() != null) {
            for (Map.Entry<String, String> entry : recipient.getFields().entrySet()) {
                formatted = formatted.replace("{" + entry.getKey() + "}", entry.getValue());
            }
        }

        return formatted;
    }

    /**
     * Parse a single CSV line handling quoted fields
     * Supports basic CSV format with quoted fields containing commas
     * @param line The CSV line to parse
     * @return Array of parsed field values
     */
    @NonNull
    private String[] parseCsvLine(@NonNull String line) {
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            
            if (c == '"') {
                // Handle escaped quotes (double quotes)
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++; // Skip the next quote
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (c == ',' && !inQuotes) {
                // End of field
                result.add(current.toString().trim());
                current = new StringBuilder();
            } else {
                current.append(c);
            }
        }
        
        // Add the last field
        result.add(current.toString().trim());
        
        return result.toArray(new String[0]);
    }

    /**
     * Parse CSV file to extract recipients
     */
    @NonNull
    public List<Recipient> parseCsv(@NonNull Uri uri) throws IOException {
        List<Recipient> recipients = new ArrayList<>();
        InputStream inputStream = context.getContentResolver().openInputStream(uri);
        
        if (inputStream == null) {
            throw new IOException("Cannot open input stream for URI: " + uri);
        }

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
            String line;
            List<String> headers = new ArrayList<>();
            boolean isFirstLine = true;

            while ((line = reader.readLine()) != null) {
                String[] values = parseCsvLine(line);

                if (isFirstLine) {
                    // Parse headers
                    for (String header : values) {
                        headers.add(header.trim().toLowerCase(Locale.ROOT));
                    }
                    isFirstLine = false;
                    continue;
                }

                // Parse recipient data
                Map<String, String> fields = new HashMap<>();
                String name = null;
                String phone = null;
                Double amount = null;

                for (int i = 0; i < Math.min(values.length, headers.size()); i++) {
                    String header = headers.get(i);
                    String value = values[i].trim();

                    switch (header) {
                        case "name":
                            name = value;
                            break;
                        case "phone":
                        case "mobile":
                        case "number":
                            phone = value;
                            break;
                        case "amount":
                            try {
                                amount = Double.parseDouble(value);
                            } catch (NumberFormatException ignored) {}
                            break;
                        default:
                            fields.put(header, value);
                            break;
                    }
                }

                if (phone != null && !phone.isEmpty()) {
                    recipients.add(new Recipient(name, phone, amount, false, fields));
                }
            }
        }

        return recipients;
    }

    /**
     * Enhanced file parser supporting Excel (.xlsx, .xls) and CSV files
     * Uses smart column mapping with aliases
     */
    @NonNull
    public ExcelParser.ParseResult parseImportFile(@NonNull Uri uri, @NonNull String fileName) throws IOException {
        // Copy file to temporary location for parsing
        File tempFile = copyUriToTempFile(uri, fileName);
        
        try {
            return ExcelParser.parseImportFile(tempFile.getAbsolutePath(), fileName);
        } finally {
            // Clean up temporary file
            if (tempFile.exists()) {
                tempFile.delete();
            }
        }
    }

    /**
     * Parse SMS message to extract transaction information
     */
    @Nullable
    public TransactionParser.Transaction parseSmsTransaction(@NonNull String message) {
        return TransactionParser.parseMobileMoneyTransaction(message);
    }

    /**
     * Enhanced recipient parsing with record validation
     */
    @NonNull
    public List<Recipient> parseAndValidateRecipients(@NonNull List<Map<String, String>> rawData, boolean isLoanData) {
        List<Recipient> validRecipients = new ArrayList<>();
        List<String> validationErrors = new ArrayList<>();
        
        for (int i = 0; i < rawData.size(); i++) {
            Map<String, String> row = rawData.get(i);
            
            try {
                if (isLoanData) {
                    RecordParser.LoanRecord loanRecord = RecordParser.parseLoanRecord(row);
                    if (RecordParser.isValidLoanRecord(loanRecord)) {
                        Recipient recipient = RecordParser.loanToRecipient(loanRecord);
                        validRecipients.add(recipient);
                    } else {
                        String error = RecordParser.getLoanValidationErrors(loanRecord);
                        validationErrors.add("Row " + (i + 1) + ": " + error);
                    }
                } else {
                    RecordParser.CustomerRecord customerRecord = RecordParser.parseCustomerRow(row);
                    if (RecordParser.isValidCustomerRecord(customerRecord)) {
                        Recipient recipient = RecordParser.customerToRecipient(customerRecord);
                        validRecipients.add(recipient);
                    } else {
                        String error = RecordParser.getCustomerValidationErrors(customerRecord);
                        validationErrors.add("Row " + (i + 1) + ": " + error);
                    }
                }
            } catch (Exception e) {
                validationErrors.add("Row " + (i + 1) + ": " + e.getMessage());
                Log.e(TAG, "Error parsing row " + (i + 1), e);
            }
        }
        
        // Log validation results
        if (!validationErrors.isEmpty()) {
            Log.w(TAG, "Validation errors found: " + validationErrors.size() + " out of " + rawData.size() + " records");
            for (String error : validationErrors.subList(0, Math.min(10, validationErrors.size()))) {
                Log.w(TAG, error);
            }
        }
        
        Log.d(TAG, "Successfully parsed " + validRecipients.size() + " valid recipients from " + rawData.size() + " records");
        return validRecipients;
    }

    /**
     * Enhanced message formatting with dynamic placeholder support
     */
    @NonNull
    public String formatMessageWithDynamicPlaceholders(@NonNull String template, @NonNull Recipient recipient) {
        try {
            String result = template;
            
            // Built-in placeholders (backward compatible)
            result = result.replace("{name}", recipient.getName() != null ? recipient.getName() : "");
            result = result.replace("{phone}", recipient.getPhone() != null ? recipient.getPhone() : "");
            result = result.replace("{amount}", recipient.getAmount() != null ? 
                String.format("%,.2f", recipient.getAmount()) : "0.00");
            
            // Dynamic placeholders from recipient fields
            if (recipient.getFields() != null) {
                for (Map.Entry<String, String> entry : recipient.getFields().entrySet()) {
                    String key = entry.getKey();
                    String value = entry.getValue();
                    
                    if (value != null && !value.isEmpty()) {
                        // Format numeric values
                        try {
                            double numValue = Double.parseDouble(value);
                            value = String.format("%,.2f", numValue);
                        } catch (NumberFormatException e) {
                            // Keep as string if not numeric
                        }
                        
                        result = result.replace("{" + key + "}", value);
                    } else {
                        result = result.replace("{" + key + "}", "");
                    }
                }
            }
            
            return result;
        } catch (Exception e) {
            Log.e(TAG, "Error formatting message", e);
            return template;
        }
    }

    /**
     * Extract placeholders from template
     */
    @NonNull
    public List<String> extractPlaceholders(@NonNull String template) {
        List<String> placeholders = new ArrayList<>();
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\\{([^}]+)\\}");
        java.util.regex.Matcher matcher = pattern.matcher(template);
        
        while (matcher.find()) {
            placeholders.add(matcher.group(1));
        }
        
        return placeholders;
    }

    /**
     * Validate template placeholders against recipient data
     */
    @NonNull
    public TemplateValidationResult validateTemplatePlaceholders(@NonNull String template, @NonNull List<Recipient> recipients) {
        List<String> placeholders = extractPlaceholders(template);
        List<String> missingPlaceholders = new ArrayList<>();
        List<String> availablePlaceholders = new ArrayList<>();
        
        if (recipients.isEmpty()) {
            return new TemplateValidationResult(placeholders, placeholders, "No recipients to validate against");
        }
        
        // Get all available fields from first recipient (assuming consistent structure)
        Recipient sampleRecipient = recipients.get(0);
        Map<String, String> allFields = new HashMap<>();
        
        // Add built-in fields
        if (sampleRecipient.getName() != null) allFields.put("name", sampleRecipient.getName());
        if (sampleRecipient.getPhone() != null) allFields.put("phone", sampleRecipient.getPhone());
        if (sampleRecipient.getAmount() != null) allFields.put("amount", String.valueOf(sampleRecipient.getAmount()));
        
        // Add custom fields
        if (sampleRecipient.getFields() != null) {
            allFields.putAll(sampleRecipient.getFields());
        }
        
        // Check each placeholder
        for (String placeholder : placeholders) {
            if (allFields.containsKey(placeholder)) {
                availablePlaceholders.add(placeholder);
            } else {
                missingPlaceholders.add(placeholder);
            }
        }
        
        String message = missingPlaceholders.isEmpty() ? 
            "All placeholders are available" : 
            "Missing placeholders: " + String.join(", ", missingPlaceholders);
        
        return new TemplateValidationResult(availablePlaceholders, missingPlaceholders, message);
    }

    /**
     * Get phone number statistics for recipients
     */
    @NonNull
    public PhoneStatistics getPhoneStatistics(@NonNull List<Recipient> recipients) {
        PhoneStatistics stats = new PhoneStatistics();
        
        for (Recipient recipient : recipients) {
            if (recipient.getPhone() == null || recipient.getPhone().isEmpty()) {
                continue;
            }
            
            PhoneNormalizer.PhoneNumberType type = PhoneNormalizer.getPhoneNumberType(recipient.getPhone());
            
            stats.totalRecipients++;
            
            switch (type) {
                case KENYA_MOBILE:
                    stats.kenyaMobileCount++;
                    break;
                case INTERNATIONAL:
                    stats.internationalCount++;
                    break;
                case OTHER:
                    stats.otherCount++;
                    break;
                case INVALID:
                    stats.invalidCount++;
                    break;
            }
        }
        
        return stats;
    }

    /**
     * Normalize phone numbers in recipient list
     */
    @NonNull
    public List<Recipient> normalizePhoneNumbers(@NonNull List<Recipient> recipients) {
        List<Recipient> normalizedRecipients = new ArrayList<>();
        
        for (Recipient recipient : recipients) {
            if (recipient.getPhone() != null && !recipient.getPhone().isEmpty()) {
                String normalizedPhone = PhoneNormalizer.normalizePhone(recipient.getPhone());
                if (!normalizedPhone.isEmpty()) {
                    Recipient normalizedRecipient = new Recipient(
                        recipient.getName(),
                        normalizedPhone,
                        recipient.getAmount(),
                        recipient.isProcessed(),
                        recipient.getFields()
                    );
                    normalizedRecipients.add(normalizedRecipient);
                }
            }
        }
        
        return normalizedRecipients;
    }

    /**
     * Parse Excel file with smart mapping (enhanced version of parseCsv)
     */
    @NonNull
    public ExcelParser.ParseResult parseExcelSmart(@NonNull Uri uri, @NonNull String fileName) throws IOException {
        return parseImportFile(uri, fileName);
    }

    /**
     * Copy URI content to temporary file for parsing
     */
    @NonNull
    private File copyUriToTempFile(@NonNull Uri uri, @NonNull String fileName) throws IOException {
        InputStream inputStream = context.getContentResolver().openInputStream(uri);
        if (inputStream == null) {
            throw new IOException("Cannot open input stream for URI: " + uri);
        }

        // Create temporary file
        File tempDir = new File(context.getCacheDir(), "temp_imports");
        if (!tempDir.exists()) {
            tempDir.mkdirs();
        }
        
        File tempFile = new File(tempDir, "temp_" + System.currentTimeMillis() + "_" + fileName);
        
        try (FileOutputStream outputStream = new FileOutputStream(tempFile)) {
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
        } finally {
            inputStream.close();
        }
        
        return tempFile;
    }

    /**
     * Detect file type from file name
     */
    @NonNull
    public ExcelParser.ImportFileType detectFileType(@NonNull String fileName) {
        return ExcelParser.detectFileType(fileName);
    }

    /**
     * Check if file type is supported
     */
    public boolean isFileTypeSupported(@NonNull String fileName) {
        return ExcelParser.isFileTypeSupported(fileName);
    }

    /**
     * Get file type display name for user feedback
     */
    @NonNull
    public String getFileTypeDisplayName(@NonNull String fileName) {
        return ExcelParser.getFileTypeDisplayName(fileName);
    }

    /**
     * Get file preview data for UI display
     */
    @NonNull
    public FilePreviewData getFilePreview(@NonNull Uri uri, @NonNull String fileName) throws IOException {
        ExcelParser.ParseResult result = parseImportFile(uri, fileName);
        
        List<String> headers = new ArrayList<>();
        List<List<String>> rows = new ArrayList<>();
        
        if (!result.recipients.isEmpty()) {
            // Get headers from mapping or use defaults
            if (result.mapping.name != null) headers.add(result.mapping.name);
            if (result.mapping.phone != null) headers.add(result.mapping.phone);
            if (result.mapping.amount != null) headers.add(result.mapping.amount);
            
            // Ensure we have at least name, phone, amount headers
            if (headers.isEmpty()) {
                headers.add("Name");
                headers.add("Phone");
                headers.add("Amount");
            }
            
            // Add preview rows (first 5)
            int previewCount = Math.min(5, result.recipients.size());
            for (int i = 0; i < previewCount; i++) {
                Recipient recipient = result.recipients.get(i);
                List<String> row = new ArrayList<>();
                row.add(recipient.getName() != null ? recipient.getName() : "");
                row.add(recipient.getPhone() != null ? recipient.getPhone() : "");
                row.add(recipient.getAmount() != null ? recipient.getAmount().toString() : "");
                rows.add(row);
            }
        }
        
        return new FilePreviewData(headers, rows, result.recipients.size());
    }

    /**
     * Validate message content
     */
    @NonNull
    public ValidationResult validateMessage(@NonNull String message) {
        if (message.trim().isEmpty()) {
            return new ValidationResult(false, "Message cannot be empty");
        }

        if (message.length() > 1600) {
            return new ValidationResult(false, "Message too long (max 1600 characters)");
        }

        return new ValidationResult(true, "Message is valid");
    }

    /**
     * Legacy send bulk SMS method for backward compatibility
     */
    public void sendBulkSms(
            @NonNull List<Recipient> recipients,
            @NonNull String template,
            int simSlot,
            @Nullable String campaignName,
            @NonNull ProgressCallback progressCallback
    ) {
        sendBulkSmsAsync(recipients, template, simSlot, campaignName, progressCallback)
            .subscribe(
                () -> Log.d(TAG, "Legacy bulk SMS completed"),
                error -> Log.e(TAG, "Legacy bulk SMS failed", error)
            );
    }

    /**
     * Analyze message for optimization suggestions
     */
    @NonNull
    public MessageAnalysis analyzeMessage(@NonNull String message) {
        int originalLength = message.length();
        boolean willConcatenate = originalLength > 160;
        int estimatedParts = willConcatenate ? (int) Math.ceil(originalLength / 153.0) : 1;
        
        DeliveryRisk risk = DeliveryRisk.LOW;
        String suggestion = "";
        
        if (originalLength > 160) {
            risk = DeliveryRisk.MEDIUM;
            suggestion = "Consider shortening message to avoid concatenation";
        }
        
        if (originalLength > 480) {
            risk = DeliveryRisk.HIGH;
            suggestion = "Message is very long, consider splitting into multiple messages";
        }

        return new MessageAnalysis(originalLength, willConcatenate, estimatedParts, risk, suggestion);
    }

    /**
     * Estimate send time for bulk SMS
     */
    public long estimateSendTime(int recipientCount, int sendSpeedMs) {
        // Base time + time per recipient
        return 1000 + (recipientCount * sendSpeedMs);
    }

    /**
     * Cleanup resources
     * Note: As a singleton, this is managed by Hilt lifecycle
     * Only call shutdown if explicitly needed
     */
    public void shutdown() {
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
        }
    }

    // ==================== INNER CLASSES ====================

    public static class SimSlotInfo {
        private final int slotIndex;
        private final String carrierName;
        private final String displayName;

        public SimSlotInfo(int slotIndex, String carrierName, String displayName) {
            this.slotIndex = slotIndex;
            this.carrierName = carrierName;
            this.displayName = displayName;
        }

        public int getSlotIndex() { return slotIndex; }
        public String getCarrierName() { return carrierName; }
        public String getDisplayName() { return displayName; }
    }

    public static class ValidationResult {
        private final boolean isValid;
        private final String errorMessage;

        public ValidationResult(boolean isValid, String errorMessage) {
            this.isValid = isValid;
            this.errorMessage = errorMessage;
        }

        public boolean isValid() { return isValid; }
        public String getErrorMessage() { return errorMessage; }
    }

    public static class MessageAnalysis {
        private final int originalLength;
        private final boolean willConcatenate;
        private final int estimatedParts;
        private final DeliveryRisk deliveryRisk;
        private final String suggestedOptimization;

        public MessageAnalysis(int originalLength, boolean willConcatenate, int estimatedParts,
                             DeliveryRisk deliveryRisk, String suggestedOptimization) {
            this.originalLength = originalLength;
            this.willConcatenate = willConcatenate;
            this.estimatedParts = estimatedParts;
            this.deliveryRisk = deliveryRisk;
            this.suggestedOptimization = suggestedOptimization;
        }

        public int getOriginalLength() { return originalLength; }
        public boolean getWillConcatenate() { return willConcatenate; }
        public int getEstimatedParts() { return estimatedParts; }
        public DeliveryRisk getDeliveryRisk() { return deliveryRisk; }
        public String getSuggestedOptimization() { return suggestedOptimization; }
    }

    public enum DeliveryRisk {
        LOW, MEDIUM, HIGH
    }

    public static class BillingResult {
        private final boolean success;
        private final String error;
        private final boolean blocked;
        private final String reason;

        public BillingResult(boolean success, String error, boolean blocked, String reason) {
            this.success = success;
            this.error = error;
            this.blocked = blocked;
            this.reason = reason;
        }

        public boolean getSuccess() { return success; }
        public String getError() { return error; }
        public boolean getBlocked() { return blocked; }
        public String getReason() { return reason; }
    }

    public static class SubscriptionInfo {
        private final boolean isActive;
        private final String planName;
        private final Long expiryDate;
        private final int smsLimit;
        private final int smsUsed;
        private final boolean isTrial;
        private final Long trialEndsAt;

        public SubscriptionInfo(boolean isActive, String planName, Long expiryDate,
                              int smsLimit, int smsUsed, boolean isTrial, Long trialEndsAt) {
            this.isActive = isActive;
            this.planName = planName;
            this.expiryDate = expiryDate;
            this.smsLimit = smsLimit;
            this.smsUsed = smsUsed;
            this.isTrial = isTrial;
            this.trialEndsAt = trialEndsAt;
        }

        public boolean isActive() { return isActive; }
        public String getPlanName() { return planName; }
        public Long getExpiryDate() { return expiryDate; }
        public int getSmsLimit() { return smsLimit; }
        public int getSmsUsed() { return smsUsed; }
        public boolean isTrial() { return isTrial; }
        public Long getTrialEndsAt() { return trialEndsAt; }
    }

    public static class FilePreviewData {
        private final List<String> headers;
        private final List<List<String>> rows;
        private final int totalRows;

        public FilePreviewData(List<String> headers, List<List<String>> rows, int totalRows) {
            this.headers = headers;
            this.rows = rows;
            this.totalRows = totalRows;
        }

        public List<String> getHeaders() { return headers; }
        public List<List<String>> getRows() { return rows; }
        public int getTotalRows() { return totalRows; }
    }

    /**
     * Template validation result data class
     */
    public static class TemplateValidationResult {
        private final List<String> availablePlaceholders;
        private final List<String> missingPlaceholders;
        private final String message;

        public TemplateValidationResult(List<String> availablePlaceholders, List<String> missingPlaceholders, String message) {
            this.availablePlaceholders = availablePlaceholders;
            this.missingPlaceholders = missingPlaceholders;
            this.message = message;
        }

        public List<String> getAvailablePlaceholders() { return availablePlaceholders; }
        public List<String> getMissingPlaceholders() { return missingPlaceholders; }
        public String getMessage() { return message; }
        public boolean isValid() { return missingPlaceholders.isEmpty(); }
    }

    /**
     * Phone statistics data class
     */
    public static class PhoneStatistics {
        public int totalRecipients = 0;
        public int kenyaMobileCount = 0;
        public int internationalCount = 0;
        public int otherCount = 0;
        public int invalidCount = 0;

        public double getKenyaMobilePercentage() {
            return totalRecipients > 0 ? (double) kenyaMobileCount / totalRecipients * 100 : 0;
        }

        public double getInternationalPercentage() {
            return totalRecipients > 0 ? (double) internationalCount / totalRecipients * 100 : 0;
        }

        public double getValidPercentage() {
            return totalRecipients > 0 ? (double) (kenyaMobileCount + internationalCount) / totalRecipients * 100 : 0;
        }

        @Override
        public String toString() {
            return "PhoneStatistics{" +
                   "total=" + totalRecipients +
                   ", kenya=" + kenyaMobileCount + " (" + String.format("%.1f", getKenyaMobilePercentage()) + "%)" +
                   ", international=" + internationalCount + " (" + String.format("%.1f", getInternationalPercentage()) + "%)" +
                   ", valid=" + (kenyaMobileCount + internationalCount) + " (" + String.format("%.1f", getValidPercentage()) + "%)" +
                   ", invalid=" + invalidCount +
                   '}';
        }
    }

    /**
     * Error information data class
     */
    public static class ErrorInfo {
        private final String message;
        private final String code;
        private final long timestamp;
        private final Map<String, Object> context;

        public ErrorInfo(String message, String code, long timestamp, Map<String, Object> context) {
            this.message = message;
            this.code = code;
            this.timestamp = timestamp;
            this.context = context;
        }

        public String getMessage() { return message; }
        public String getCode() { return code; }
        public long getTimestamp() { return timestamp; }
        public Map<String, Object> getContext() { return context; }
    }
}
