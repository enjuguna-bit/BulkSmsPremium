package com.afriserve.smsmanager.data.csv;

import android.util.Log;
import com.afriserve.smsmanager.data.parser.TemplateVariableExtractor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Enhanced CSV header extraction service for template variable generation
 * Converts CSV headers to selectable placeholders for message templates
 */
public class CsvHeaderExtractor {
    private static final String TAG = "CsvHeaderExtractor";
    
    // Name column patterns that should be auto-split
    private static final List<String> NAME_COLUMN_PATTERNS = Arrays.asList(
        "fullname", "full_name", "name", "names", "customername", "clientname",
        "borrower", "customer", "client", "beneficiary", "member"
    );
    
    /**
     * Extract headers from CSV parsing result and convert to template variables
     */
    public static TemplateExtractionResult extractTemplateVariables(CsvParsingResult csvResult) {
        return extractTemplateVariables(csvResult, true);
    }
    
    /**
     * Extract headers from CSV parsing result and convert to template variables
     * @param csvResult The parsed CSV result
     * @param autoSplitNames Whether to auto-split full name columns into first/second/third name
     */
    public static TemplateExtractionResult extractTemplateVariables(CsvParsingResult csvResult, boolean autoSplitNames) {
        if (csvResult == null || csvResult.getHeaders() == null) {
            Log.w(TAG, "No CSV data available for header extraction");
            return new TemplateExtractionResult(new ArrayList<>(), new ArrayList<>(), 0);
        }
        
        List<CsvHeader> headers = csvResult.getHeaders();
        List<TemplateVariableExtractor.TemplateVariable> variables = new ArrayList<>();
        List<String> availablePlaceholders = new ArrayList<>();
        Set<String> processedNameColumns = new HashSet<>();
        
        // First pass: identify name columns that need splitting
        List<String> nameColumnsToSplit = new ArrayList<>();
        if (autoSplitNames) {
            for (CsvHeader header : headers) {
                String headerName = header.getName();
                if (isNameColumn(headerName) && !processedNameColumns.contains(headerName.toLowerCase())) {
                    nameColumnsToSplit.add(headerName);
                    processedNameColumns.add(headerName.toLowerCase());
                }
            }
        }
        
        // Reset for second pass
        processedNameColumns.clear();
        
        // Convert each header to a template variable
        for (CsvHeader header : headers) {
            String headerName = header.getName();
            String placeholder = header.getPlaceholder();
            String displayName = createDisplayName(headerName);
            String sampleValue = getSampleValue(csvResult.getRecipients(), headerName);
            
            TemplateVariableExtractor.TemplateVariable variable = 
                new TemplateVariableExtractor.TemplateVariable(
                    placeholder,
                    displayName,
                    headerName,
                    sampleValue
                );
            
            variables.add(variable);
            availablePlaceholders.add(placeholder);
            
            // Auto-split name columns: add firstname, secondname, thirdname placeholders
            if (autoSplitNames && nameColumnsToSplit.contains(headerName) 
                && !processedNameColumns.contains(headerName.toLowerCase())) {
                
                processedNameColumns.add(headerName.toLowerCase());
                
                // Add split name placeholders with sample values
                SplitNameComponents splitComponents = getSplitNameSample(csvResult.getRecipients(), headerName);
                
                // First Name
                variables.add(new TemplateVariableExtractor.TemplateVariable(
                    "{firstname}",
                    "First Name",
                    "firstname",
                    splitComponents.firstName
                ));
                availablePlaceholders.add("{firstname}");
                
                // Second Name (middle name)
                variables.add(new TemplateVariableExtractor.TemplateVariable(
                    "{secondname}",
                    "Second Name",
                    "secondname",
                    splitComponents.secondName
                ));
                availablePlaceholders.add("{secondname}");
                
                // Third Name
                variables.add(new TemplateVariableExtractor.TemplateVariable(
                    "{thirdname}",
                    "Third Name",
                    "thirdname",
                    splitComponents.thirdName
                ));
                availablePlaceholders.add("{thirdname}");
                
                Log.d(TAG, "Auto-split name column '" + headerName + "' into {firstname}, {secondname}, {thirdname}");
            }
        }
        
        Log.d(TAG, "Extracted " + variables.size() + " template variables from " + headers.size() + " headers");
        if (autoSplitNames && !nameColumnsToSplit.isEmpty()) {
            Log.d(TAG, "Auto-split " + nameColumnsToSplit.size() + " name column(s): " + nameColumnsToSplit);
        }
        
        return new TemplateExtractionResult(variables, availablePlaceholders, headers.size());
    }
    
