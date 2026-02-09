package com.afriserve.smsmanager.ui.bulk;

import android.net.Uri;
import android.util.Log;
import android.widget.EditText;
import android.widget.Toast;

import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.ViewModelProvider;

import com.afriserve.smsmanager.data.csv.CsvFileHandler;
import com.afriserve.smsmanager.data.csv.CsvParsingResult;
import com.afriserve.smsmanager.data.csv.CsvHeaderExtractor;
import com.afriserve.smsmanager.data.parser.TemplateVariableExtractor;
import com.afriserve.smsmanager.data.templates.CsvTemplateService;
import com.afriserve.smsmanager.data.templates.TemplateEngine;
import com.afriserve.smsmanager.ui.sms.TemplateVariableDialog;

import java.util.List;

import javax.inject.Inject;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * Integration helper for CSV template functionality
 * Shows how to use the CSV template system in existing activities/fragments
 */
public class CsvTemplateIntegration {
    private static final String TAG = "CsvTemplateIntegration";
    
    @Inject
    CsvFileHandler csvFileHandler;
    
    @Inject
    CsvTemplateService csvTemplateService;
    
    @Inject
    TemplateEngine templateEngine;
    
    private final CompositeDisposable disposables = new CompositeDisposable();
    
    private FragmentActivity activity;
    private EditText messageEditText;
    private CsvParsingResult currentCsvResult;
    private TemplateVariableDialog variableDialog;
    
    public CsvTemplateIntegration(FragmentActivity activity, EditText messageEditText) {
        this.activity = activity;
        this.messageEditText = messageEditText;
        // Assuming dependency injection is set up
    }
    
    /**
     * Handle CSV file upload and extract template variables
     */
    public void handleCsvUpload(Uri csvUri, String fileName) {
        disposables.add(
            csvFileHandler.parseFile(csvUri, fileName)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(this::onCsvParsed, this::onCsvParseError)
        );
    }
    
    /**
     * Show template variables dialog
     */
    public void showTemplateVariablesDialog() {
        if (currentCsvResult == null) {
            Toast.makeText(activity, "Please upload a CSV file first", Toast.LENGTH_SHORT).show();
            return;
        }
        
        String currentTemplate = messageEditText.getText().toString();
        
        variableDialog = TemplateVariableDialog.newInstance(currentCsvResult, currentTemplate);
        variableDialog.setOnTemplateSelectedListener(new TemplateVariableDialog.OnTemplateSelectedListener() {
            @Override
            public void onTemplateSelected(String template) {
                messageEditText.setText(template);
            }
            
            @Override
            public void onVariableSelected(String variable) {
                insertVariableIntoMessage(variable);
            }
        });
        
        variableDialog.show(activity.getSupportFragmentManager(), "TemplateVariableDialog");
    }
    
    /**
     * Generate and show message preview
     */
    public void generateMessagePreview() {
        if (currentCsvResult == null) {
            Toast.makeText(activity, "Please upload a CSV file first", Toast.LENGTH_SHORT).show();
            return;
        }
        
        String template = messageEditText.getText().toString();
        if (template.trim().isEmpty()) {
            Toast.makeText(activity, "Please enter a message template", Toast.LENGTH_SHORT).show();
            return;
        }
        
        disposables.add(
            csvTemplateService.generatePreviewMessages(template, currentCsvResult, 5)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(this::onPreviewGenerated, this::onPreviewError)
        );
    }
    
    /**
     * Generate personalized messages for all recipients
     */
    public void generatePersonalizedMessages() {
        if (currentCsvResult == null) {
            Toast.makeText(activity, "Please upload a CSV file first", Toast.LENGTH_SHORT).show();
            return;
        }
        
        String template = messageEditText.getText().toString();
        if (template.trim().isEmpty()) {
            Toast.makeText(activity, "Please enter a message template", Toast.LENGTH_SHORT).show();
            return;
        }
        
        disposables.add(
            csvTemplateService.generatePersonalizedMessages(template, currentCsvResult)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(this::onPersonalizedMessagesGenerated, this::onPersonalizedMessagesError)
        );
    }
    
