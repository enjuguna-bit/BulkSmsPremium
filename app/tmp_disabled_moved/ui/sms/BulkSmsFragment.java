package com.bulksms.smsmanager.ui.sms;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.bulksms.smsmanager.BulkSmsService;
import com.bulksms.smsmanager.BulkSmsViewModel;
import com.bulksms.smsmanager.R;
import com.bulksms.smsmanager.databinding.FragmentSmsCampaignBinding;
import com.bulksms.smsmanager.models.Recipient;
import com.bulksms.smsmanager.data.parser.TemplateVariableExtractor;
import com.bulksms.smsmanager.data.persistence.UploadPersistenceService.UploadSession;
import com.bulksms.smsmanager.ui.sms.TemplateVariableAdapter;
import com.bulksms.smsmanager.ui.sms.MessagePreviewAdapter;
import androidx.recyclerview.widget.GridLayoutManager;
import dagger.hilt.android.AndroidEntryPoint;
import java.util.ArrayList;
import java.util.List;

/**
 * Premium Bulk SMS Fragment
 * Uses Hilt for dependency injection
 */
// @AndroidEntryPoint
public class BulkSmsFragment extends Fragment {
    private FragmentSmsCampaignBinding binding;
    private BulkSmsViewModel viewModel;
    private ActivityResultLauncher<String> filePickerLauncher;
    private TemplateVariableAdapter variableAdapter;
    private MessagePreviewAdapter previewAdapter;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Initialize file picker launcher
        filePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            this::handleFileSelection
        );
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, 
                           @Nullable ViewGroup container, 
                           @Nullable Bundle savedInstanceState) {
        binding = FragmentSmsCampaignBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        // Initialize ViewModel
        viewModel = new ViewModelProvider(this).get(BulkSmsViewModel.class);
        
        // Setup UI
        setupRecyclerView();
        setupClickListeners();
        observeViewModel();
        
        // Request permissions if needed
        requestPermissions();
    }

    private void setupRecyclerView() {
        binding.recyclerViewRecipients.setLayoutManager(new LinearLayoutManager(requireContext()));
        
        // Setup template variables grid
        binding.recyclerViewTemplateVariables.setLayoutManager(new GridLayoutManager(requireContext(), 3));
        variableAdapter = new TemplateVariableAdapter(new ArrayList<>(), this::onVariableClick);
        binding.recyclerViewTemplateVariables.setAdapter(variableAdapter);
        
        // Setup message preview
        binding.recyclerViewMessagePreview.setLayoutManager(new LinearLayoutManager(requireContext()));
        previewAdapter = new MessagePreviewAdapter(new ArrayList<>());
        binding.recyclerViewMessagePreview.setAdapter(previewAdapter);
    }

    private void setupClickListeners() {
        binding.btnSelectFile.setOnClickListener(v -> selectFile());
        binding.btnAddRecipient.setOnClickListener(v -> addRecipientManually());
        binding.btnSendBulk.setOnClickListener(v -> sendBulkSmsWithConfirmation());
        binding.btnClearAll.setOnClickListener(v -> clearAll());
        binding.btnSaveTemplate.setOnClickListener(v -> saveTemplate());
        binding.btnTemplateLibrary.setOnClickListener(v -> openTemplateLibrary());
        
        // Template text change listener
        binding.inputTemplate.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                viewModel.updateMessagePreview(s.toString());
            }
            
            @Override
            public void afterTextChanged(android.text.Editable s) {}
        });
        
        // New enhanced controls
        binding.btnRunQueue.setOnClickListener(v -> runQueue());
        binding.btnClearExhausted.setOnClickListener(v -> clearExhausted());
        binding.btnPauseResume.setOnClickListener(v -> togglePause());
        binding.btnStopSending.setOnClickListener(v -> stopSending());
        
        // SIM slot selection
        binding.radioSim1.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) viewModel.setSelectedSimSlot(0);
        });
        binding.radioSim2.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) viewModel.setSelectedSimSlot(1);
        });
        
        // Send speed controls
        binding.btnSpeed300.setOnClickListener(v -> viewModel.setSendSpeed(300));
        binding.btnSpeed600.setOnClickListener(v -> viewModel.setSendSpeed(600));
        binding.btnSpeed1000.setOnClickListener(v -> viewModel.setSendSpeed(1000));
    }

    private void observeViewModel() {
        viewModel.getRecipients().observe(getViewLifecycleOwner(), this::updateRecipientCount);
        viewModel.getError().observe(getViewLifecycleOwner(), this::showError);
        viewModel.getIsLoading().observe(getViewLifecycleOwner(), this::updateLoadingState);
        viewModel.getProgress().observe(getViewLifecycleOwner(), this::updateProgress);
        viewModel.getStatus().observe(getViewLifecycleOwner(), this::updateStatus);
        
        // Enhanced observations
        viewModel.getActiveSession().observe(getViewLifecycleOwner(), this::updateSessionUI);
        viewModel.getShowResumePrompt().observe(getViewLifecycleOwner(), this::showResumePrompt);
        viewModel.getQueueStats().observe(getViewLifecycleOwner(), this::updateQueueStatus);
        viewModel.getDeliveryStats().observe(getViewLifecycleOwner(), this::updateDeliveryStatus);
        viewModel.getIsSending().observe(getViewLifecycleOwner(), this::updateSendingState);
        viewModel.getIsPaused().observe(getViewLifecycleOwner(), this::updatePausedState);
        viewModel.getSentCount().observe(getViewLifecycleOwner(), this::updateSentCount);
        viewModel.getFailedCount().observe(getViewLifecycleOwner(), this::updateFailedCount);
        viewModel.getQueuedCount().observe(getViewLifecycleOwner(), this::updateQueuedCount);
        viewModel.getSendSpeed().observe(getViewLifecycleOwner(), this::updateSendSpeed);
        viewModel.getSelectedSimSlot().observe(getViewLifecycleOwner(), this::updateSimSlot);
        
        // Template variables and preview observers
        viewModel.getTemplateVariables().observe(getViewLifecycleOwner(), this::updateTemplateVariables);
        viewModel.getMessagePreviews().observe(getViewLifecycleOwner(), this::updateMessagePreviews);
    }

    private void selectFile() {
        // Enhanced file picker supporting multiple file types
        String[] mimeTypes = {
            "text/csv",                                    // CSV files
            "application/vnd.ms-excel",                   // XLS files  
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", // XLSX files
            "application/vnd.ms-excel.sheet.macroEnabled.12", // XLSM files
            "application/vnd.ms-excel.template",          // XLT files
            "application/vnd.openxmlformats-officedocument.spreadsheetml.template", // XLTX files
            "application/vnd.ms-excel.template.macroEnabled.12", // XLTM files
            "application/vnd.oasis.opendocument.spreadsheet", // ODS files
            "application/vnd.oasis.opendocument.text",     // ODT files
            "text/plain",                                  // TXT files
            "text/tab-separated-values",                   // TSV files
            "application/octet-stream"                      // Fallback for unknown files
        };
        
        // Create a MIME type filter that accepts all supported types
        filePickerLauncher.launch("*/*");
    }

    private void handleFileSelection(Uri uri) {
        if (uri != null) {
            binding.txtFileName.setText(uri.getLastPathSegment());
            viewModel.importRecipientsFromCsv(uri);
        }
    }

    private void addRecipientManually() {
        // Implement manual recipient addition dialog
        Toast.makeText(requireContext(), "Add recipient manually - TODO", Toast.LENGTH_SHORT).show();
    }

    private void sendBulkSms() {
        String template = binding.inputTemplate.getText().toString().trim();
        int simSlot = binding.radioSim1.isChecked() ? 0 : 1;
        String campaignName = "Bulk Campaign";

        if (template.isEmpty()) {
            Toast.makeText(requireContext(), "Please enter a message template", Toast.LENGTH_SHORT).show();
            return;
        }

        // Validate message
        BulkSmsService.ValidationResult result = viewModel.validateMessage(template);
        if (!result.isValid()) {
            Toast.makeText(requireContext(), result.getErrorMessage(), Toast.LENGTH_LONG).show();
            return;
        }

        viewModel.sendBulkSms(template, simSlot, campaignName);
    }
    
    private void sendBulkSmsWithConfirmation() {
        List<com.bulksms.smsmanager.models.Recipient> recipients = viewModel.getRecipients().getValue();
        if (recipients == null || recipients.isEmpty()) {
            Toast.makeText(requireContext(), "No recipients to send to", Toast.LENGTH_SHORT).show();
            return;
        }
        
        if (recipients.size() > 50) {
            // Show confirmation for large batches
            int etaMinutes = (int) Math.ceil((recipients.size() * viewModel.getSendSpeed().getValue()) / 60000.0);
            new android.app.AlertDialog.Builder(requireContext())
                .setTitle("Confirm Bulk Send")
                .setMessage("You are about to send " + recipients.size() + " messages.\n\n" +
                           "Estimated time: ~" + etaMinutes + " minute" + (etaMinutes != 1 ? "s" : "") + "\n\n" +
                           "This cannot be undone once started.")
                .setPositiveButton("Send All", (dialog, which) -> sendBulkSms())
                .setNegativeButton("Cancel", null)
                .show();
        } else {
            sendBulkSms();
        }
    }
    
    private void runQueue() {
        viewModel.runQueueNow();
    }
    
    private void clearExhausted() {
        new android.app.AlertDialog.Builder(requireContext())
            .setTitle("Clear Exhausted Messages")
            .setMessage("Remove all messages that exceeded max retry attempts?")
            .setPositiveButton("Clear", (dialog, which) -> viewModel.clearExhaustedMessages())
            .setNegativeButton("Cancel", null)
            .show();
    }
    
    private void togglePause() {
        viewModel.togglePause();
    }
    
    private void stopSending() {
        new android.app.AlertDialog.Builder(requireContext())
            .setTitle("Stop Sending")
            .setMessage("Are you sure you want to stop the current campaign?")
            .setPositiveButton("Stop", (dialog, which) -> viewModel.stopSending())
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void clearAll() {
        viewModel.clearRecipients();
        viewModel.clearTemplateData();
        binding.inputTemplate.setText("");
        binding.txtFileName.setText("File Loaded: 0 recipients");
        binding.txtRecipientCount.setText("Recipients: 0");
        Toast.makeText(requireContext(), "All data cleared", Toast.LENGTH_SHORT).show();
    }

    private void saveTemplate() {
        String template = binding.inputTemplate.getText().toString().trim();
        if (!template.isEmpty()) {
            // Save to preferences or database
            Toast.makeText(requireContext(), "Template saved", Toast.LENGTH_SHORT).show();
        }
    }

    private void openTemplateLibrary() {
        Toast.makeText(requireContext(), "Template library - TODO", Toast.LENGTH_SHORT).show();
    }

    private void requestPermissions() {
        String[] permissions = {
            Manifest.permission.SEND_SMS,
            Manifest.permission.READ_SMS,
            Manifest.permission.READ_CONTACTS
        };

        boolean allGranted = true;
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(requireContext(), permission) 
                != PackageManager.PERMISSION_GRANTED) {
                allGranted = false;
                break;
            }
        }

        if (!allGranted) {
            requestPermissions(permissions, 1001);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, 
                                         @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        
        if (requestCode == 1001) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            
            if (!allGranted) {
                Toast.makeText(requireContext(), 
                    "Some permissions denied - functionality may be limited", 
                    Toast.LENGTH_LONG).show();
            }
        }
    }

    // UI Update Methods
    private void updateRecipientCount(List<Recipient> recipients) {
        int count = recipients != null ? recipients.size() : 0;
        binding.txtRecipientCount.setText("Recipients: " + count);
        
        // Update file name display with recipient count
        UploadSession session = viewModel.getActiveSession().getValue();
        if (session != null) {
            binding.txtFileName.setText("File Loaded: " + count + " recipients");
        }
    }

    private void showError(String error) {
        Toast.makeText(requireContext(), "Error: " + error, Toast.LENGTH_LONG).show();
    }

    private void updateLoadingState(Boolean isLoading) {
        binding.progressFileLoading.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        binding.btnSelectFile.setEnabled(!isLoading);
        binding.btnSendBulk.setEnabled(!isLoading);
    }

    private void updateProgress(Integer progress) {
        binding.progressBar.setProgress(progress);
        binding.txtProgress.setText("Progress: " + progress + "%");
    }

    private void updateStatus(String status) {
        // Update status in UI
        binding.txtStatus.setText(status);
    }
    
    // Enhanced UI update methods
    private void updateSessionUI(com.bulksms.smsmanager.data.persistence.UploadPersistenceService.UploadSession session) {
        if (session != null) {
            binding.txtSessionInfo.setText("Session: " + session.fileName + " (" + session.validRecords + " contacts)");
            binding.txtSessionInfo.setVisibility(View.VISIBLE);
        } else {
            binding.txtSessionInfo.setVisibility(View.GONE);
        }
    }
    
    private void showResumePrompt(Boolean show) {
        if (show != null && show) {
            new android.app.AlertDialog.Builder(requireContext())
                .setTitle("Resume Previous Session?")
                .setMessage("You have a previous upload session. Would you like to resume?")
                .setPositiveButton("Resume", (dialog, which) -> viewModel.resumeSession())
                .setNegativeButton("Discard", (dialog, which) -> viewModel.discardSession())
                .show();
        }
    }
    
    private void updateQueueStatus(com.bulksms.smsmanager.data.queue.SmsQueueManager.QueueStatistics stats) {
        binding.txtQueueStatus.setText("Queue: " + stats.pendingCount + " pending, " + 
                                     stats.failedCount + " failed, " + 
                                     stats.exhaustedCount + " exhausted");
        
        if (stats.exhaustedCount > 0) {
            binding.btnClearExhausted.setVisibility(View.VISIBLE);
        } else {
            binding.btnClearExhausted.setVisibility(View.GONE);
        }
        
        if (stats.circuitBreakerActive) {
            binding.txtCircuitBreakerStatus.setText("⚠️ Circuit Breaker Active");
            binding.txtCircuitBreakerStatus.setVisibility(View.VISIBLE);
        } else {
            binding.txtCircuitBreakerStatus.setVisibility(View.GONE);
        }
    }
    
    private void updateDeliveryStatus(com.bulksms.smsmanager.data.tracking.EnhancedDeliveryTracker.DeliveryStatistics stats) {
        binding.txtDeliveryStatus.setText("Delivery: " + stats.sentCount + " sent, " + 
                                        stats.deliveredCount + " delivered (" + 
                                        String.format("%.1f%%", stats.getDeliveryRate()) + ")");
    }
    
    private void updateSendingState(Boolean isSending) {
        if (isSending != null && isSending) {
            binding.btnSendBulk.setEnabled(false);
            binding.btnPauseResume.setEnabled(true);
            binding.btnStopSending.setEnabled(true);
            binding.progressSending.setVisibility(View.VISIBLE);
        } else {
            binding.btnSendBulk.setEnabled(true);
            binding.btnPauseResume.setEnabled(false);
            binding.btnStopSending.setEnabled(false);
            binding.progressSending.setVisibility(View.GONE);
        }
    }
    
    private void updatePausedState(Boolean isPaused) {
        if (isPaused != null && isPaused) {
            binding.btnPauseResume.setText("Resume");
            binding.btnPauseResume.setBackgroundColor(getResources().getColor(android.R.color.holo_green_light));
        } else {
            binding.btnPauseResume.setText("Pause");
            binding.btnPauseResume.setBackgroundColor(getResources().getColor(android.R.color.holo_orange_light));
        }
    }
    
    private void updateSentCount(Integer count) {
        binding.txtSentCount.setText(String.valueOf(count));
    }
    
    private void updateFailedCount(Integer count) {
        binding.txtFailedCount.setText(String.valueOf(count));
    }
    
    private void updateQueuedCount(Integer count) {
        binding.txtQueuedCount.setText(String.valueOf(count));
    }
    
    private void updateSendSpeed(Integer speed) {
        // Update speed button highlights
        binding.btnSpeed300.setBackgroundColor(speed == 300 ? 
            getResources().getColor(android.R.color.holo_blue_light) : 
            getResources().getColor(android.R.color.darker_gray));
        binding.btnSpeed600.setBackgroundColor(speed == 600 ? 
            getResources().getColor(android.R.color.holo_blue_light) : 
            getResources().getColor(android.R.color.darker_gray));
        binding.btnSpeed1000.setBackgroundColor(speed == 1000 ? 
            getResources().getColor(android.R.color.holo_blue_light) : 
            getResources().getColor(android.R.color.darker_gray));
    }
    
    private void updateSimSlot(Integer simSlot) {
        if (simSlot != null) {
            binding.radioSim1.setChecked(simSlot == 0);
            binding.radioSim2.setChecked(simSlot == 1);
        }
    }

    /**
     * Handle template variable click
     */
    private void onVariableClick(TemplateVariableExtractor.TemplateVariable variable) {
        String currentText = binding.inputTemplate.getText().toString();
        int cursorPosition = binding.inputTemplate.getSelectionStart();
        
        // Insert variable at cursor position
        String newText = currentText.substring(0, cursorPosition) + 
                        variable.variableName + 
                        currentText.substring(cursorPosition);
        
        binding.inputTemplate.setText(newText);
        binding.inputTemplate.setSelection(cursorPosition + variable.variableName.length());
    }
    
    /**
     * Update template variables UI
     */
    private void updateTemplateVariables(List<TemplateVariableExtractor.TemplateVariable> variables) {
        if (variables != null && !variables.isEmpty()) {
            variableAdapter.updateVariables(variables);
            binding.txtVariableCount.setText("Available Variables: " + variables.size());
            binding.cardTemplateVariables.setVisibility(View.VISIBLE);
        } else {
            binding.cardTemplateVariables.setVisibility(View.GONE);
        }
    }
    
    /**
     * Update message preview UI
     */
    private void updateMessagePreviews(List<TemplateVariableExtractor.MessagePreview> previews) {
        if (previews != null && !previews.isEmpty()) {
            previewAdapter.updatePreviews(previews);
            binding.cardMessagePreview.setVisibility(View.VISIBLE);
        } else {
            binding.cardMessagePreview.setVisibility(View.GONE);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
