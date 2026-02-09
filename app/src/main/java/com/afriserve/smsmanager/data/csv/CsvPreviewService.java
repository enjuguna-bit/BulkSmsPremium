package com.afriserve.smsmanager.data.csv;

import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Preview and validation service for CSV data
 */
public class CsvPreviewService {
    private static final String TAG = "CsvPreviewService";
    private static final int PREVIEW_COUNT = 3;

    /**
     * Generate message preview for first N recipients with data substitution
     */
    public static List<String> generateMessagePreviews(
            String template,
            List<CsvRecipient> recipients,
            int count
    ) {
        List<String> previews = new ArrayList<>();
        int actualCount = Math.min(count, recipients.size());

        for (int i = 0; i < actualCount; i++) {
            CsvRecipient recipient = recipients.get(i);
            String previewMessage = template;

            // Replace all placeholders with actual values
            if (recipient.getData() != null) {
                for (Map.Entry<String, String> entry : recipient.getData().entrySet()) {
                    String placeholder = "{" + entry.getKey().toLowerCase().replace(" ", "") + "}";
                    previewMessage = previewMessage.replace(placeholder, entry.getValue());
                }
            }

            // Add phone number to preview
            String preview = previewMessage + "\n\n[To: " + recipient.getPhoneNumber() + "]";
            previews.add(preview);
        }

        return previews;
    }

    /**
     * Validate phone numbers and return summary
     */
    public static PhoneValidationResult validatePhoneNumbers(List<CsvRecipient> recipients) {
        List<String> validNumbers = new ArrayList<>();
        List<String> invalidNumbers = new ArrayList<>();

        for (CsvRecipient recipient : recipients) {
            if (isValidPhoneNumber(recipient.getPhoneNumber())) {
                validNumbers.add(recipient.getPhoneNumber());
            } else {
                invalidNumbers.add(recipient.getPhoneNumber());
            }
        }

        return new PhoneValidationResult(
                recipients.size(),
                validNumbers.size(),
                invalidNumbers.size(),
                validNumbers,
                invalidNumbers
        );
    }

    /**
     * Create summary message for upload result
     */
    public static String createSummary(
            CsvParsingResult parsingResult,
            String fileName
    ) {
        StringBuilder summary = new StringBuilder();
        summary.append("📊 CSV Upload Summary\n");
        summary.append("━".repeat(40)).append("\n");
        summary.append("File: ").append(fileName).append("\n");
        summary.append("\n");
        summary.append("Recipients Detected: ").append(parsingResult.getTotalCount()).append("\n");
        summary.append("✓ Valid Recipients: ").append(parsingResult.getValidCount()).append("\n");
        summary.append("✗ Invalid Recipients: ").append(parsingResult.getInvalidCount()).append("\n");
        summary.append("\n");
        summary.append("Columns Available: ").append(parsingResult.getColumnCount()).append("\n");
        
        if (parsingResult.getDetectionResult() != null) {
            summary.append("Phone Column: ").append(
                parsingResult.getDetectionResult().getDetectedPhoneColumnName() != null ? 
                parsingResult.getDetectionResult().getDetectedPhoneColumnName() : "Not detected"
            ).append("\n");
        }
        
        summary.append("\n");
        
        if (parsingResult.getDetectionResult() != null && 
            parsingResult.getDetectionResult().getPotentialPhoneColumns() != null &&
            parsingResult.getDetectionResult().getPotentialPhoneColumns().size() > 1) {
            summary.append("⚠️  Multiple phone columns detected:\n");
            for (String column : parsingResult.getDetectionResult().getPotentialPhoneColumns()) {
                summary.append("  • ").append(column).append("\n");
            }
        }
        
        if (parsingResult.getParseErrors() != null && !parsingResult.getParseErrors().isEmpty()) {
            summary.append("\n");
            summary.append("⚠️  Parsing errors:\n");
            for (String error : parsingResult.getParseErrors()) {
                summary.append("  • ").append(error).append("\n");
            }
        }

        return summary.toString();
    }

    /**
     * Simple phone number validation
     */
    private static boolean isValidPhoneNumber(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.trim().isEmpty()) {
            return false;
        }

        String digitsOnly = phoneNumber
                .replace("+", "")
                .replace(" ", "")
                .replace("-", "")
                .replace("(", "")
                .replace(")", "")
                .replace(".", "")
                .replace(",", "");

        return digitsOnly.length() >= 10 && digitsOnly.length() <= 15 && digitsOnly.matches("\\d+");
    }
}
