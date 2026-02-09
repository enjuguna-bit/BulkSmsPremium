package com.afriserve.smsmanager.data.parser;

import android.util.Log;

import com.opencsv.CSVParser;
import com.opencsv.CSVParserBuilder;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import com.opencsv.exceptions.CsvValidationException;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * CSV file parser utility
 * Handles CSV file parsing with various delimiters and formats
 */
public class CsvParser {
    private static final String TAG = "CsvParser";
    private static final int SAMPLE_SIZE = 64 * 1024;
    private static final char[] CANDIDATE_DELIMITERS = new char[] {',', ';', '\t', '|'};

    /**
     * Parse CSV file and return list of rows as maps
     * Enhanced to handle TSV and TXT files
     */
    public static List<Map<String, String>> parseCsvFile(String filePath) throws IOException {
        ParsedData parsed = parseCsvFileWithHeaders(filePath);
        return parsed.rows;
    }

    /**
     * Parse CSV file and return headers + rows
     */
    public static ParsedData parseCsvFileWithHeaders(String filePath) throws IOException {
        try (InputStream inputStream = new FileInputStream(filePath)) {
            return parseCsvStreamWithHeaders(inputStream);
        }
    }

    /**
     * Parse CSV input stream and return headers + rows
     */
    public static ParsedData parseCsvStreamWithHeaders(InputStream inputStream) throws IOException {
        if (inputStream == null) {
            throw new IOException("Input stream is null");
        }

        BufferedInputStream buffered = new BufferedInputStream(inputStream);
        buffered.mark(SAMPLE_SIZE);

        byte[] sampleBytes = new byte[SAMPLE_SIZE];
        int sampleLen = buffered.read(sampleBytes);

        if (sampleLen <= 0) {
            throw new IOException("File has no headers");
        }

        Charset charset = detectCharset(sampleBytes, sampleLen);
        String sample = new String(sampleBytes, 0, sampleLen, charset);
        char delimiter = detectDelimiterFromSample(sample);

        buffered.reset();
        int bomLength = detectBomLength(sampleBytes, sampleLen);
        if (bomLength > 0) {
            long skipped = buffered.skip(bomLength);
            if (skipped < bomLength) {
                Log.w(TAG, "Unable to skip full BOM length");
            }
        }

        CSVParser parser = new CSVParserBuilder()
            .withSeparator(delimiter)
            .build();

        try (CSVReader reader = new CSVReaderBuilder(new InputStreamReader(buffered, charset))
            .withCSVParser(parser)
            .build()) {

            String[] rawHeaders = reader.readNext();
            if (rawHeaders == null || rawHeaders.length == 0) {
                throw new IOException("File has no headers");
            }

            List<String> headers = normalizeHeaders(rawHeaders);

            List<Map<String, String>> data = new ArrayList<>();
            String[] row;
            while ((row = reader.readNext()) != null) {
                if (isBlankRow(row)) {
                    continue;
                }

                Map<String, String> rowMap = new LinkedHashMap<>();
                for (int i = 0; i < headers.size(); i++) {
                    String value = i < row.length ? row[i] : "";
                    rowMap.put(headers.get(i), cleanValue(value));
                }
                data.add(rowMap);
            }

            Log.d(TAG, "Parsed " + data.size() + " rows from file");
            return new ParsedData(headers, data);

        } catch (CsvValidationException e) {
            Log.e(TAG, "CSV parsing error", e);
            throw new IOException("Failed to parse file: " + e.getMessage(), e);
        }
    }

    /**
     * Parse CSV file with recipient mapping
     */
    public static ExcelParser.ParseResult parseCsvWithMapping(String filePath) throws IOException {
        List<Map<String, String>> data = parseCsvFile(filePath);
        return ExcelParser.parseWithSmartMapping(data);
    }

    /**
     * Convert data to CSV format
     */
    public static String toCsv(List<Map<String, String>> data) {
        if (data.isEmpty()) {
            return "";
        }

        StringBuilder csv = new StringBuilder();

        // Get headers from first row
        Map<String, String> firstRow = data.get(0);
        List<String> headers = new ArrayList<>(firstRow.keySet());

        // Write headers
        for (int i = 0; i < headers.size(); i++) {
            if (i > 0) csv.append(",");
            csv.append(escapeCsvValue(headers.get(i)));
        }
        csv.append("\n");

        // Write data rows
        for (Map<String, String> row : data) {
            for (int i = 0; i < headers.size(); i++) {
                if (i > 0) csv.append(",");
                String value = row.getOrDefault(headers.get(i), "");
                csv.append(escapeCsvValue(value));
            }
            csv.append("\n");
        }

        return csv.toString();
    }

