package com.afriserve.smsmanager.ui.csv;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.afriserve.smsmanager.AppConfig;
import com.afriserve.smsmanager.R;
import com.afriserve.smsmanager.models.CsvRowModel;
import com.afriserve.smsmanager.models.Recipient;
import com.afriserve.smsmanager.data.persistence.UploadPersistenceService;
import com.afriserve.smsmanager.data.persistence.UploadPersistenceService.UploadSession;
import com.afriserve.smsmanager.billing.SubscriptionHelper;
import com.afriserve.smsmanager.worker.BulkSmsWorkManager;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import dagger.hilt.android.AndroidEntryPoint;
import javax.inject.Inject;

@AndroidEntryPoint
public class CsvUploadFragment extends Fragment {
    private static final int PICK_CSV_FILE = 1001;

    private ProgressBar progressLoading;
    private TextView tvSummary, tvPhoneValidation;
    private TextInputEditText etMessageTemplate;
    private MaterialButton btnSelectCsv, btnInsertPlaceholder, btnPreview, btnSend;
    private LinearLayout llPreviewContainer;
    private RecyclerView previewRecyclerView;

    private CsvUploadViewModel viewModel;
    private PreviewAdapter previewAdapter;
    private ActivityResultLauncher<Intent> filePickerLauncher;

    @Inject
    UploadPersistenceService uploadPersistence;

    private ExecutorService subscriptionExecutor;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        subscriptionExecutor = Executors.newSingleThreadExecutor();
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, 
                           @Nullable ViewGroup container, 
                           @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_csv_upload, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        viewModel = new ViewModelProvider(this, requireActivity().getDefaultViewModelProviderFactory()).get(CsvUploadViewModel.class);
        
