package com.afriserve.smsmanager.data.parser;

import android.util.Log;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Extracts template variables from Excel/CSV data
 * Creates clickable variable buttons for template composition
 */
public class TemplateVariableExtractor {
    private static final String TAG = "TemplateVariableExtractor";
    
    // Common variable patterns and their display names
    private static final Map<String, String> VARIABLE_DISPLAY_NAMES = new HashMap<>();
    
    static {
        VARIABLE_DISPLAY_NAMES.put("fullnames", "Full Names");
        VARIABLE_DISPLAY_NAMES.put("name", "Name");
        VARIABLE_DISPLAY_NAMES.put("customername", "Customer Name");
        VARIABLE_DISPLAY_NAMES.put("client", "Client");
        VARIABLE_DISPLAY_NAMES.put("phonenumber", "Phone Number");
        VARIABLE_DISPLAY_NAMES.put("phone", "Phone");
        VARIABLE_DISPLAY_NAMES.put("mobile", "Mobile");
        VARIABLE_DISPLAY_NAMES.put("telephone", "Telephone");
        VARIABLE_DISPLAY_NAMES.put("arrears_amount", "Arrears Amount");
        VARIABLE_DISPLAY_NAMES.put("amount", "Amount");
        VARIABLE_DISPLAY_NAMES.put("balance", "Balance");
        VARIABLE_DISPLAY_NAMES.put("loan", "Loan");
        VARIABLE_DISPLAY_NAMES.put("loanamount", "Loan Amount");
        VARIABLE_DISPLAY_NAMES.put("interest", "Interest");
        VARIABLE_DISPLAY_NAMES.put("payment", "Payment");
        VARIABLE_DISPLAY_NAMES.put("due", "Due");
        VARIABLE_DISPLAY_NAMES.put("loanid", "Loan ID");
        VARIABLE_DISPLAY_NAMES.put("account", "Account");
        VARIABLE_DISPLAY_NAMES.put("accountnumber", "Account Number");
        VARIABLE_DISPLAY_NAMES.put("email", "Email");
        VARIABLE_DISPLAY_NAMES.put("address", "Address");
        VARIABLE_DISPLAY_NAMES.put("city", "City");
        VARIABLE_DISPLAY_NAMES.put("country", "Country");
        VARIABLE_DISPLAY_NAMES.put("date", "Date");
        VARIABLE_DISPLAY_NAMES.put("duedate", "Due Date");
        VARIABLE_DISPLAY_NAMES.put("status", "Status");
    }
    
    /**
     * Extract all unique column headers from parsed data
     */
    public static List<TemplateVariable> extractVariables(List<Map<String, String>> data) {
        List<TemplateVariable> variables = new ArrayList<>();
        
        if (data == null || data.isEmpty()) {
            Log.w(TAG, "No data available for variable extraction");
            return variables;
        }
        
        // Get all unique column headers
        Map<String, String> uniqueHeaders = new HashMap<>();
        
        for (Map<String, String> row : data) {
            for (String header : row.keySet()) {
                if (header != null && !header.trim().isEmpty()) {
                    String normalizedHeader = normalizeHeader(header);
                    if (!uniqueHeaders.containsKey(normalizedHeader)) {
                        uniqueHeaders.put(normalizedHeader, header);
                    }
                }
            }
        }
        
        // Create template variables
        for (Map.Entry<String, String> entry : uniqueHeaders.entrySet()) {
            String normalizedHeader = entry.getKey();
            String originalHeader = entry.getValue();
            
            String displayName = getDisplayName(normalizedHeader);
            String variableName = "{" + normalizedHeader + "}";
            
            // Check if this column has sample data
            String sampleValue = getSampleValue(data, originalHeader);
            
            variables.add(new TemplateVariable(
                variableName,
                displayName,
                originalHeader,
                sampleValue
            ));
        }
        
        Log.d(TAG, "Extracted " + variables.size() + " template variables");
        return variables;
    }
    
    /**
     * Normalize header names for consistent variable naming
     */
    private static String normalizeHeader(String header) {
        if (header == null) return "";
        
        return header.toLowerCase()
            .replaceAll("\\s+", "_")
            .replaceAll("[^\\w_]", "")
            .replaceAll("_+", "_")
            .replaceAll("^_|_$", "");
    }
    
