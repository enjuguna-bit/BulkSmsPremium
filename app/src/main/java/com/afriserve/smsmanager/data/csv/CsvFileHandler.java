package com.afriserve.smsmanager.data.csv;

import android.content.ContentResolver;
import android.net.Uri;
import android.util.Log;
import android.content.Context;

import javax.inject.Inject;
import dagger.hilt.android.qualifiers.ApplicationContext;

import java.io.IOException;
import java.io.InputStream;

import com.afriserve.smsmanager.data.parser.CsvParser;
import com.afriserve.smsmanager.data.parser.ExcelParser;
import com.afriserve.smsmanager.utils.FileUtils;
import com.afriserve.smsmanager.data.parser.TemplateVariableExtractor;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * CSV file parsing and handling service
 */
public class CsvFileHandler {
    private static final String TAG = "CsvFileHandler";

    private final Context context;
    private final ContentResolver contentResolver;

    @Inject
    public CsvFileHandler(@ApplicationContext Context context) {
        this.context = context;
        this.contentResolver = context.getContentResolver();
    }

    /**
     * Parse CSV file from URI
     */
    public io.reactivex.rxjava3.core.Single<CsvParsingResult> parseFile(Uri fileUri, String fileName) {
        return io.reactivex.rxjava3.core.Single.fromCallable(() -> {
            List<CsvHeader> headers = new ArrayList<>();
            List<CsvRecipient> recipients = new ArrayList<>();
            List<CsvRecipient> validRecipients = new ArrayList<>();
            List<String> invalidRecipients = new ArrayList<>();
            List<String> parseErrors = new ArrayList<>();

            try {
                String resolvedFileName = resolveFileName(fileUri, fileName);
                ExcelParser.ImportFileType fileType = ExcelParser.detectFileType(resolvedFileName);

                List<Map<String, String>> dataRows = new ArrayList<>();
                List<String> headerNames = new ArrayList<>();

                switch (fileType) {
                    case CSV:
                    case TXT:
                    case TSV:
                        try (InputStream inputStream = contentResolver.openInputStream(fileUri)) {
                            if (inputStream == null) {
                                throw new IOException("Unable to open input stream for uri: " + fileUri);
                            }
                            CsvParser.ParsedData parsed = CsvParser.parseCsvStreamWithHeaders(inputStream);
                            dataRows = parsed.rows;
                            headerNames = parsed.headers;
                        }
                        break;
                    case XLSX:
                    case XLS:
                    case XLSM:
                    case XLT:
                    case XLTX:
                    case XLTM:
                        try (InputStream inputStream = contentResolver.openInputStream(fileUri)) {
                            if (inputStream == null) {
                                throw new IOException("Unable to open input stream for uri: " + fileUri);
                            }
                            ExcelParser.ParsedSheet parsed = ExcelParser.parseExcelStreamWithHeaders(inputStream);
                            dataRows = parsed.rows;
                            headerNames = parsed.headers;
                        }
                        break;
                    default:
                        throw new IOException("Unsupported file type: " + resolvedFileName +
                            ". Please use CSV, Excel (.xlsx, .xls, .xlsm), or text files (.txt, .tsv).");
                }

                if ((headerNames == null || headerNames.isEmpty()) && !dataRows.isEmpty()) {
                    headerNames = new ArrayList<>(dataRows.get(0).keySet());
                }

                for (int i = 0; i < headerNames.size(); i++) {
                    headers.add(new CsvHeader(i, headerNames.get(i).trim()));
                }

                // Detect phone column
                CsvDetectionResult detectionResult = detectPhoneColumns(headers);

                // Process data rows
                int rowIndex = 0;
                for (Map<String, String> row : dataRows) {
                    rowIndex++;
                    if (row == null || row.isEmpty()) {
                        continue;
                    }

                    Map<String, String> rowData = new HashMap<>();
                    for (String header : headerNames) {
                        String value = row.getOrDefault(header, "");
                        rowData.put(header, cleanValue(value));
                    }

                    if (isRowEmpty(rowData)) {
                        continue;
                    }

                    String phoneNumber = extractPhoneNumber(rowData, detectionResult);
                    CsvRecipient recipient = new CsvRecipient(phoneNumber, rowData);
                    recipients.add(recipient);

                    if (isValidPhoneNumber(phoneNumber)) {
                        validRecipients.add(recipient);
                    } else {
                        invalidRecipients.add((phoneNumber != null ? phoneNumber : "") + " - Invalid format (row " + rowIndex + ")");
                    }
                }

                return new CsvParsingResult(
                        recipients,
                        validRecipients,
                        invalidRecipients,
                        headers,
                        detectionResult,
                        parseErrors);

            } catch (Exception e) {
                Log.e(TAG, "Error parsing CSV file", e);
                parseErrors.add("Failed to parse file: " + e.getMessage());
                throw new IOException("Failed to parse CSV file: " + e.getMessage(), e);
            }
        });
    }

