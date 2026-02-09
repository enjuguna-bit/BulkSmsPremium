package com.afriserve.smsmanager.data.csv;

import android.util.Log;
import com.afriserve.smsmanager.data.parser.TemplateVariableExtractor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Enhanced CSV header extraction service for template variable generation
 * Converts CSV headers to selectable placeholders for message templates
 */
public class CsvHeaderExtractor {
    private static final String TAG = "CsvHeaderExtractor";
    
    /**
     * Extract headers from CSV parsing result and convert to template variables
     */
    public static TemplateExtractionResult extractTemplateVariables(CsvParsingResult csvResult) {
        if (csvResult == null || csvResult.getHeaders() == null) {
            Log.w(TAG, "No CSV data available for header extraction");
            return new TemplateExtractionResult(new ArrayList<>(), new ArrayList<>(), 0);
        }
        
        List<CsvHeader> headers = csvResult.getHeaders();
        List<TemplateVariableExtractor.TemplateVariable> variables = new ArrayList<>();
        List<String> availablePlaceholders = new ArrayList<>();
        
        // Convert each header to a template variable
        for (CsvHeader header : headers) {
            String placeholder = header.getPlaceholder();
            String displayName = createDisplayName(header.getName());
            String sampleValue = getSampleValue(csvResult.getRecipients(), header.getName());
            
            TemplateVariableExtractor.TemplateVariable variable = 
                new TemplateVariableExtractor.TemplateVariable(
                    placeholder,
                    displayName,
                    header.getName(),
                    sampleValue
                );
            
            variables.add(variable);
            availablePlaceholders.add(placeholder);
        }
        
        Log.d(TAG, "Extracted " + variables.size() + " template variables from " + headers.size() + " headers");
        
        return new TemplateExtractionResult(variables, availablePlaceholders, headers.size());
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
                    // Truncate long values
                    if (value.length() > 30) {
                        value = value.substring(0, 27) + "...";
                    }
                    return value;
                }
            }
        }
        
        return "";
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