    /**
     * Get display name for a variable
     */
    private static String getDisplayName(String normalizedHeader) {
        return VARIABLE_DISPLAY_NAMES.getOrDefault(normalizedHeader, 
            toTitleCase(normalizedHeader.replace("_", " ")));
    }
    
    /**
     * Convert to title case
     */
    private static String toTitleCase(String input) {
        if (input == null || input.isEmpty()) return "";
        
        StringBuilder result = new StringBuilder();
        boolean capitalizeNext = true;
        
        for (char c : input.toCharArray()) {
            if (Character.isWhitespace(c)) {
                capitalizeNext = true;
                result.append(c);
            } else if (capitalizeNext) {
                result.append(Character.toUpperCase(c));
                capitalizeNext = false;
            } else {
                result.append(Character.toLowerCase(c));
            }
        }
        
        return result.toString();
    }
    
    /**
     * Get sample value from the first non-empty row
     */
    private static String getSampleValue(List<Map<String, String>> data, String header) {
        for (Map<String, String> row : data) {
            String value = row.get(header);
            if (value != null && !value.trim().isEmpty()) {
                // Truncate long values
                if (value.length() > 50) {
                    value = value.substring(0, 47) + "...";
                }
                return value;
            }
        }
        return "";
    }
    
    /**
     * Generate message preview for first few recipients
     */
    public static List<MessagePreview> generatePreview(String template, List<Map<String, String>> data, int maxCount) {
        List<MessagePreview> previews = new ArrayList<>();
        
        if (template == null || template.isEmpty() || data == null || data.isEmpty()) {
            return previews;
        }
        
        int count = Math.min(maxCount, data.size());
        
        for (int i = 0; i < count; i++) {
            Map<String, String> row = data.get(i);
            String processedMessage = processTemplate(template, row);
            
            // Get recipient info
            String name = getRecipientName(row);
            String phone = getRecipientPhone(row);
            
            previews.add(new MessagePreview(name, phone, processedMessage));
        }
        
        return previews;
    }
    
    /**
     * Process template with variable replacement
     */
    private static String processTemplate(String template, Map<String, String> row) {
        String result = template;
        
        // Replace all variables with their values
        for (Map.Entry<String, String> entry : row.entrySet()) {
            String header = entry.getKey();
            String value = entry.getValue();
            
            if (value == null) value = "";
            
            // Create normalized variable name
            String normalizedHeader = normalizeHeader(header);
            String variableName = "{" + normalizedHeader + "}";
            
            result = result.replace(variableName, value);
        }
        
        return result;
    }
    
    /**
     * Get recipient name from row data
     */
    private static String getRecipientName(Map<String, String> row) {
        // Try common name fields
        String[] nameFields = {"FullNames", "Name", "CustomerName", "Customer", "Client"};
        
        for (String field : nameFields) {
            String value = row.get(field);
            if (value != null && !value.trim().isEmpty()) {
                return value.trim();
            }
        }
        
        return "Unknown";
    }
    
    /**
     * Get recipient phone from row data
     */
    private static String getRecipientPhone(Map<String, String> row) {
        // Try common phone fields
        String[] phoneFields = {"PhoneNumber", "Phone", "MobilePhone", "Mobile", "Telephone", "Tel"};
        
        for (String field : phoneFields) {
            String value = row.get(field);
            if (value != null && !value.trim().isEmpty()) {
                return value.trim();
            }
        }
        
        return "No Phone";
    }
    
    /**
     * Template variable data class
     */
    public static class TemplateVariable {
        public final String variableName;
        public final String displayName;
        public final String columnName;
        public final String sampleValue;
        // Backwards-compatible description field used by adapters
        public final String description;
        
        public TemplateVariable(String variableName, String displayName, String columnName, String sampleValue) {
            this.variableName = variableName;
            this.displayName = displayName;
            this.columnName = columnName;
            this.sampleValue = sampleValue;
            this.description = displayName; // legacy alias
        }
        
        @Override
        public String toString() {
            return displayName;
        }
    }
    
    /**
     * Message preview data class
     */
    public static class MessagePreview {
        public final String recipientName;
        public final String phoneNumber;
        public final String message;
        public final String processedMessage; // legacy name expected by adapters
        
        public MessagePreview(String recipientName, String phoneNumber, String message) {
            this.recipientName = recipientName;
            this.phoneNumber = phoneNumber;
            this.message = message;
            this.processedMessage = message;
        }
    }
}
