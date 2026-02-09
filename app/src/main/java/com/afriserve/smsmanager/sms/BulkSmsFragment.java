package com.afriserve.smsmanager.ui.sms;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.TextView;
import android.widget.Toast;
import android.text.TextUtils;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.afriserve.smsmanager.BulkSmsViewModel;
import com.afriserve.smsmanager.R;
import com.afriserve.smsmanager.models.RecipientComplianceResult;
import com.afriserve.smsmanager.models.Recipient;
import com.afriserve.smsmanager.data.parser.ExcelParser;
import com.afriserve.smsmanager.data.parser.TemplateVariableExtractor;
import com.afriserve.smsmanager.sms.DefaultSmsAppManager;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.List;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class BulkSmsFragment extends Fragment {

    private BulkSmsViewModel viewModel;
    private ActivityResultLauncher<String[]> filePickerLauncher;

    // Basic view references
    private Button btnSelectFile;
    private MaterialButton btnSendBulk, btnAddRecipient, btnSaveTemplate, btnTemplateLibrary;
    private TextInputEditText inputTemplate;
    private TextInputLayout inputTemplateLayout;
    private RadioButton radioSim1, radioSim2;
    private ProgressBar progressBar, progressFileLoading, progressSending;
    private TextView txtStatus, txtSentCount, txtFailedCount, txtRecipientCount, txtFileName;
    private TextView txtProgress, txtQueuedCount, txtDeliveryStatus, txtQueueStatus, txtSessionInfo, txtSendSpeed;

    // Advanced UI references
    private MaterialCardView cardTemplateVariables, cardMessagePreview;
    private RecyclerView recyclerViewTemplateVariables, recyclerViewMessagePreview;
    private TextView txtVariableCount, txtDetectedColumns;
    
    // Advanced control buttons
    private MaterialButton btnClearAll, btnRunQueue, btnClearExhausted, btnPauseResume, btnStopSending;
    private Button btnSpeed300, btnSpeed600, btnSpeed1000;

    // Adapters
    private TemplateVariableAdapter variableAdapter;
    private MessagePreviewAdapter previewAdapter;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        filePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.OpenDocument(),
                uri -> {
                    if (uri != null) {
                        viewModel.importRecipientsFromCsv(uri);
                    }
                });
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_sms_campaign, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        viewModel = new ViewModelProvider(this).get(BulkSmsViewModel.class);

        initViews(view);
        setupRecyclerViews();
        setupClickListeners();
        observeViewModel();
        setupBackNavigation();
    }

    private void initViews(View view) {
        // Basic views
        btnSelectFile = view.findViewById(R.id.btnSelectFile);
        btnSendBulk = view.findViewById(R.id.btnSendBulk);
        btnAddRecipient = view.findViewById(R.id.btnAddRecipient);
        btnSaveTemplate = view.findViewById(R.id.btnSaveTemplate);
        btnTemplateLibrary = view.findViewById(R.id.btnTemplateLibrary);
        inputTemplate = view.findViewById(R.id.inputTemplate);
        inputTemplateLayout = view.findViewById(R.id.inputTemplateLayout);
        radioSim1 = view.findViewById(R.id.radioSim1);
        radioSim2 = view.findViewById(R.id.radioSim2);
        progressBar = view.findViewById(R.id.progressBar);
        progressFileLoading = view.findViewById(R.id.progressFileLoading);
        progressSending = view.findViewById(R.id.progressSending);
        txtStatus = view.findViewById(R.id.txtStatus);
        txtSentCount = view.findViewById(R.id.txtSentCount);
        txtFailedCount = view.findViewById(R.id.txtFailedCount);
        txtRecipientCount = view.findViewById(R.id.txtRecipientCount);
        txtFileName = view.findViewById(R.id.txtFileName);

        // Advanced status views
        txtProgress = view.findViewById(R.id.txtProgress);
        txtQueuedCount = view.findViewById(R.id.txtQueuedCount);
        txtDeliveryStatus = view.findViewById(R.id.txtDeliveryStatus);
        txtQueueStatus = view.findViewById(R.id.txtQueueStatus);
        txtSessionInfo = view.findViewById(R.id.txtSessionInfo);
        txtSendSpeed = view.findViewById(R.id.txtSendSpeed);

        // Advanced UI views
        cardTemplateVariables = view.findViewById(R.id.cardTemplateVariables);
        cardMessagePreview = view.findViewById(R.id.cardMessagePreview);
        recyclerViewTemplateVariables = view.findViewById(R.id.recyclerViewTemplateVariables);
        recyclerViewMessagePreview = view.findViewById(R.id.recyclerViewMessagePreview);
        txtVariableCount = view.findViewById(R.id.txtVariableCount);
        txtDetectedColumns = view.findViewById(R.id.txtDetectedColumns);
        
        // Advanced control buttons
        btnClearAll = view.findViewById(R.id.btnClearAll);
        btnRunQueue = view.findViewById(R.id.btnRunQueue);
        btnClearExhausted = view.findViewById(R.id.btnClearExhausted);
        btnPauseResume = view.findViewById(R.id.btnPauseResume);
        btnStopSending = view.findViewById(R.id.btnStopSending);
        btnSpeed300 = view.findViewById(R.id.btnSpeed300);
        btnSpeed600 = view.findViewById(R.id.btnSpeed600);
        btnSpeed1000 = view.findViewById(R.id.btnSpeed1000);
    }

    private void setupRecyclerViews() {
        // Setup template variables grid
        if (recyclerViewTemplateVariables != null) {
            recyclerViewTemplateVariables.setLayoutManager(new GridLayoutManager(requireContext(), 3));
            variableAdapter = new TemplateVariableAdapter(new ArrayList<>(), this::onVariableClick);
            recyclerViewTemplateVariables.setAdapter(variableAdapter);
        }

        // Setup message preview
        if (recyclerViewMessagePreview != null) {
            recyclerViewMessagePreview.setLayoutManager(new LinearLayoutManager(requireContext()));
            previewAdapter = new MessagePreviewAdapter(new ArrayList<>());
            recyclerViewMessagePreview.setAdapter(previewAdapter);
        }
    }

    private void setupClickListeners() {
        if (btnSelectFile != null) {
            btnSelectFile.setOnClickListener(v -> {
                // Open file picker for CSV/Excel files
                filePickerLauncher.launch(new String[] {
                    "text/csv",
                    "text/plain",
                    "application/vnd.ms-excel",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                });
            });
        }

        if (btnAddRecipient != null) {
            btnAddRecipient.setOnClickListener(v -> addRecipientManually());
        }

        if (btnSaveTemplate != null) {
            btnSaveTemplate.setOnClickListener(v -> saveTemplate());
        }

        if (btnTemplateLibrary != null) {
            btnTemplateLibrary.setOnClickListener(v -> openTemplateLibrary());
        }

        // Core action buttons
        if (btnSendBulk != null) {
            btnSendBulk.setOnClickListener(v -> proceedWithSending());
        }
        if (btnClearAll != null) {
            btnClearAll.setOnClickListener(v -> clearAll());
        }
        if (btnRunQueue != null) {
            btnRunQueue.setOnClickListener(v -> viewModel.retryFailedMessages());
        }
        if (btnClearExhausted != null) {
            btnClearExhausted.setOnClickListener(v -> viewModel.clearExhaustedRetries());
        }
        if (btnPauseResume != null) {
            btnPauseResume.setOnClickListener(v -> togglePauseResume());
        }
        if (btnStopSending != null) {
            btnStopSending.setOnClickListener(v -> viewModel.stopSending());
        }

        // Speed control click listeners
        if (btnSpeed300 != null) {
            btnSpeed300.setOnClickListener(v -> setSendSpeed(300));
        }
        if (btnSpeed600 != null) {
            btnSpeed600.setOnClickListener(v -> setSendSpeed(600));
        }
        if (btnSpeed1000 != null) {
            btnSpeed1000.setOnClickListener(v -> setSendSpeed(1000));
        }

        // Template text change listener
        if (inputTemplate != null) {
            inputTemplate.addTextChangedListener(new android.text.TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    viewModel.updateMessagePreview(s.toString());
                }

                @Override
                public void afterTextChanged(android.text.Editable s) {}
            });
        }

        if (inputTemplateLayout != null) {
            inputTemplateLayout.setEndIconOnClickListener(v -> copyTemplateToClipboard());
        }
    }

    private void observeViewModel() {
        // Use explicit Raw getters to avoid Java property access ambiguity
        viewModel.getIsLoadingRaw().observe(getViewLifecycleOwner(), isLoading -> {
            if (progressFileLoading != null) {
                progressFileLoading.setVisibility(isLoading ? View.VISIBLE : View.GONE);
            }
            if (btnSendBulk != null) {
                btnSendBulk.setEnabled(!isLoading);
            }
            if (btnSelectFile != null) {
                btnSelectFile.setEnabled(!isLoading);
            }
        });

        viewModel.getStatusRaw().observe(getViewLifecycleOwner(), status -> {
            if (status != null && txtStatus != null) {
                txtStatus.setText(status);
            }
        });

        viewModel.getError().observe(getViewLifecycleOwner(), error -> {
            if (error != null && isAdded()) {
                Toast.makeText(requireContext(), error, Toast.LENGTH_LONG).show();
                if (txtStatus != null) {
                    txtStatus.setText(error);
                }
            }
        });

        viewModel.getRecipientsRaw().observe(getViewLifecycleOwner(), list -> {
            if (list != null) {
                if (btnSelectFile != null) {
                    btnSelectFile.setText("Imported " + list.size() + " Contacts");
                }
                if (txtRecipientCount != null) {
                    txtRecipientCount.setText("Recipients: " + list.size());
                }
            }
        });

        viewModel.getSentCountRaw().observe(getViewLifecycleOwner(), count -> {
            if (txtSentCount != null)
                txtSentCount.setText(String.valueOf(count));
        });

        viewModel.getFailedCountRaw().observe(getViewLifecycleOwner(), count -> {
            if (txtFailedCount != null)
                txtFailedCount.setText(String.valueOf(count));
        });

        viewModel.getProgress().observe(getViewLifecycleOwner(), progress -> {
            int safeProgress = progress != null ? progress : 0;
            if (progressBar != null) {
                progressBar.setProgress(safeProgress);
            }
            if (txtProgress != null) {
                txtProgress.setText(getString(R.string.bulk_sms_progress_format, safeProgress));
            }
        });

        // Template variables and preview observers
        viewModel.getTemplateVariables().observe(getViewLifecycleOwner(), this::updateTemplateVariables);
        viewModel.getMessagePreviews().observe(getViewLifecycleOwner(), this::updateMessagePreviews);
        viewModel.getDetectedColumns().observe(getViewLifecycleOwner(), this::updateDetectedColumns);

        // Advanced feature observers
        viewModel.getIsPaused().observe(getViewLifecycleOwner(), this::updatePausedState);
        viewModel.getIsSending().observe(getViewLifecycleOwner(), this::updateSendingState);
        viewModel.getQueuedCount().observe(getViewLifecycleOwner(), this::updateQueuedCount);
        viewModel.getDeliveryStats().observe(getViewLifecycleOwner(), this::updateDeliveryStatus);
        viewModel.getQueueStats().observe(getViewLifecycleOwner(), this::updateQueueStatus);
        viewModel.getSendSpeed().observe(getViewLifecycleOwner(), this::updateSendSpeed);
        viewModel.getSelectedSimSlot().observe(getViewLifecycleOwner(), this::updateSimSlot);
        viewModel.getActiveSession().observe(getViewLifecycleOwner(), this::updateSessionInfo);
        viewModel.getShowResumePrompt().observe(getViewLifecycleOwner(), this::showResumePrompt);
        viewModel.getRateLimitStats().observe(getViewLifecycleOwner(), this::updateRateLimitStatus);
    }

    private void addRecipientManually() {
        // Create dialog for manual recipient addition
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_add_recipient, null);
        TextInputEditText nameInput = dialogView.findViewById(R.id.editName);
        TextInputEditText phoneInput = dialogView.findViewById(R.id.editPhone);
        TextInputEditText emailInput = dialogView.findViewById(R.id.editEmail);

        new MaterialAlertDialogBuilder(requireContext())
            .setTitle("Add Recipient Manually")
            .setView(dialogView)
            .setPositiveButton("Add", (dialog, which) -> {
                String name = nameInput.getText() != null ? nameInput.getText().toString().trim() : "";
                String phone = phoneInput.getText() != null ? phoneInput.getText().toString().trim() : "";
                String email = emailInput.getText() != null ? emailInput.getText().toString().trim() : "";

                if (phone.isEmpty()) {
                    Toast.makeText(requireContext(), "Phone number is required", Toast.LENGTH_SHORT).show();
                    return;
                }

                Recipient recipient = new Recipient(name.isEmpty() ? phone : name, phone);
                if (!email.isEmpty()) {
                    recipient.addVariable("email", email);
                }

                viewModel.addRecipient(recipient);
                Toast.makeText(requireContext(), "Recipient added", Toast.LENGTH_SHORT).show();
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void saveTemplate() {
        String template = inputTemplate != null ? inputTemplate.getText().toString().trim() : "";
        if (template.isEmpty()) {
            Toast.makeText(requireContext(), "Please enter a template first", Toast.LENGTH_SHORT).show();
            return;
        }

        // Create dialog for template name
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_save_template, null);
        TextInputEditText nameInput = dialogView.findViewById(R.id.editTemplateName);

        new MaterialAlertDialogBuilder(requireContext())
            .setTitle("Save Template")
            .setView(dialogView)
            .setPositiveButton("Save", (dialog, which) -> {
                String templateName = nameInput.getText() != null ? nameInput.getText().toString().trim() : "";
                if (templateName.isEmpty()) {
                    Toast.makeText(requireContext(), "Template name is required", Toast.LENGTH_SHORT).show();
                    return;
                }

                viewModel.saveTemplate(templateName, template);
                Toast.makeText(requireContext(), "Template saved successfully", Toast.LENGTH_SHORT).show();
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void openTemplateLibrary() {
        List<String> templates = viewModel.getSavedTemplates();
        if (templates.isEmpty()) {
            Toast.makeText(requireContext(), "No saved templates found", Toast.LENGTH_SHORT).show();
            return;
        }

        new MaterialAlertDialogBuilder(requireContext())
            .setTitle("Template Library")
            .setItems(templates.toArray(new String[0]), (dialog, which) -> {
                String selectedTemplate = templates.get(which);
                String templateContent = viewModel.getTemplateContent(selectedTemplate);
                if (inputTemplate != null) {
                    inputTemplate.setText(templateContent);
                }
                Toast.makeText(requireContext(), "Template loaded: " + selectedTemplate, Toast.LENGTH_SHORT).show();
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void copyTemplateToClipboard() {
        if (inputTemplate == null) return;
        String template = inputTemplate.getText() != null ? inputTemplate.getText().toString() : "";
        if (template.trim().isEmpty()) {
            Toast.makeText(requireContext(), "Template is empty", Toast.LENGTH_SHORT).show();
            return;
        }
        android.content.ClipboardManager clipboard =
            (android.content.ClipboardManager) requireContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE);
        android.content.ClipData clip = android.content.ClipData.newPlainText("Message Template", template);
        clipboard.setPrimaryClip(clip);
        Toast.makeText(requireContext(), "Template copied", Toast.LENGTH_SHORT).show();
    }

    private void onVariableClick(TemplateVariableExtractor.TemplateVariable variable) {
        if (inputTemplate != null) {
            String currentText = inputTemplate.getText().toString();
            int cursorPosition = inputTemplate.getSelectionStart();
            
            // Insert variable at cursor position
            String newText = currentText.substring(0, cursorPosition) + 
                            variable.variableName + 
                            currentText.substring(cursorPosition);
            
            inputTemplate.setText(newText);
            inputTemplate.setSelection(cursorPosition + variable.variableName.length());
        }
    }

    private void updateTemplateVariables(List<TemplateVariableExtractor.TemplateVariable> variables) {
        if (variables != null && !variables.isEmpty() && variableAdapter != null) {
            variableAdapter.updateVariables(variables);
            if (txtVariableCount != null) {
                txtVariableCount.setText("Available Variables: " + variables.size());
            }
            if (cardTemplateVariables != null) {
                cardTemplateVariables.setVisibility(View.VISIBLE);
            }
        } else {
            if (cardTemplateVariables != null) {
                cardTemplateVariables.setVisibility(View.GONE);
            }
        }
    }

    private void setupBackNavigation() {
        requireActivity().getOnBackPressedDispatcher().addCallback(
            getViewLifecycleOwner(),
            new OnBackPressedCallback(true) {
                @Override
                public void handleOnBackPressed() {
                    try {
                        NavController navController = Navigation.findNavController(
                            requireActivity(),
                            R.id.nav_host_fragment_container
                        );
                        if (!navController.popBackStack()) {
                            navController.navigate(R.id.nav_dashboard);
                        }
                    } catch (Exception e) {
                        requireActivity().finish();
                    }
                }
            }
        );
    }

    private void updateDetectedColumns(ExcelParser.ColumnMapping mapping) {
        if (txtDetectedColumns == null) return;

        boolean hasName = mapping != null && !TextUtils.isEmpty(mapping.name);
        boolean hasPhone = mapping != null && !TextUtils.isEmpty(mapping.phone);
        boolean hasAmount = mapping != null && !TextUtils.isEmpty(mapping.amount);

        if (!hasName && !hasPhone && !hasAmount) {
            txtDetectedColumns.setVisibility(View.GONE);
            return;
        }

        List<String> parts = new ArrayList<>();
        if (hasPhone) {
            parts.add("Phone: " + mapping.phone);
        }
        if (hasName) {
            parts.add("Name: " + mapping.name);
        }
        if (hasAmount) {
            parts.add("Amount: " + mapping.amount);
        }

        String summary = getString(R.string.bulk_sms_detected_columns_prefix) + " " + TextUtils.join(" • ", parts);
        txtDetectedColumns.setText(summary);
        txtDetectedColumns.setVisibility(View.VISIBLE);
    }

    private void updateMessagePreviews(List<TemplateVariableExtractor.MessagePreview> previews) {
        if (previews != null && !previews.isEmpty() && previewAdapter != null) {
            previewAdapter.updatePreviews(previews);
            if (cardMessagePreview != null) {
                cardMessagePreview.setVisibility(View.VISIBLE);
            }
        } else {
            if (cardMessagePreview != null) {
                cardMessagePreview.setVisibility(View.GONE);
            }
        }
    }

    private boolean hasPermissions() {
        return ContextCompat.checkSelfPermission(requireContext(),
                android.Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestPermissions() {
        if (!ensureDefaultSmsApp()) {
            return;
        }
        requestPermissions(new String[] { android.Manifest.permission.SEND_SMS }, 101);
    }

    private boolean ensureDefaultSmsApp() {
        DefaultSmsAppManager manager = new DefaultSmsAppManager(requireContext());
        if (manager.isDefaultSmsApp()) {
            return true;
        }

        if (getActivity() instanceof AppCompatActivity) {
            manager.requestDefaultSmsAppStatus((AppCompatActivity) getActivity(),
                new DefaultSmsAppManager.DefaultSmsAppCallback() {
                    @Override
                    public void onAlreadyDefaultSmsApp() {
                    }

                    @Override
                    public void onDefaultSmsAppIntentReady(Intent intent) {
                        startActivity(intent);
                    }

                    @Override
                    public void onDefaultSmsAppSuccess() {
                    }

                    @Override
                    public void onDefaultSmsAppFailed() {
                        Toast.makeText(requireContext(),
                                "Unable to set default SMS app",
                                Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onDefaultSmsAppCancelled() {
                    }

                    @Override
                    public void onPermissionsRequired(String[] missingPermissions) {
                        Toast.makeText(requireContext(),
                                "Default SMS app requires additional permissions",
                                Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onNotSupported() {
                        Toast.makeText(requireContext(),
                                "Default SMS app is not supported on this device",
                                Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onUserDeclined() {
                    }

                    @Override
                    public void onShowMoreInfo() {
                    }

                    @Override
                    public void onError(String message) {
                        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
                    }
                });
        } else {
            Toast.makeText(requireContext(),
                    "Unable to request default SMS role",
                    Toast.LENGTH_SHORT).show();
        }
        return false;
    }

    // Advanced UI Methods

    private void clearAll() {
        new MaterialAlertDialogBuilder(requireContext())
            .setTitle("Clear All Data")
            .setMessage("This will clear all recipients and reset the campaign. Are you sure?")
            .setPositiveButton("Clear", (dialog, which) -> {
                viewModel.clearAllData();
                if (inputTemplate != null) {
                    inputTemplate.setText("");
                }
                Toast.makeText(requireContext(), "All data cleared", Toast.LENGTH_SHORT).show();
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void setSendSpeed(int speed) {
        viewModel.setSendSpeed(speed);
        updateSpeedButtons(speed);
        Toast.makeText(requireContext(), "Send speed set to " + speed + " SMS/min", Toast.LENGTH_SHORT).show();
    }

    private void updateSpeedButtons(int selectedSpeed) {
        if (btnSpeed300 != null) {
            btnSpeed300.setSelected(selectedSpeed == 300);
        }
        if (btnSpeed600 != null) {
            btnSpeed600.setSelected(selectedSpeed == 600);
        }
        if (btnSpeed1000 != null) {
            btnSpeed1000.setSelected(selectedSpeed == 1000);
        }
    }

    private void updatePauseResumeButton() {
        if (btnPauseResume == null) return;
        
        Boolean isPaused = viewModel.getIsPaused().getValue();
        if (isPaused != null && isPaused) {
            btnPauseResume.setText("Resume");
        } else {
            btnPauseResume.setText("Pause");
        }
    }

    // Advanced UI Update Methods

    private void updatePausedState(Boolean isPaused) {
        updatePauseResumeButton();
        if (progressSending != null) {
            progressSending.setVisibility(isPaused != null && !isPaused ? View.VISIBLE : View.GONE);
        }
    }

    private void updateSendingState(Boolean isSending) {
        if (btnSendBulk != null) {
            btnSendBulk.setEnabled(isSending == null || !isSending);
            btnSendBulk.setText(isSending != null && isSending ? "Sending..." : "Send Bulk SMS");
        }
    }

    private void updateQueuedCount(Integer count) {
        if (txtQueuedCount != null) {
            txtQueuedCount.setText(String.valueOf(count != null ? count : 0));
        }
    }

    private void updateDeliveryStatus(com.afriserve.smsmanager.data.tracking.EnhancedDeliveryTracker.DeliveryStatistics stats) {
        if (txtDeliveryStatus != null && stats != null) {
            String status = String.format("Delivered: %d | Pending: %d | Failed: %d", 
                stats.getDeliveredCount(), stats.getPendingCount(), stats.getFailedCount());
            txtDeliveryStatus.setText(status);
        }
    }

    private void updateQueueStatus(com.afriserve.smsmanager.data.queue.SmsQueueManager.QueueStatistics stats) {
        if (txtQueueStatus != null && stats != null) {
            String status = String.format("Queue: %d | Processing: %d | Retries: %d", 
                stats.getQueuedCount(), stats.getProcessingCount(), stats.getRetryCount());
            txtQueueStatus.setText(status);
        }
    }

    private void updateSendSpeed(Integer speed) {
        if (txtSendSpeed != null) {
            txtSendSpeed.setText(speed != null ? speed + " SMS/min" : "0 SMS/min");
        }
        updateSpeedButtons(speed != null ? speed : 0);
    }

    private void updateSimSlot(Integer simSlot) {
        if (radioSim1 != null && radioSim2 != null) {
            if (simSlot != null && simSlot == 0) {
                radioSim1.setChecked(true);
            } else {
                radioSim2.setChecked(true);
            }
        }
    }

    private void updateSessionInfo(com.afriserve.smsmanager.data.persistence.UploadPersistenceService.UploadSession session) {
        if (txtSessionInfo != null) {
            if (session != null) {
                String info = String.format("Session: %d recipients | %s", 
                    session.recipients.size(), session.processingStatus);
                txtSessionInfo.setText(info);
                txtSessionInfo.setVisibility(View.VISIBLE);
            } else {
                txtSessionInfo.setVisibility(View.GONE);
            }
        }
    }

    private void showResumePrompt(Boolean show) {
        if (show != null && show) {
            new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Resume Previous Session?")
                .setMessage("A previous upload session was found. Would you like to resume?")
                .setPositiveButton("Resume", (dialog, which) -> viewModel.resumeSession())
                .setNegativeButton("Discard", (dialog, which) -> viewModel.discardSession())
                .show();
        }
    }

    private void updateRateLimitStatus(com.afriserve.smsmanager.data.compliance.RateLimitManager.RateLimitStats stats) {
        if (stats != null && stats.isNearLimit()) {
            Toast.makeText(requireContext(), 
                "Warning: Approaching rate limit (" + stats.getUsagePercentage() + "%)", 
                Toast.LENGTH_LONG).show();
        }
    }

    // Compliance Methods

    private void proceedWithSending() {
        if (!hasPermissions()) {
            requestPermissions();
            return;
        }

        int simSlot = (radioSim1 != null && radioSim1.isChecked()) ? 0 : 1;
        viewModel.startBulkSend(
                inputTemplate.getText().toString(),
                simSlot);
    }

    private void showComplianceDialog(List<RecipientComplianceResult> results) {
        StringBuilder message = new StringBuilder("Compliance issues found:\n\n");
        int issueCount = 0;
        
        for (RecipientComplianceResult result : results) {
            if (!result.getComplianceResult().isCompliant()) {
                message.append("• ").append(result.getRecipient().getPhone())
                       .append(": ").append(result.getComplianceResult().getReason())
                       .append("\n");
                issueCount++;
                
                if (issueCount >= 5) {
                    message.append("... and ").append(results.size() - issueCount).append(" more issues");
                    break;
                }
            }
        }
        
        new MaterialAlertDialogBuilder(requireContext())
            .setTitle("Compliance Check")
            .setMessage(message.toString())
            .setPositiveButton("Send Anyway", (dialog, which) -> proceedWithSending())
            .setNegativeButton("Cancel", null)
            .show();
    }
    
    private void togglePauseResume() {
        Boolean isPaused = viewModel.getIsPaused().getValue();
        if (isPaused != null && isPaused) {
            viewModel.resumeSending();
        } else {
            viewModel.pauseSending();
        }
    }
}