    /**
     * Check if a header name represents a full name column that should be split
     */
    private static boolean isNameColumn(String headerName) {
        if (headerName == null) return false;
        
        String normalized = headerName.toLowerCase()
            .replace("_", "")
            .replace(" ", "")
            .trim();
        
        for (String pattern : NAME_COLUMN_PATTERNS) {
            if (normalized.contains(pattern)) {
                // Make sure it's not a different column type
                if (normalized.contains("phone") || normalized.contains("email") ||
                    normalized.contains("address") || normalized.contains("amount") ||
                    normalized.contains("balance") || normalized.contains("loan")) {
                    continue;
                }
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Get sample split name values from the first few recipients
     */
    private static SplitNameComponents getSplitNameSample(List<CsvRecipient> recipients, String headerName) {
        SplitNameComponents components = new SplitNameComponents();
        
        if (recipients == null || recipients.isEmpty()) {
            return components;
        }
        
        // Look through first 5 recipients for a non-empty name value
        int searchLimit = Math.min(5, recipients.size());
        for (int i = 0; i < searchLimit; i++) {
            CsvRecipient recipient = recipients.get(i);
            if (recipient != null && recipient.getData() != null) {
                String fullName = recipient.getData().get(headerName);
                if (fullName != null && !fullName.trim().isEmpty()) {
                    NameSplitter.SplitName split = NameSplitter.split(fullName);
                    components.firstName = truncateSample(split.firstName);
                    components.secondName = truncateSample(split.secondName);
                    components.thirdName = truncateSample(split.thirdName);
                    return components;
                }
            }
        }
        
        return components;
    }
    
    /**
     * Container for split name sample components
     */
    private static class SplitNameComponents {
        String firstName = "";
        String secondName = "";
        String thirdName = "";
    }
    
    /**
     * Create display name from header name
     */
    private static String createDisplayName(String headerName) {
        if (headerName == null || headerName.trim().isEmpty()) {
            return "Unknown";
        }
        
        // Convert to title case and replace underscores with spaces
        String displayName = headerName.toLowerCase()
            .replaceAll("_", " ")
            .replaceAll("\\s+", " ")
            .trim();
        
        // Convert to title case
        StringBuilder result = new StringBuilder();
        boolean capitalizeNext = true;
        
        for (char c : displayName.toCharArray()) {
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
     * Get sample value for a header from the first few recipients
     */
    private static String getSampleValue(List<CsvRecipient> recipients, String headerName) {
        if (recipients == null || recipients.isEmpty()) {
            return "";
        }
        
        // Look through first 5 recipients for a non-empty value
        int searchLimit = Math.min(5, recipients.size());
        for (int i = 0; i < searchLimit; i++) {
            CsvRecipient recipient = recipients.get(i);
            if (recipient != null && recipient.getData() != null) {
                String value = recipient.getData().get(headerName);
                if (value != null && !value.trim().isEmpty()) {
                    return truncateSample(value);
                }
            }
        }
        
        return "";
    }
    
    /**
     * Truncate sample values for display
     */
    private static String truncateSample(String value) {
        if (value == null || value.trim().isEmpty()) {
            return "";
        }
        // Truncate long values
        if (value.length() > 30) {
            value = value.substring(0, 27) + "...";
        }
        return value;
    }
    
    /**
     * Generate message preview using template variables
     */
    public static List<TemplateVariableExtractor.MessagePreview> generateMessagePreview(
            String template, 
            List<CsvRecipient> recipients, 
            int maxPreviewCount) {
        
        List<TemplateVariableExtractor.MessagePreview> previews = new ArrayList<>();
        
        if (template == null || template.trim().isEmpty() || recipients == null || recipients.isEmpty()) {
            return previews;
        }
        
        int previewCount = Math.min(maxPreviewCount, recipients.size());
        
        for (int i = 0; i < previewCount; i++) {
            CsvRecipient recipient = recipients.get(i);
            if (recipient == null || recipient.getData() == null) continue;
            
            // Process template with recipient data
            String processedMessage = processTemplateWithRecipient(template, recipient.getData());
            
            // Get recipient info
            String recipientName = getRecipientName(recipient.getData());
            String phoneNumber = recipient.getPhoneNumber();
            
            previews.add(new TemplateVariableExtractor.MessagePreview(
                recipientName, 
                phoneNumber, 
                processedMessage
            ));
        }
        
        return previews;
    }
    
    /**
     * Process template with recipient data
     */
    private static String processTemplateWithRecipient(String template, Map<String, String> recipientData) {
        String result = template;
        
        // Replace all placeholders with actual values
        for (Map.Entry<String, String> entry : recipientData.entrySet()) {
            String headerName = entry.getKey();
            String value = entry.getValue();
            
            if (value == null) value = "";
            
            // Create placeholder from header name
            String placeholder = "{" + headerName.toLowerCase().replace(" ", "") + "}";
            
            result = result.replace(placeholder, value);
        }
        
        // Auto-split name support
        for (Map.Entry<String, String> entry : recipientData.entrySet()) {
            if (isNameColumn(entry.getKey())) {
                String fullName = entry.getValue();
                if (fullName != null && !fullName.trim().isEmpty()) {
                    NameSplitter.SplitName split = NameSplitter.split(fullName);
                    result = result.replace("{firstname}", split.getPart("firstname"));
                    result = result.replace("{secondname}", split.getPart("secondname"));
                    result = result.replace("{thirdname}", split.getPart("thirdname"));
                }
                break;
            }
        }
        
        return result;
    }
    
    /**
     * Get recipient name from data
     */
    private static String getRecipientName(Map<String, String> recipientData) {
        // Try common name fields
        String[] nameFields = {
            "fullnames", "fullname", "name", "customername", "customer", "client", "borrower"
        };
        
        for (String field : nameFields) {
            String value = recipientData.get(field);
            if (value != null && !value.trim().isEmpty()) {
                return value.trim();
            }
        }
        
        return "Unknown";
    }
    
    /**
     * Extract variables used in a template
     */
    public static List<String> extractVariablesFromTemplate(String template) {
        List<String> variables = new ArrayList<>();
        
        if (template == null || template.trim().isEmpty()) {
            return variables;
        }
        
        // Simple regex to find {variable} patterns
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\\{([^}]+)\\}");
        java.util.regex.Matcher matcher = pattern.matcher(template);
        
        while (matcher.find()) {
            String variable = matcher.group(1);
            if (!variables.contains(variable)) {
                variables.add(variable);
            }
        }
        
        return variables;
    }
    
    /**
     * Validate template against available variables
     */
    public static TemplateValidationResult validateTemplate(
            String template, 
            List<String> availablePlaceholders) {
        
        List<String> usedVariables = extractVariablesFromTemplate(template);
        List<String> missingVariables = new ArrayList<>();
        List<String> validVariables = new ArrayList<>();
        
        for (String variable : usedVariables) {
            String placeholder = "{" + variable + "}";
            if (availablePlaceholders.contains(placeholder)) {
                validVariables.add(variable);
            } else {
                missingVariables.add(variable);
            }
        }
        
        boolean isValid = missingVariables.isEmpty();
        String message = isValid ? 
            "Template is valid. Uses " + validVariables.size() + " variables." :
            "Template has " + missingVariables.size() + " missing variables: " + String.join(", ", missingVariables);
        
        return new TemplateValidationResult(isValid, message, validVariables, missingVariables);
    }
    
    /**
     * Template extraction result
     */
    public static class TemplateExtractionResult {
        public final List<TemplateVariableExtractor.TemplateVariable> variables;
        public final List<String> availablePlaceholders;
        public final int headerCount;
        
        public TemplateExtractionResult(
                List<TemplateVariableExtractor.TemplateVariable> variables,
                List<String> availablePlaceholders,
                int headerCount) {
            this.variables = variables;
            this.availablePlaceholders = availablePlaceholders;
            this.headerCount = headerCount;
        }
    }
    
    /**
     * Template validation result
     */
    public static class TemplateValidationResult {
        public final boolean isValid;
        public final String message;
        public final List<String> validVariables;
        public final List<String> missingVariables;
        
        public TemplateValidationResult(
                boolean isValid, 
                String message,
                List<String> validVariables,
                List<String> missingVariables) {
            this.isValid = isValid;
            this.message = message;
            this.validVariables = validVariables;
            this.missingVariables = missingVariables;
        }
    }
}
