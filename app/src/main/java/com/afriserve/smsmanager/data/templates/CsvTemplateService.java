package com.afriserve.smsmanager.data.templates;

import android.util.Log;
import com.afriserve.smsmanager.data.csv.CsvFileHandler;
import com.afriserve.smsmanager.data.csv.CsvHeaderExtractor;
import com.afriserve.smsmanager.data.csv.CsvParsingResult;
import com.afriserve.smsmanager.data.parser.TemplateVariableExtractor;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;

import io.reactivex.rxjava3.core.Single;

/**
 * Service for integrating CSV uploads with template functionality
 * Handles CSV processing, variable extraction, and message personalization
 */
@Singleton
public class CsvTemplateService {
    private static final String TAG = "CsvTemplateService";
    
    private final CsvFileHandler csvFileHandler;
    private final TemplateEngine templateEngine;
    
    @Inject
    public CsvTemplateService(CsvFileHandler csvFileHandler, TemplateEngine templateEngine) {
        this.csvFileHandler = csvFileHandler;
        this.templateEngine = templateEngine;
    }
    
    /**
     * Process CSV file and extract template variables
     */
    public Single<CsvTemplateProcessingResult> processCsvForTemplates(CsvParsingResult csvResult) {
        return Single.fromCallable(() -> {
            try {
                // Extract template variables from CSV headers
                CsvHeaderExtractor.TemplateExtractionResult extractionResult = 
                    csvFileHandler.extractTemplateVariables(csvResult);
                
                Log.d(TAG, "Processed CSV with " + extractionResult.variables.size() + " variables");
                
                return new CsvTemplateProcessingResult(
                    csvResult,
                    extractionResult.variables,
                    extractionResult.availablePlaceholders,
                    extractionResult.headerCount
                );
                
            } catch (Exception e) {
                Log.e(TAG, "Error processing CSV for templates", e);
                throw new RuntimeException("Failed to process CSV: " + e.getMessage(), e);
            }
        });
    }
    
    /**
     * Generate personalized messages for all recipients
     */
    public Single<List<PersonalizedMessage>> generatePersonalizedMessages(
            String template, 
            CsvParsingResult csvResult) {
        return Single.fromCallable(() -> {
            List<PersonalizedMessage> messages = new ArrayList<>();
            
            if (template == null || template.trim().isEmpty()) {
                return messages;
            }
            
            // Validate template first
            CsvHeaderExtractor.TemplateValidationResult validation = 
                csvFileHandler.validateTemplate(template, csvResult);
            
            if (!validation.isValid) {
                Log.w(TAG, "Template validation failed: " + validation.message);
                // Still proceed but log the warning
            }
            
            // Generate personalized messages for each valid recipient
            for (com.afriserve.smsmanager.data.csv.CsvRecipient recipient : csvResult.getValidRecipients()) {
                if (recipient.getPhoneNumber() == null || recipient.getPhoneNumber().trim().isEmpty()) {
                    continue; // Skip recipients without phone numbers
                }
                
                // Process template with recipient data
                String personalizedMessage = templateEngine.processTemplate(template, recipient.getData());
                
                // Get recipient name for display
                String recipientName = extractRecipientName(recipient.getData());
                
                messages.add(new PersonalizedMessage(
                    recipient.getPhoneNumber(),
                    recipientName,
                    personalizedMessage,
                    recipient.getData()
                ));
            }
            
            Log.d(TAG, "Generated " + messages.size() + " personalized messages");
            return messages;
            
        });
    }
    
    /**
     * Generate preview messages for first few recipients
     */
    public Single<List<TemplateVariableExtractor.MessagePreview>> generatePreviewMessages(
            String template, 
            CsvParsingResult csvResult, 
            int maxCount) {
        return Single.fromCallable(() -> {
            return csvFileHandler.generateMessagePreview(template, csvResult, maxCount);
        });
    }
    
    /**
     * Validate template against available variables
     */
    public Single<TemplateValidationResult> validateTemplateWithCsv(
            String template, 
            CsvParsingResult csvResult) {
        return Single.fromCallable(() -> {
            CsvHeaderExtractor.TemplateValidationResult csvValidation = 
                csvFileHandler.validateTemplate(template, csvResult);
            
            // Also validate with template engine
            TemplateEngine.TemplateValidationResult engineValidation = 
                templateEngine.validateTemplate(template);
            
            // Combine results
            List<String> allErrors = new ArrayList<>();
            allErrors.addAll(csvValidation.missingVariables);
            allErrors.addAll(engineValidation.errors);
            
            List<String> allWarnings = new ArrayList<>();
            allWarnings.addAll(engineValidation.warnings);
            
            boolean isValid = csvValidation.isValid && engineValidation.isValid;
            
            return new TemplateValidationResult(
                isValid,
                csvValidation.message,
                csvValidation.validVariables,
                csvValidation.missingVariables,
                allErrors,
                allWarnings
            );
        });
    }
    
    /**
     * Extract recipient name from data
     */
    private String extractRecipientName(java.util.Map<String, String> data) {
        String[] nameFields = {
            "fullnames", "fullname", "name", "customername", "customer", "client", "borrower"
        };
        
        for (String field : nameFields) {
            String value = data.get(field);
            if (value != null && !value.trim().isEmpty()) {
                return value.trim();
            }
        }
        
        return "Unknown";
    }
    
    /**
     * Result of CSV template processing
     */
    public static class CsvTemplateProcessingResult {
        public final CsvParsingResult csvResult;
        public final List<TemplateVariableExtractor.TemplateVariable> variables;
        public final List<String> availablePlaceholders;
        public final int headerCount;
        
        public CsvTemplateProcessingResult(
                CsvParsingResult csvResult,
                List<TemplateVariableExtractor.TemplateVariable> variables,
                List<String> availablePlaceholders,
                int headerCount) {
            this.csvResult = csvResult;
            this.variables = variables;
            this.availablePlaceholders = availablePlaceholders;
            this.headerCount = headerCount;
        }
    }
    
    /**
     * Personalized message for a recipient
     */
    public static class PersonalizedMessage {
        public final String phoneNumber;
        public final String recipientName;
        public final String message;
        public final java.util.Map<String, String> originalData;
        
        public PersonalizedMessage(
                String phoneNumber,
                String recipientName,
                String message,
                java.util.Map<String, String> originalData) {
            this.phoneNumber = phoneNumber;
            this.recipientName = recipientName;
            this.message = message;
            this.originalData = originalData;
        }
    }
    
    /**
     * Enhanced template validation result
     */
    public static class TemplateValidationResult {
        public final boolean isValid;
        public final String message;
        public final List<String> validVariables;
        public final List<String> missingVariables;
        public final List<String> errors;
        public final List<String> warnings;
        
        public TemplateValidationResult(
                boolean isValid,
                String message,
                List<String> validVariables,
                List<String> missingVariables,
                List<String> errors,
                List<String> warnings) {
            this.isValid = isValid;
            this.message = message;
            this.validVariables = validVariables;
            this.missingVariables = missingVariables;
            this.errors = errors;
            this.warnings = warnings;
        }
    }
}
