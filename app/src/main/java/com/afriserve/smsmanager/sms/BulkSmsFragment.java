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
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.Spinner;
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
import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.List;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class BulkSmsFragment extends Fragment {

    private BulkSmsViewModel viewModel;
    private Toast deliveryToast;
    private boolean arrearsDialogShown;
    private String arrearsColumnName;
    private ActivityResultLauncher<String[]> filePickerLauncher;
    private static final String[] IMPORT_FILE_MIME_TYPES = new String[] {
        "*/*",
        "text/csv",
        "text/comma-separated-values",
        "application/csv",
        "text/plain",
        "application/octet-stream",
        "application/vnd.ms-excel",
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        "application/vnd.ms-excel.sheet.binary.macroEnabled.12",
        "application/vnd.ms-excel.sheet.macroEnabled.12",
        "application/vnd.ms-excel.template.macroEnabled.12",
        "application/vnd.openxmlformats-officedocument.spreadsheetml.template",
        "application/vnd.oasis.opendocument.spreadsheet"
    };

    // Basic view references
    private Button btnSelectFile;
    private MaterialButton btnSendBulk, btnAddRecipient, btnSaveTemplate, btnTemplateLibrary;
    private TextInputEditText inputTemplate;
    private TextInputLayout inputTemplateLayout;
    private RadioButton radioSim1, radioSim2;
    private ProgressBar progressBar, progressFileLoading, progressSending;
    private TextView txtStatus, txtSentCount, txtFailedCount, txtRecipientCount, txtFileName;
    private TextView txtCampaignReadiness, txtCampaignSmsParts, txtCalculationPreview;
    private TextView txtProgress, txtQueuedCount, txtDeliveryStatus, txtQueueStatus, txtSessionInfo, txtSendSpeed;

    // Advanced UI references
    private MaterialCardView cardTemplateVariables, cardMessagePreview;
    private RecyclerView recyclerViewTemplateVariables, recyclerViewMessagePreview;
    private TextView txtVariableCount, txtDetectedColumns;
    private MaterialButton btnArrearsFilter;
    private Spinner spinnerCalculationFirst, spinnerCalculationOperator, spinnerCalculationSecond, spinnerCalculationThird;
    private CheckBox checkCalculationThird, checkCalculationMoney;
    private MaterialButton btnInsertCalculation;
    private EditText editCalculationFirstNumber, editCalculationSecondNumber, editCalculationThirdNumber;
    
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
                        arrearsDialogShown = false;
                        persistReadPermission(uri);
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
        txtCampaignReadiness = view.findViewById(R.id.txtCampaignReadiness);
        txtCampaignSmsParts = view.findViewById(R.id.txtCampaignSmsParts);

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
        btnArrearsFilter = view.findViewById(R.id.btnArrearsFilter);
        spinnerCalculationFirst = view.findViewById(R.id.spinnerCalculationFirst);
        spinnerCalculationOperator = view.findViewById(R.id.spinnerCalculationOperator);
        spinnerCalculationSecond = view.findViewById(R.id.spinnerCalculationSecond);
        spinnerCalculationThird = view.findViewById(R.id.spinnerCalculationThird);
        checkCalculationThird = view.findViewById(R.id.checkCalculationThird);
        checkCalculationMoney = view.findViewById(R.id.checkCalculationMoney);
        btnInsertCalculation = view.findViewById(R.id.btnInsertCalculation);
        editCalculationFirstNumber = view.findViewById(R.id.editCalculationFirstNumber);
        editCalculationSecondNumber = view.findViewById(R.id.editCalculationSecondNumber);
        editCalculationThirdNumber = view.findViewById(R.id.editCalculationThirdNumber);
        txtCalculationPreview = view.findViewById(R.id.txtCalculationPreview);
        
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
                filePickerLauncher.launch(IMPORT_FILE_MIME_TYPES);
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
        if (checkCalculationThird != null) {
            checkCalculationThird.setOnCheckedChangeListener((button, checked) -> {
                if (spinnerCalculationThird != null) {
                    spinnerCalculationThird.setVisibility(checked ? View.VISIBLE : View.GONE);
                }
                updateCalculationPreview();
            });
        }
        setupCalculationOperandListener(spinnerCalculationFirst, editCalculationFirstNumber);
        setupCalculationOperandListener(spinnerCalculationSecond, editCalculationSecondNumber);
        setupCalculationOperandListener(spinnerCalculationThird, editCalculationThirdNumber);
        if (spinnerCalculationOperator != null) {
            spinnerCalculationOperator.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
                @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                    updateCalculationPreview();
                }
                @Override public void onNothingSelected(android.widget.AdapterView<?> parent) {
                    updateCalculationPreview();
                }
            });
        }
        if (checkCalculationMoney != null) {
            checkCalculationMoney.setOnCheckedChangeListener((button, checked) -> updateCalculationPreview());
        }
        if (btnInsertCalculation != null) {
            btnInsertCalculation.setOnClickListener(v -> insertCalculation());
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
                    updateCampaignReadiness();
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

        viewModel.getDeliveredRecipient().observe(getViewLifecycleOwner(), recipientName -> {
            if (recipientName != null && !recipientName.trim().isEmpty() && isAdded()) {
                if (deliveryToast != null) {
                    deliveryToast.cancel();
                }
                deliveryToast = Toast.makeText(
                        requireContext(), "Delivered to " + recipientName, Toast.LENGTH_SHORT);
                deliveryToast.show();
                viewModel.clearDeliveredRecipient();
            }
        });

        viewModel.getRecipientsRaw().observe(getViewLifecycleOwner(), list -> {
            if (list != null) {
                int recipientCount = list.size();
                if (btnSelectFile != null) {
                    btnSelectFile.setText(recipientCount > 0
                            ? "Imported " + recipientCount + " contacts"
                            : "Import contacts from Excel / CSV");
                }
                if (txtRecipientCount != null) {
                    txtRecipientCount.setText("Recipients: " + recipientCount);
                }
                updateCampaignReadiness(recipientCount);
                if (txtFileName != null && recipientCount == 0) {
                    txtFileName.setText("No file loaded yet");
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

    private void persistReadPermission(Uri uri) {
        try {
            requireContext().getContentResolver().takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            );
        } catch (SecurityException | IllegalArgumentException e) {
            android.util.Log.w("BulkSmsFragment", "Read permission was not persistable for selected file", e);
        }
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
            updateCalculationFields(variables);
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

    private void updateCalculationFields(List<TemplateVariableExtractor.TemplateVariable> variables) {
        List<String> options = new ArrayList<>();
        if (variables != null) {
            for (TemplateVariableExtractor.TemplateVariable variable : variables) {
                String token = variable.variableName;
                String lower = token.toLowerCase(java.util.Locale.ROOT);
                if (lower.matches(".*(amount|arrears|balance|paid|payment|interest|due|loan|number|days|total).*")) {
                    options.add(variable.displayName + " " + token);
                }
            }
        }
        if (options.isEmpty()) return;
        options.add("Fixed number");
        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_item, options);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        if (spinnerCalculationFirst != null) spinnerCalculationFirst.setAdapter(adapter);
        if (spinnerCalculationSecond != null) spinnerCalculationSecond.setAdapter(adapter);
        if (spinnerCalculationThird != null) {
            ArrayAdapter<String> thirdAdapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_item, options);
            thirdAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            spinnerCalculationThird.setAdapter(thirdAdapter);
        }
    }

    private void setupCalculationOperandListener(Spinner spinner, EditText numberInput) {
        if (spinner == null || numberInput == null) return;
        spinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                Object value = parent.getItemAtPosition(position);
                boolean fixed = value != null && "Fixed number".equals(value.toString());
                numberInput.setVisibility(fixed ? View.VISIBLE : View.GONE);
                updateCalculationPreview();
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) {
                numberInput.setVisibility(View.GONE);
                updateCalculationPreview();
            }
        });
        numberInput.setOnFocusChangeListener((v, hasFocus) -> updateCalculationPreview());
        numberInput.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { updateCalculationPreview(); }
            @Override public void afterTextChanged(android.text.Editable s) { }
        });
    }

    private void updateCalculationPreview() {
        if (txtCalculationPreview == null) return;
        String first = resolveOperand(spinnerCalculationFirst, editCalculationFirstNumber);
        String second = resolveOperand(spinnerCalculationSecond, editCalculationSecondNumber);
        if (first.isEmpty() || second.isEmpty()) {
            txtCalculationPreview.setText("Preview: choose two fields or enter numbers");
            return;
        }
        String operator = spinnerCalculationOperator != null && spinnerCalculationOperator.getSelectedItem() != null
                ? spinnerCalculationOperator.getSelectedItem().toString() : "+";
        String expression = first + " " + operator + " " + second;
        if (checkCalculationThird != null && checkCalculationThird.isChecked()) {
            String third = resolveOperand(spinnerCalculationThird, editCalculationThirdNumber);
            if (!third.isEmpty()) expression += " " + operator + " " + third;
        }
        String token = checkCalculationMoney != null && checkCalculationMoney.isChecked()
                ? "{{money(" + expression + ")}}" : "{{" + expression + "}}";
        txtCalculationPreview.setText("Preview: " + token);
    }

    private void insertCalculation() {
        if (inputTemplate == null || spinnerCalculationFirst == null || spinnerCalculationSecond == null) return;
        String first = resolveOperand(spinnerCalculationFirst, editCalculationFirstNumber);
        String second = resolveOperand(spinnerCalculationSecond, editCalculationSecondNumber);
        if (first.isEmpty() || second.isEmpty()) return;
        String operator = spinnerCalculationOperator != null && spinnerCalculationOperator.getSelectedItem() != null
                ? spinnerCalculationOperator.getSelectedItem().toString() : "+";
        if ("×".equals(operator)) operator = "*";
        if ("÷".equals(operator)) operator = "/";
        String expression = first + " " + operator + " " + second;
        if (checkCalculationThird != null && checkCalculationThird.isChecked() && spinnerCalculationThird != null) {
            String third = resolveOperand(spinnerCalculationThird, editCalculationThirdNumber);
            if (!third.isEmpty()) expression += " " + operator + " " + third;
        }
        String insertion = (checkCalculationMoney != null && checkCalculationMoney.isChecked() ? "{{money(" : "{{")
                + expression + (checkCalculationMoney != null && checkCalculationMoney.isChecked() ? ")}}" : "}}");
        int cursor = Math.max(0, inputTemplate.getSelectionStart());
        String current = inputTemplate.getText() == null ? "" : inputTemplate.getText().toString();
        inputTemplate.setText(current.substring(0, Math.min(cursor, current.length())) + insertion
                + current.substring(Math.min(cursor, current.length())));
        inputTemplate.setSelection(Math.min(cursor, current.length()) + insertion.length());
    }

    private String extractToken(Object selected) {
        if (selected == null) return "";
        String value = selected.toString();
        int start = value.lastIndexOf('{');
        int end = value.lastIndexOf('}');
        return start >= 0 && end > start ? value.substring(start + 1, end) : "";
    }

    private String resolveOperand(Spinner spinner, EditText numberInput) {
        Object selected = spinner == null ? null : spinner.getSelectedItem();
        if (selected != null && "Fixed number".equals(selected.toString())) {
            return numberInput != null && numberInput.getText() != null ? numberInput.getText().toString().trim() : "";
        }
        return extractToken(selected);
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
        boolean hasArrearsDays = mapping != null && !TextUtils.isEmpty(mapping.arrearsDays);

        if (hasArrearsDays) {
            arrearsColumnName = mapping.arrearsDays;
            if (btnArrearsFilter != null) {
                btnArrearsFilter.setText("Choose arrears range (" + arrearsColumnName + ")");
                btnArrearsFilter.setVisibility(View.VISIBLE);
                btnArrearsFilter.setOnClickListener(v -> showArrearsBucketDialog(arrearsColumnName));
            }
            if (!arrearsDialogShown && isAdded()) {
                arrearsDialogShown = true;
                showArrearsBucketDialog(mapping.arrearsDays);
            }
        } else {
            arrearsColumnName = null;
            if (btnArrearsFilter != null) {
                btnArrearsFilter.setVisibility(View.GONE);
                btnArrearsFilter.setOnClickListener(null);
            }
        }

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
        if (hasArrearsDays) {
            parts.add("Days: " + mapping.arrearsDays);
        }

        String summary = getString(R.string.bulk_sms_detected_columns_prefix) + " " + TextUtils.join(" • ", parts);
        txtDetectedColumns.setText(summary);
        txtDetectedColumns.setVisibility(View.VISIBLE);
    }

    private void showArrearsBucketDialog(@NonNull String columnName) {
        EditText rangeInput = new EditText(requireContext());
        rangeInput.setSingleLine(true);
        rangeInput.setHint("For example: 1 or 23-46");
        rangeInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT);

        LinearLayout inputContainer = new LinearLayout(requireContext());
        inputContainer.setOrientation(LinearLayout.VERTICAL);
        int horizontalPadding = (int) (24 * getResources().getDisplayMetrics().density);
        inputContainer.setPadding(horizontalPadding, 0, horizontalPadding, 0);
        inputContainer.addView(rangeInput);

        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Choose arrears customers")
                .setMessage("Detected days column: " + columnName
                        + "\nEnter one day (1) or an inclusive range (23-46).")
                .setView(inputContainer)
                .setPositiveButton("Apply", null)
                .setNeutralButton("All customers", (d, which) -> viewModel.applyArrearsFilter("All customers"))
                .setNegativeButton("Cancel", null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    String selection = rangeInput.getText() == null
                            ? "" : rangeInput.getText().toString().trim();
                    if (viewModel.applyArrearsFilter(selection)) {
                        dialog.dismiss();
                    } else {
                        rangeInput.setError("Enter a valid day or range, e.g. 1 or 23-46");
                    }
                }));
        dialog.show();
    }

    private void updateMessagePreviews(List<TemplateVariableExtractor.MessagePreview> previews) {
        if (previews != null && !previews.isEmpty() && previewAdapter != null) {
            previewAdapter.updatePreviews(previews);
            if (txtCampaignSmsParts != null) {
                String message = previews.get(0).message == null ? "" : previews.get(0).message;
                int segmentSize = message.matches(".*[^\\u0000-\\u007F].*") ? 70 : 160;
                int parts = message.isEmpty() ? 0 : (int) Math.ceil(message.length() / (double) segmentSize);
                txtCampaignSmsParts.setText("SMS parts: " + parts);
            }
            if (cardMessagePreview != null) {
                cardMessagePreview.setVisibility(View.VISIBLE);
            }
        } else {
            if (cardMessagePreview != null) {
                cardMessagePreview.setVisibility(View.GONE);
            }
        }
    }

    private void updateCampaignReadiness() {
        if (viewModel != null && viewModel.getRecipientsRaw().getValue() != null) {
            updateCampaignReadiness(viewModel.getRecipientsRaw().getValue().size());
        }
    }

    private void updateCampaignReadiness(int recipientCount) {
        if (txtCampaignReadiness == null) return;
        String template = inputTemplate == null || inputTemplate.getText() == null
                ? "" : inputTemplate.getText().toString().trim();
        if (recipientCount == 0) {
            txtCampaignReadiness.setText("Start by importing contacts");
        } else if (template.isEmpty()) {
            txtCampaignReadiness.setText("Add a message template");
        } else {
            txtCampaignReadiness.setText("Ready to preview and send");
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
            // Keep the import screen usable. The previous session is offered as an
            // inline action instead of blocking the workflow with a modal dialog.
            Snackbar.make(requireView(), "Previous upload ready to resume", Snackbar.LENGTH_INDEFINITE)
                .setAction("RESUME", v -> viewModel.resumeSession())
                .setActionTextColor(ContextCompat.getColor(requireContext(), R.color.color_primary))
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

        final EditText nameInput = new EditText(requireContext());
        nameInput.setHint("Campaign name");
        nameInput.setSingleLine(true);
        nameInput.setText("Bulk Campaign");
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Name this campaign")
                .setView(nameInput)
                .setPositiveButton("Send", (dialog, which) -> {
                    String name = nameInput.getText() == null ? "Bulk Campaign" : nameInput.getText().toString().trim();
                    viewModel.setCampaignName(name.isEmpty() ? "Bulk Campaign" : name);
                    int simSlot = (radioSim1 != null && radioSim1.isChecked()) ? 0 : 1;
                    viewModel.startBulkSend(inputTemplate.getText().toString(), simSlot);
                })
                .setNegativeButton("Cancel", null)
                .show();
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
    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // Null all view references so the GC can collect them while the Fragment
        // backstack keeps this instance alive.
        btnSelectFile      = null;
        btnSendBulk        = null;
        btnAddRecipient    = null;
        btnSaveTemplate    = null;
        btnTemplateLibrary = null;
        inputTemplate      = null;
        inputTemplateLayout = null;
        radioSim1          = null;
        radioSim2          = null;
        progressBar        = null;
        progressFileLoading = null;
        progressSending    = null;
        txtStatus          = null;
        txtSentCount       = null;
        txtFailedCount     = null;
        txtRecipientCount  = null;
        txtFileName        = null;
        txtProgress        = null;
        txtQueuedCount     = null;
        txtDeliveryStatus  = null;
        txtQueueStatus     = null;
        txtSessionInfo     = null;
        txtSendSpeed       = null;
        cardTemplateVariables  = null;
        cardMessagePreview     = null;
        recyclerViewTemplateVariables = null;
        recyclerViewMessagePreview    = null;
        txtVariableCount   = null;
        txtDetectedColumns = null;
        btnArrearsFilter   = null;
        btnClearAll        = null;
        btnRunQueue        = null;
        btnClearExhausted  = null;
        btnPauseResume     = null;
        btnStopSending     = null;
        btnSpeed300        = null;
        btnSpeed600        = null;
        btnSpeed1000       = null;
        // Adapters hold Context via their RecyclerView; release them too.
        variableAdapter    = null;
        previewAdapter     = null;
    }
}