        initViews(view);
        setupFilePicker();
        setupClickListeners();
        setupObservers();
    }

    private void initViews(View view) {
        progressLoading = view.findViewById(R.id.progress_loading);
        tvSummary = view.findViewById(R.id.tv_summary);
        tvPhoneValidation = view.findViewById(R.id.tv_phone_validation);
        etMessageTemplate = view.findViewById(R.id.et_message_template);
        btnSelectCsv = view.findViewById(R.id.btn_select_csv);
        btnInsertPlaceholder = view.findViewById(R.id.btn_insert_placeholder);
        btnPreview = view.findViewById(R.id.btn_preview);
        btnSend = view.findViewById(R.id.btn_send);
        llPreviewContainer = view.findViewById(R.id.ll_preview_container);
    }

    private void setupFilePicker() {
        filePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    if (uri != null) {
                        viewModel.processCsvFile(uri, requireContext().getContentResolver());
                    }
                }
            }
        );
    }

    private void setupClickListeners() {
        btnSelectCsv.setOnClickListener(v -> openFilePicker());
        btnInsertPlaceholder.setOnClickListener(v -> showPlaceholderDialog());
        btnPreview.setOnClickListener(v -> generatePreview());
        btnSend.setOnClickListener(v -> sendBulkSms());
    }

    private void setupObservers() {
        viewModel.getIsLoading().observe(getViewLifecycleOwner(), isLoading -> {
            progressLoading.setVisibility(isLoading ? View.VISIBLE : View.GONE);
            btnSelectCsv.setEnabled(!isLoading);
            btnPreview.setEnabled(!isLoading);
            btnSend.setEnabled(!isLoading);
        });

        viewModel.getSummary().observe(getViewLifecycleOwner(), summary -> {
            tvSummary.setText(summary);
        });

        viewModel.getPhoneValidation().observe(getViewLifecycleOwner(), validation -> {
            if (validation != null) {
                String validationText = "Valid: " + validation.getValidCount() + 
                                       "/" + validation.getTotalCount() + 
                                       " (" + validation.getValidityPercentage() + "%)";
                tvPhoneValidation.setText(validationText);
            }
        });

        viewModel.getMessagePreviews().observe(getViewLifecycleOwner(), this::showPreviews);

        viewModel.getError().observe(getViewLifecycleOwner(), this::showError);

        viewModel.getSendSuccess().observe(getViewLifecycleOwner(), success -> {
            if (success) {
                Snackbar.make(requireView(), "Bulk SMS queued for sending.", Snackbar.LENGTH_LONG).show();
                // Clear form after successful send
                etMessageTemplate.setText("");
                llPreviewContainer.removeAllViews();
                viewModel.clear();
            }
        });
    }

    private void openFilePicker() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"text/csv", "application/vnd.ms-excel", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "text/plain"});
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, false);
        filePickerLauncher.launch(Intent.createChooser(intent, "Select CSV or Excel file"));
    }

    private void showPlaceholderDialog() {
        List<String> columns = viewModel.getPlaceholders().getValue();
        if (columns == null || columns.isEmpty()) {
            Toast.makeText(requireContext(), "Please select a CSV file first", Toast.LENGTH_SHORT).show();
            return;
        }

        new MaterialAlertDialogBuilder(requireContext())
            .setTitle("Insert Placeholder")
            .setMessage("Select a column to insert as placeholder:")
            .setItems(columns.toArray(new String[0]), (dialog, which) -> {
                String placeholder = columns.get(which);
                int cursorStart = etMessageTemplate.getSelectionStart();
                int cursorEnd = etMessageTemplate.getSelectionEnd();
                String currentText = etMessageTemplate.getText().toString();
                String newText = currentText.substring(0, cursorStart) + placeholder + currentText.substring(cursorEnd);
                etMessageTemplate.setText(newText);
                etMessageTemplate.setSelection(cursorStart + placeholder.length());
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void generatePreview() {
        String template = etMessageTemplate.getText().toString().trim();
        if (template.isEmpty()) {
            Toast.makeText(requireContext(), "Please enter a message template", Toast.LENGTH_SHORT).show();
            return;
        }

        viewModel.updateMessageTemplate(template);
    }

    private void showPreviews(List<String> previews) {
        llPreviewContainer.removeAllViews();
        
        if (previews.isEmpty()) {
            TextView emptyView = new TextView(requireContext());
            emptyView.setText("No preview data available");
            emptyView.setPadding(16, 16, 16, 16);
            llPreviewContainer.addView(emptyView);
            return;
        }

        for (String preview : previews) {
            TextView previewView = new TextView(requireContext());
            previewView.setText(preview);
            previewView.setPadding(16, 8, 16, 8);
            previewView.setBackgroundColor(getResources().getColor(android.R.color.background_light));
            
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            );
            params.setMargins(0, 8, 0, 8);
            previewView.setLayoutParams(params);
            
            llPreviewContainer.addView(previewView);
        }
    }

    private void sendBulkSms() {
        String template = etMessageTemplate.getText().toString().trim();
        if (template.isEmpty()) {
            Toast.makeText(requireContext(), "Please enter a message template", Toast.LENGTH_SHORT).show();
            return;
        }

        List<com.afriserve.smsmanager.data.csv.CsvRecipient> recipients = viewModel.getRecipientsToSend();
        if (recipients.isEmpty()) {
            Toast.makeText(requireContext(), "No valid recipients found", Toast.LENGTH_SHORT).show();
            return;
        }

        refreshSubscriptionStatusAndSend(recipients, template);
    }

    private void refreshSubscriptionStatusAndSend(
            @NonNull List<com.afriserve.smsmanager.data.csv.CsvRecipient> recipients,
            @NonNull String template
    ) {
        if (!isAdded()) {
            return;
        }
        if (btnSend != null) {
            btnSend.setEnabled(false);
        }
        getSubscriptionExecutor().execute(() -> {
            SubscriptionHelper.SubscriptionStatus status;
            try {
                status = SubscriptionHelper.INSTANCE.refreshSubscriptionStatusBlocking(requireContext(), true);
            } catch (Exception e) {
                status = SubscriptionHelper.INSTANCE.getCachedStatus(requireContext());
            }
            final SubscriptionHelper.SubscriptionStatus finalStatus = status;

            if (!isAdded()) {
                return;
            }
            requireActivity().runOnUiThread(() -> {
                if (btnSend != null) {
                    btnSend.setEnabled(true);
                }
                if (!isSubscriptionActive(finalStatus)
                        && recipients.size() > AppConfig.Limits.FREE_RECIPIENTS_LIMIT) {
                    Snackbar.make(requireView(),
                            "Large send detected. Continuing with delivery-safe mode.",
                            Snackbar.LENGTH_LONG).show();
                }
                proceedWithSend(recipients, template);
            });
        });
    }

    private void proceedWithSend(
            @NonNull List<com.afriserve.smsmanager.data.csv.CsvRecipient> recipients,
            @NonNull String template
    ) {
        new MaterialAlertDialogBuilder(requireContext())
            .setTitle("Send Bulk SMS")
            .setMessage("Are you sure you want to send SMS to all recipients? This will send " + recipients.size() + " messages.")
            .setPositiveButton("Send", (dialog, which) -> {
                UploadSession session = new UploadSession();
                session.fileId = UUID.randomUUID().toString();
                session.fileName = "CSV Upload";
                session.template = template;
                session.recipients = new ArrayList<>();
                session.totalRecords = recipients.size();
                session.validRecords = recipients.size();
                session.processingStatus = "ready";
                session.sendSpeed = 2000;
                session.simSlot = 0;
                session.campaignName = "CSV Campaign";
                session.campaignType = "TRANSACTIONAL";
                session.source = "csv";

                for (com.afriserve.smsmanager.data.csv.CsvRecipient csvRecipient : recipients) {
                    String phone = csvRecipient.getPhoneNumber();
                    String name = null;
                    if (csvRecipient.getData() != null) {
                        if (csvRecipient.getData().containsKey("name")) {
                            name = csvRecipient.getData().get("name");
                        } else if (csvRecipient.getData().containsKey("Name")) {
                            name = csvRecipient.getData().get("Name");
                        }
                    }
                    Recipient recipient = new Recipient(name, phone, null, false, csvRecipient.getData());
                    session.recipients.add(recipient);
                }

                uploadPersistence.saveCurrentUploadSync(session);
                BulkSmsWorkManager.enqueueBulkSend(requireContext(), session.fileId, 0L, true);
                viewModel.markSendQueued();
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private boolean isSubscriptionActive(@Nullable SubscriptionHelper.SubscriptionStatus status) {
        if (status == null) {
            return false;
        }
        long now = System.currentTimeMillis();
        return status.getPremium() && (status.getPaidUntilMillis() == null || status.getPaidUntilMillis() > now);
    }

    @NonNull
    private ExecutorService getSubscriptionExecutor() {
        if (subscriptionExecutor == null || subscriptionExecutor.isShutdown()) {
            subscriptionExecutor = Executors.newSingleThreadExecutor();
        }
        return subscriptionExecutor;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (subscriptionExecutor != null) {
            subscriptionExecutor.shutdown();
            subscriptionExecutor = null;
        }
    }

    private void showError(String message) {
        if (message != null) {
            Snackbar.make(requireView(), message, Snackbar.LENGTH_LONG).show();
        }
    }
}