    /**
     * Escape CSV value if needed
     */
    private static String escapeCsvValue(String value) {
        if (value == null) return "";

        // Check if value contains comma, quote, or newline
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            // Escape quotes and wrap in quotes
            value = value.replace("\"", "\"\"");
            return "\"" + value + "\"";
        }

        return value;
    }

    /**
     * Summarize column mapping for user feedback
     */
    public static String summarizeMapping(ExcelParser.ColumnMapping mapping) {
        if (mapping == null) return "No mapping available";

        StringBuilder summary = new StringBuilder();
        summary.append("Column Mapping:\n");

        if (mapping.name != null) {
            summary.append("• Name: ").append(mapping.name).append("\n");
        } else {
            summary.append("• Name: Not found\n");
        }

        if (mapping.phone != null) {
            summary.append("• Phone: ").append(mapping.phone).append("\n");
        } else {
            summary.append("• Phone: Not found\n");
        }

        if (mapping.amount != null) {
            summary.append("• Amount: ").append(mapping.amount).append("\n");
        } else {
            summary.append("• Amount: Not found\n");
        }

        return summary.toString();
    }

    /**
     * Auto-suggest column mapping based on headers
     */
    public static ExcelParser.ColumnMapping autoSuggestMapping(List<String> headers) {
        ExcelParser.ColumnMapping mapping = new ExcelParser.ColumnMapping();

        List<String> normalizedHeaders = new ArrayList<>();
        for (String header : headers) {
            normalizedHeaders.add(header.toLowerCase()
                .replaceAll("\\s+", "")
                .replaceAll("[^\\w]", ""));
        }

        // Find matches using aliases
        mapping.name = findColumnMatch(headers, normalizedHeaders,
            List.of("fullnames", "fullname", "customername", "name", "client", "customer"));

        mapping.phone = findColumnMatch(headers, normalizedHeaders,
            List.of("phonenumber", "phone", "mobilephone", "contact", "phoneno", "mobile", "telephone", "tel"));

        mapping.amount = findColumnMatch(headers, normalizedHeaders,
            List.of("arrearsamount", "amount", "balance", "loan", "cost", "arrears", "payment", "due"));

        return mapping;
    }

    /**
     * Find column match using aliases
     */
    private static String findColumnMatch(List<String> headers, List<String> normalizedHeaders, List<String> aliases) {
        for (String alias : aliases) {
            String normalizedAlias = alias.toLowerCase()
                .replaceAll("\\s+", "")
                .replaceAll("[^\\w]", "");
            int index = normalizedHeaders.indexOf(normalizedAlias);
            if (index != -1) {
                return headers.get(index);
            }
        }
        return null;
    }

    /**
     * Validate CSV format
     */
    public static boolean isValidCsvFormat(String filePath) {
        try {
            ParsedData parsed = parseCsvFileWithHeaders(filePath);
            return parsed.headers != null && !parsed.headers.isEmpty();
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Get CSV statistics
     */
    public static CsvStatistics getCsvStatistics(String filePath) throws IOException {
        List<Map<String, String>> data = parseCsvFile(filePath);

        CsvStatistics stats = new CsvStatistics();
        stats.totalRows = data.size();
        stats.totalColumns = data.isEmpty() ? 0 : data.get(0).size();

        if (!data.isEmpty()) {
            stats.headers = new ArrayList<>(data.get(0).keySet());

            // Count non-empty values per column
            for (String header : stats.headers) {
                int nonEmptyCount = 0;
                for (Map<String, String> row : data) {
                    String value = row.get(header);
                    if (value != null && !value.trim().isEmpty()) {
                        nonEmptyCount++;
                    }
                }
                stats.columnDataCount.put(header, nonEmptyCount);
            }
        }

        return stats;
    }

    private static boolean isBlankRow(String[] row) {
        if (row == null || row.length == 0) return true;
        for (String value : row) {
            if (value != null && !value.trim().isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private static String cleanValue(String value) {
        if (value == null) return "";
        String cleaned = value
            .replace("\uFEFF", "")
            .replaceAll("[\\u200B\\u00A0]", "")
            .trim();
        if (looksLikeScientific(cleaned)) {
            try {
                cleaned = new BigDecimal(cleaned).toPlainString();
            } catch (NumberFormatException ignored) {
            }
        }
        return cleaned;
    }

    private static boolean looksLikeScientific(String value) {
        if (value == null) return false;
        return value.matches("[-+]?\\d+(\\.\\d+)?[eE][-+]?\\d+");
    }

    private static List<String> normalizeHeaders(String[] rawHeaders) {
        List<String> headers = new ArrayList<>();
        Set<String> used = new HashSet<>();

        for (int i = 0; i < rawHeaders.length; i++) {
            String header = rawHeaders[i] != null ? rawHeaders[i].trim() : "";
            header = header.replace("\uFEFF", "");
            if (header.isEmpty()) {
                header = "Column" + (i + 1);
            }
            header = makeUniqueHeader(header, used);
            headers.add(header);
        }

        return headers;
    }

    private static String makeUniqueHeader(String header, Set<String> used) {
        String base = header;
        String key = base.toLowerCase(Locale.US);
        if (!used.contains(key)) {
            used.add(key);
            return base;
        }

        int index = 2;
        while (true) {
            String candidate = base + "_" + index;
            String candidateKey = candidate.toLowerCase(Locale.US);
            if (!used.contains(candidateKey)) {
                used.add(candidateKey);
                return candidate;
            }
            index++;
        }
    }

    private static Charset detectCharset(byte[] sample, int length) {
        if (length >= 3
            && (sample[0] & 0xFF) == 0xEF
            && (sample[1] & 0xFF) == 0xBB
            && (sample[2] & 0xFF) == 0xBF) {
            return StandardCharsets.UTF_8;
        }
        if (length >= 2) {
            int b0 = sample[0] & 0xFF;
            int b1 = sample[1] & 0xFF;
            if (b0 == 0xFF && b1 == 0xFE) return StandardCharsets.UTF_16LE;
            if (b0 == 0xFE && b1 == 0xFF) return StandardCharsets.UTF_16BE;
        }
        return StandardCharsets.UTF_8;
    }

    private static int detectBomLength(byte[] sample, int length) {
        if (length >= 3
            && (sample[0] & 0xFF) == 0xEF
            && (sample[1] & 0xFF) == 0xBB
            && (sample[2] & 0xFF) == 0xBF) {
            return 3;
        }
        if (length >= 2) {
            int b0 = sample[0] & 0xFF;
            int b1 = sample[1] & 0xFF;
            if (b0 == 0xFF && b1 == 0xFE) return 2;
            if (b0 == 0xFE && b1 == 0xFF) return 2;
        }
        return 0;
    }

    private static char detectDelimiterFromSample(String sample) {
        if (sample == null || sample.isEmpty()) {
            return ',';
        }

        int bestCount = -1;
        char bestDelimiter = ',';

        for (char delimiter : CANDIDATE_DELIMITERS) {
            int count = countDelimiter(sample, delimiter);
            if (count > bestCount) {
                bestCount = count;
                bestDelimiter = delimiter;
            }
        }

        return bestDelimiter;
    }

    private static int countDelimiter(String sample, char delimiter) {
        int count = 0;
        boolean inQuotes = false;

        for (int i = 0; i < sample.length(); i++) {
            char c = sample.charAt(i);
            if (c == '"') {
                if (inQuotes && i + 1 < sample.length() && sample.charAt(i + 1) == '"') {
                    i++; // Escaped quote
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (c == delimiter && !inQuotes) {
                count++;
            }
        }

        return count;
    }

    /**
     * CSV statistics data class
     */
    public static class CsvStatistics {
        public int totalRows;
        public int totalColumns;
        public List<String> headers = new ArrayList<>();
        public Map<String, Integer> columnDataCount = new HashMap<>();

        @Override
        public String toString() {
            return "CsvStatistics{" +
                   "totalRows=" + totalRows +
                   ", totalColumns=" + totalColumns +
                   ", headers=" + headers +
                   '}';
        }
    }

    /**
     * Parsed CSV data with headers
     */
    public static class ParsedData {
        public final List<String> headers;
        public final List<Map<String, String>> rows;

        public ParsedData(List<String> headers, List<Map<String, String>> rows) {
            this.headers = headers;
            this.rows = rows;
        }
    }
}
