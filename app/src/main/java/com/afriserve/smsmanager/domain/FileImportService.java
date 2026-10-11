package com.afriserve.smsmanager.domain;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.afriserve.smsmanager.data.parser.ExcelParser;
import com.afriserve.smsmanager.data.parser.PhoneNormalizer;
import com.afriserve.smsmanager.data.parser.RecordParser;
import com.afriserve.smsmanager.data.parser.TransactionParser;
import com.afriserve.smsmanager.models.Recipient;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Singleton;

import dagger.hilt.android.qualifiers.ApplicationContext;

/**
 * Handles all file import, parsing, and validation operations.
 * Extracted from BulkSmsService to separate I/O concerns from sending logic.
 */
@Singleton
public class FileImportService {
    private static final String TAG = "FileImportService";

    private final Context context;

    @Inject
    public FileImportService(@ApplicationContext @NonNull Context context) {
        this.context = context;
    }

    // ==================== Primary Import ====================

    /**
     * Enhanced file parser supporting Excel (.xlsx, .xls) and CSV files.
     */
    @NonNull
    public ExcelParser.ParseResult parseImportFile(@NonNull Uri uri, @NonNull String fileName) throws IOException {
        File tempFile = copyUriToTempFile(uri, fileName);
        try {
            return ExcelParser.parseImportFile(tempFile.getAbsolutePath(), fileName);
        } finally {
            if (tempFile.exists()) {
                tempFile.delete();
            }
        }
    }

    /**
     * Parse CSV file to extract recipients.
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
                    for (String header : values) {
                        headers.add(header.trim().toLowerCase(Locale.ROOT));
                    }
                    isFirstLine = false;
                    continue;
                }

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

    // ==================== Validation & Analysis ====================

    /**
     * Enhanced recipient parsing with record validation.
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
                        validRecipients.add(RecordParser.loanToRecipient(loanRecord));
                    } else {
                        validationErrors.add("Row " + (i + 1) + ": " + RecordParser.getLoanValidationErrors(loanRecord));
                    }
                } else {
                    RecordParser.CustomerRecord customerRecord = RecordParser.parseCustomerRow(row);
                    if (RecordParser.isValidCustomerRecord(customerRecord)) {
                        validRecipients.add(RecordParser.customerToRecipient(customerRecord));
                    } else {
                        validationErrors.add("Row " + (i + 1) + ": " + RecordParser.getCustomerValidationErrors(customerRecord));
                    }
                }
            } catch (Exception e) {
                validationErrors.add("Row " + (i + 1) + ": " + e.getMessage());
                Log.e(TAG, "Error parsing row " + (i + 1), e);
            }
        }

        if (!validationErrors.isEmpty()) {
            Log.w(TAG, "Validation errors: " + validationErrors.size() + " out of " + rawData.size());
            for (String error : validationErrors.subList(0, Math.min(10, validationErrors.size()))) {
                Log.w(TAG, error);
            }
        }

        Log.d(TAG, "Parsed " + validRecipients.size() + " valid recipients from " + rawData.size() + " records");
        return validRecipients;
    }

    /**
     * Normalize phone numbers in recipient list.
     */
    @NonNull
    public List<Recipient> normalizePhoneNumbers(@NonNull List<Recipient> recipients) {
        List<Recipient> normalized = new ArrayList<>();
        for (Recipient recipient : recipients) {
            if (recipient.getPhone() != null && !recipient.getPhone().isEmpty()) {
                String normalizedPhone = PhoneNormalizer.normalizePhone(recipient.getPhone());
                if (!normalizedPhone.isEmpty()) {
                    normalized.add(new Recipient(
                        recipient.getName(), normalizedPhone, recipient.getAmount(),
                        recipient.isProcessed(), recipient.getFields()));
                }
            }
        }
        return normalized;
    }

    /**
     * Get phone number statistics for recipients.
     */
    @NonNull
    public PhoneStatistics getPhoneStatistics(@NonNull List<Recipient> recipients) {
        PhoneStatistics stats = new PhoneStatistics();
        for (Recipient recipient : recipients) {
            if (recipient.getPhone() == null || recipient.getPhone().isEmpty()) continue;
            PhoneNormalizer.PhoneNumberType type = PhoneNormalizer.getPhoneNumberType(recipient.getPhone());
            stats.totalRecipients++;
            switch (type) {
                case KENYA_MOBILE: stats.kenyaMobileCount++; break;
                case INTERNATIONAL: stats.internationalCount++; break;
                case OTHER: stats.otherCount++; break;
                case INVALID: stats.invalidCount++; break;
            }
        }
        return stats;
    }

    /**
     * Parse SMS transaction info.
     */
    @Nullable
    public TransactionParser.Transaction parseSmsTransaction(@NonNull String message) {
        return TransactionParser.parseMobileMoneyTransaction(message);
    }

    // ==================== File Type Utilities ====================

    @NonNull
    public ExcelParser.ImportFileType detectFileType(@NonNull String fileName) {
        return ExcelParser.detectFileType(fileName);
    }

    public boolean isFileTypeSupported(@NonNull String fileName) {
        return ExcelParser.isFileTypeSupported(fileName);
    }

    @NonNull
    public String getFileTypeDisplayName(@NonNull String fileName) {
        return ExcelParser.getFileTypeDisplayName(fileName);
    }

    // ==================== File Preview ====================

    @NonNull
    public FilePreviewData getFilePreview(@NonNull Uri uri, @NonNull String fileName) throws IOException {
        ExcelParser.ParseResult result = parseImportFile(uri, fileName);

        List<String> headers = new ArrayList<>();
        List<List<String>> rows = new ArrayList<>();

        if (!result.recipients.isEmpty()) {
            if (result.mapping.name != null) headers.add(result.mapping.name);
            if (result.mapping.phone != null) headers.add(result.mapping.phone);
            if (result.mapping.amount != null) headers.add(result.mapping.amount);

            if (headers.isEmpty()) {
                headers.add("Name");
                headers.add("Phone");
                headers.add("Amount");
            }

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

    // ==================== Internal Helpers ====================

    @NonNull
    public String[] parseCsvLine(@NonNull String line) {
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (c == ',' && !inQuotes) {
                result.add(current.toString().trim());
                current = new StringBuilder();
            } else {
                current.append(c);
            }
        }
        result.add(current.toString().trim());
        return result.toArray(new String[0]);
    }

    @NonNull
    private File copyUriToTempFile(@NonNull Uri uri, @NonNull String fileName) throws IOException {
        File tempDir = new File(context.getCacheDir(), "temp_imports");
        if (!tempDir.exists()) {
            tempDir.mkdirs();
        }

        File tempFile = new File(tempDir, "temp_" + System.currentTimeMillis() + "_" + sanitizeTempFileName(fileName));

        try (InputStream inputStream = context.getContentResolver().openInputStream(uri);
             FileOutputStream outputStream = new FileOutputStream(tempFile)) {
            if (inputStream == null) {
                throw new IOException("Cannot open input stream for URI: " + uri);
            }
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
        }

        return tempFile;
    }

    @NonNull
    private String sanitizeTempFileName(@NonNull String fileName) {
        String safeName = fileName
                .replaceAll("[\\\\/:*?\"<>|]", "_")
                .replaceAll("\\s+", " ")
                .trim();
        return safeName.isEmpty() ? "selected_file" : safeName;
    }

    // ==================== Data Classes ====================

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
}
