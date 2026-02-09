package com.afriserve.smsmanager.data.parser;

import android.util.Log;
import com.afriserve.smsmanager.models.Recipient;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * CSV file parser utility
 * Handles CSV file parsing with various delimiters and formats
 */
public class CsvParser {
    private static final String TAG = "CsvParser";
    
    /**
     * Parse CSV file and return list of rows as maps
     * Enhanced to handle TSV and TXT files
     */
    public static List<Map<String, String>> parseCsvFile(String filePath) throws IOException {
        List<Map<String, String>> data = new ArrayList<>();
        
        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            String line;
            String[] headers = null;
            int lineNumber = 0;
            
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                line = line.trim();
                
                if (line.isEmpty()) {
                    continue; // Skip empty lines
                }
                
                // Detect delimiter and parse line
                String[] values = parseDelimitedLine(line);
                
                if (lineNumber == 1) {
                    // First line contains headers
                    headers = values;
                    continue;
                }
                
                if (headers == null) {
                    throw new IOException("File has no headers");
                }
                
                // Create row map
                Map<String, String> rowMap = new HashMap<>();
                for (int i = 0; i < headers.length && i < values.length; i++) {
                    String header = headers[i].trim();
                    String value = values[i].trim();
                    rowMap.put(header, value);
                }
                
                data.add(rowMap);
            }
            
            Log.d(TAG, "Parsed " + data.size() + " rows from file");
            return data;
            
        } catch (Exception e) {
            Log.e(TAG, "File parsing error", e);
            throw new IOException("Failed to parse file: " + e.getMessage(), e);
        }
    }
    
    /**
     * Parse line with automatic delimiter detection
     * Supports CSV (comma), TSV (tab), and other delimiters
     */
    private static String[] parseDelimitedLine(String line) {
        // Detect delimiter
        if (line.contains("\t")) {
            return parseTsvLine(line);
        } else if (line.contains(",")) {
            return parseCsvLine(line);
        } else if (line.contains(";")) {
            return parseSemicolonLine(line);
        } else if (line.contains("|")) {
            return parsePipeLine(line);
        } else {
            // Default to treating the whole line as one value
            return new String[]{line};
        }
    }
    
    /**
     * Parse TSV (tab-separated values) line
     */
    private static String[] parseTsvLine(String line) {
        return line.split("\t", -1); // -1 to keep empty strings
    }
    
    /**
     * Parse semicolon-separated line
     */
    private static String[] parseSemicolonLine(String line) {
        return line.split(";", -1);
    }
    
    /**
     * Parse pipe-separated line
     */
    private static String[] parsePipeLine(String line) {
        return line.split("\\|", -1);
    }
    
    /**
     * Parse CSV line handling quoted values and commas
     */
    private static String[] parseCsvLine(String line) {
        List<String> values = new ArrayList<>();
        StringBuilder currentValue = new StringBuilder();
        boolean inQuotes = false;
        
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            
            if (c == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    // Escaped quote
                    currentValue.append('"');
                    i++; // Skip next quote
                } else {
                    // Toggle quote state
                    inQuotes = !inQuotes;
                }
            } else if (c == ',' && !inQuotes) {
                // End of value
                values.add(currentValue.toString());
                currentValue = new StringBuilder();
            } else {
                currentValue.append(c);
            }
        }
        
        // Add last value
        values.add(currentValue.toString());
        
        return values.toArray(new String[0]);
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
        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            String firstLine = reader.readLine();
            if (firstLine == null) return false;
            
            // Check if line has commas
            return firstLine.contains(",");
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
}