    /**
     * Validate current template
     */
    public void validateCurrentTemplate() {
        if (currentCsvResult == null) {
            Toast.makeText(activity, "Please upload a CSV file first", Toast.LENGTH_SHORT).show();
            return;
        }
        
        String template = messageEditText.getText().toString();
        
        disposables.add(
            csvTemplateService.validateTemplateWithCsv(template, currentCsvResult)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(this::onTemplateValidated, this::onTemplateValidationError)
        );
    }
    
    /**
     * Insert variable into message at cursor position
     */
    private void insertVariableIntoMessage(String variable) {
        int start = Math.max(messageEditText.getSelectionStart(), 0);
        int end = Math.max(messageEditText.getSelectionEnd(), 0);
        
        String currentText = messageEditText.getText().toString();
        String newText = currentText.substring(0, start) + variable + currentText.substring(end);
        
        messageEditText.setText(newText);
        messageEditText.setSelection(start + variable.length());
        
        // Update preview if dialog is showing
        if (variableDialog != null) {
            variableDialog.updateTemplate(newText);
        }
    }
    
    // Callback methods
    
    private void onCsvParsed(CsvParsingResult result) {
        currentCsvResult = result;
        
        // Extract template variables
        CsvHeaderExtractor.TemplateExtractionResult extractionResult = 
            csvFileHandler.extractTemplateVariables(result);
        
        String message = "CSV loaded successfully!\n" +
                        "Rows: " + result.getValidRecipients().size() + "\n" +
                        "Variables: " + extractionResult.variables.size();
        
        Toast.makeText(activity, message, Toast.LENGTH_LONG).show();
        
        // Optionally show variables dialog automatically
        showTemplateVariablesDialog();
    }
    
    private void onCsvParseError(Throwable error) {
        Log.e(TAG, "Error parsing CSV", error);
        Toast.makeText(activity, "Error parsing CSV: " + error.getMessage(), Toast.LENGTH_LONG).show();
    }
    
    private void onPreviewGenerated(List<TemplateVariableExtractor.MessagePreview> previews) {
        if (variableDialog != null) {
            // Update dialog with new preview
            variableDialog.updateTemplate(messageEditText.getText().toString());
        }
        
        String message = "Generated preview for " + previews.size() + " recipients";
        Toast.makeText(activity, message, Toast.LENGTH_SHORT).show();
    }
    
    private void onPreviewError(Throwable error) {
        Log.e(TAG, "Error generating preview", error);
        Toast.makeText(activity, "Error generating preview: " + error.getMessage(), Toast.LENGTH_LONG).show();
    }
    
    private void onPersonalizedMessagesGenerated(List<CsvTemplateService.PersonalizedMessage> messages) {
        String message = "Generated " + messages.size() + " personalized messages";
        Toast.makeText(activity, message, Toast.LENGTH_SHORT).show();
        
        // Here you can navigate to a preview screen or send the messages
        // For example: navigateToSendMessagesScreen(messages);
    }
    
    private void onPersonalizedMessagesError(Throwable error) {
        Log.e(TAG, "Error generating personalized messages", error);
        Toast.makeText(activity, "Error generating messages: " + error.getMessage(), Toast.LENGTH_LONG).show();
    }
    
    private void onTemplateValidated(CsvTemplateService.TemplateValidationResult result) {
        if (result.isValid) {
            Toast.makeText(activity, "Template is valid!", Toast.LENGTH_SHORT).show();
        } else {
            String message = "Template issues found:\n" + result.message;
            Toast.makeText(activity, message, Toast.LENGTH_LONG).show();
        }
    }
    
    private void onTemplateValidationError(Throwable error) {
        Log.e(TAG, "Error validating template", error);
        Toast.makeText(activity, "Error validating template: " + error.getMessage(), Toast.LENGTH_LONG).show();
    }
    
    /**
     * Clean up resources
     */
    public void cleanup() {
        disposables.clear();
    }
    
    /**
     * Get current CSV result
     */
    public CsvParsingResult getCurrentCsvResult() {
        return currentCsvResult;
    }
    
    /**
     * Check if CSV is loaded
     */
    public boolean isCsvLoaded() {
        return currentCsvResult != null;
    }
}