    /**
     * Detect phone columns in headers
     */
    private CsvDetectionResult detectPhoneColumns(List<CsvHeader> headers) {
        List<String> potentialPhoneColumns = new ArrayList<>();
        Integer detectedPhoneColumnIndex = null;
        String detectedPhoneColumnName = null;

        for (int i = 0; i < headers.size(); i++) {
            CsvHeader header = headers.get(i);
            if (CsvHeader.isPhoneColumn(header.getName())) {
                potentialPhoneColumns.add(header.getName());
                if (detectedPhoneColumnIndex == null) {
                    detectedPhoneColumnIndex = i;
                    detectedPhoneColumnName = header.getName();
                }
            }
        }

        return new CsvDetectionResult(
                headers,
                detectedPhoneColumnIndex,
                detectedPhoneColumnName,
                potentialPhoneColumns.size() > 1,
                potentialPhoneColumns,
                headers.size(),
                "");
    }

    /**
     * Extract phone number from row data
     */
    private String extractPhoneNumber(Map<String, String> rowData, CsvDetectionResult detectionResult) {
        if (detectionResult.getDetectedPhoneColumnName() != null) {
            return rowData.get(detectionResult.getDetectedPhoneColumnName());
        }

        // Fallback: try to find any phone-like value
        for (Map.Entry<String, String> entry : rowData.entrySet()) {
            if (CsvHeader.isPhoneColumn(entry.getKey()) && isValidPhoneNumber(entry.getValue())) {
                return entry.getValue();
            }
        }

        return "";
    }

    /**
     * Simple phone number validation
     */
    private boolean isValidPhoneNumber(String phoneNumber) {
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

    private String resolveFileName(Uri fileUri, String fileName) {
        if (fileName != null && !fileName.trim().isEmpty() && fileName.contains(".")) {
            return fileName;
        }
        return FileUtils.getFileNameFromUri(context, fileUri);
    }

    private String cleanValue(String value) {
        if (value == null) return "";
        return value
            .replace("\uFEFF", "")
            .replaceAll("[\\u200B\\u00A0]", "")
            .trim();
    }

    private boolean isRowEmpty(Map<String, String> rowData) {
        for (String value : rowData.values()) {
            if (value != null && !value.trim().isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Extract template variables from CSV parsing result
     */
    public CsvHeaderExtractor.TemplateExtractionResult extractTemplateVariables(CsvParsingResult csvResult) {
        return CsvHeaderExtractor.extractTemplateVariables(csvResult);
    }

    /**
     * Generate message preview using template and CSV data
     */
    public List<TemplateVariableExtractor.MessagePreview> generateMessagePreview(
            String template,
            CsvParsingResult csvResult,
            int maxPreviewCount) {
        return CsvHeaderExtractor.generateMessagePreview(
                template,
                csvResult.getRecipients(),
                maxPreviewCount);
    }

    /**
     * Validate template against available CSV variables
     */
    public CsvHeaderExtractor.TemplateValidationResult validateTemplate(
            String template,
            CsvParsingResult csvResult) {
        CsvHeaderExtractor.TemplateExtractionResult extractionResult = extractTemplateVariables(csvResult);
        return CsvHeaderExtractor.validateTemplate(template, extractionResult.availablePlaceholders);
    }
}
