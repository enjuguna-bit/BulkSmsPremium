package com.afriserve.smsmanager.ui.sms;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.ContactsContract;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.TextUtils;
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
import com.afriserve.smsmanager.R;
import com.afriserve.smsmanager.databinding.FragmentSmsSendBinding;
import com.afriserve.smsmanager.billing.SubscriptionHelper;
import com.afriserve.smsmanager.ui.contacts.ContactSearchDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import dagger.hilt.android.AndroidEntryPoint;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Single SMS Fragment for sending individual messages
 * Uses Hilt for dependency injection
 */
@AndroidEntryPoint
public class SingleSmsFragment extends Fragment {
    private static final int FREE_MULTI_LIMIT = 7;
    private static final int SMS_PERMISSION_REQUEST_CODE = 1002;
    private static final int CONTACTS_PERMISSION_REQUEST_CODE = 1003;

    private FragmentSmsSendBinding binding;
    private SingleSmsViewModel viewModel;
    private ActivityResultLauncher<Intent> contactPickerLauncher;
    private TextWatcher messageTextWatcher;
    private ExecutorService subscriptionExecutor;
    private PendingContactAction pendingContactAction = PendingContactAction.NONE;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private enum PendingContactAction {
        NONE,
        SINGLE,
        MULTIPLE
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Initialize contact picker launcher
        contactPickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == android.app.Activity.RESULT_OK && result.getData() != null) {
                        handleContactSelection(result.getData());
                    }
                });
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        binding = FragmentSmsSendBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if (subscriptionExecutor == null || subscriptionExecutor.isShutdown()) {
            subscriptionExecutor = Executors.newSingleThreadExecutor();
        }

        // Initialize ViewModel
        viewModel = new ViewModelProvider(this).get(SingleSmsViewModel.class);

        setupClickListeners();
        setupMessageWatcher();
        observeViewModel();
        updateSimSelectionAvailability();

        // Request permissions if needed
        requestRequiredPermissions();
    }

    private void setupClickListeners() {
        binding.btnSendSms.setOnClickListener(v -> sendSms());
        binding.btnSelectContact.setOnClickListener(v -> requestContactsPermission(PendingContactAction.SINGLE));
        binding.btnSelectMultipleContacts.setOnClickListener(v -> requestContactsPermission(PendingContactAction.MULTIPLE));
        binding.radioSimGroup.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.radioSim2 && !hasReadPhoneStatePermission()) {
                Toast.makeText(requireContext(), "Grant phone permission to use SIM 2", Toast.LENGTH_SHORT).show();
                binding.radioSim1.setChecked(true);
                requestPermissions(new String[] { Manifest.permission.READ_PHONE_STATE }, SMS_PERMISSION_REQUEST_CODE);
            }
        });
    }

    private void setupMessageWatcher() {
        messageTextWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                int length = s.length();
                binding.txtCharCount.setText(length + " / 160 characters");

                // Show warning if message is long
                if (length > 160) {
                    int parts = (int) Math.ceil(length / 153.0);
                    binding.txtCharCount.setText(length + " / 160 characters (" + parts + " SMS)");
                    binding.txtCharCount.setTextColor(
                            ContextCompat.getColor(requireContext(), android.R.color.holo_orange_dark));
                } else {
                    binding.txtCharCount.setTextColor(
                            ContextCompat.getColor(requireContext(), android.R.color.darker_gray));
                }
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        };
        binding.inputMessage.addTextChangedListener(messageTextWatcher);
    }

    private void observeViewModel() {
        viewModel.getIsLoading().observe(getViewLifecycleOwner(), isLoading -> {
            binding.progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
            binding.btnSendSms.setEnabled(!isLoading);
        });

        viewModel.getStatus().observe(getViewLifecycleOwner(), status -> {
            if (status != null && !status.isEmpty()) {
                binding.txtStatus.setText(status);
                binding.statusCard.setVisibility(View.VISIBLE); // Use statusCard instead of txtStatus visibility
                                                                // directly
                binding.txtStatus.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.black));
            } else {
                binding.statusCard.setVisibility(View.GONE);
            }
        });

        viewModel.getError().observe(getViewLifecycleOwner(), error -> {
            if (error != null && !error.isEmpty()) {
                Toast.makeText(requireContext(), "Error: " + error, Toast.LENGTH_LONG).show();
                binding.txtStatus.setText(error);
                binding.statusCard.setVisibility(View.VISIBLE);
                binding.txtStatus.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.holo_red_dark));
            }
        });
    }

    private void selectContact() {
        // Use standard contact picker
        Intent intent = new Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI);
        contactPickerLauncher.launch(intent);
    }

    private void handleContactSelection(Intent data) {
        Uri contactUri = data.getData();
        if (contactUri != null) {
            try (android.database.Cursor cursor = requireContext().getContentResolver().query(
                    contactUri,
                    new String[] { ContactsContract.CommonDataKinds.Phone.NUMBER },
                    null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER);
                    if (numberIndex >= 0) {
                        String phoneNumber = cursor.getString(numberIndex);
                        binding.inputPhoneNumber.setText(phoneNumber);
                    }
                }
            } catch (Exception e) {
                Toast.makeText(requireContext(), "Failed to read contact", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void sendSms() {
        String phoneNumberInput = binding.inputPhoneNumber.getText() != null
                ? binding.inputPhoneNumber.getText().toString().trim()
                : "";
        String message = binding.inputMessage.getText() != null ? binding.inputMessage.getText().toString().trim() : "";

        // Check for SIM radio exists
        int simSlot = 0;
        if (binding.radioSim1.isChecked())
            simSlot = 0;
        else if (binding.radioSim2.isChecked())
            simSlot = 1;

        // Validation
        List<String> recipients = parseRecipients(phoneNumberInput);
        if (recipients.isEmpty()) {
            Toast.makeText(requireContext(), "Please enter at least one valid phone number", Toast.LENGTH_SHORT).show();
            binding.inputPhoneNumber.setError("Valid phone number required");
            return;
        }

        if (message.isEmpty()) {
            Toast.makeText(requireContext(), "Please enter a message", Toast.LENGTH_SHORT).show();
            binding.inputMessage.setError("Required");
            return;
        }

        refreshSubscriptionStatusAndSend(recipients, message, simSlot);
    }

    private void refreshSubscriptionStatusAndSend(List<String> recipients, String message, int simSlot) {
        Context context = getContext();
        if (!isAdded() || context == null) {
            return;
        }
        if (subscriptionExecutor == null || subscriptionExecutor.isShutdown()) {
            subscriptionExecutor = Executors.newSingleThreadExecutor();
        }

        final Context appContext = context.getApplicationContext();
        subscriptionExecutor.execute(() -> {
            SubscriptionHelper.SubscriptionStatus status;
            try {
                status = SubscriptionHelper.INSTANCE.refreshSubscriptionStatusBlocking(appContext, true);
            } catch (Exception e) {
                status = SubscriptionHelper.INSTANCE.getCachedStatus(appContext);
            }
            final SubscriptionHelper.SubscriptionStatus finalStatus = status;
            mainHandler.post(() -> {
                if (!isAdded() || binding == null) {
                    return;
                }
                if (recipients.size() > 1 && !isSubscriptionActive(finalStatus) && recipients.size() > FREE_MULTI_LIMIT) {
                    showFreeLimitDialog(FREE_MULTI_LIMIT);
                    return;
                }

                // Check permissions
                if (!hasSmsPermissions()) {
                    requestRequiredPermissions();
                    return;
                }

                // Send SMS via ViewModel
                if (recipients.size() == 1) {
                    viewModel.sendSms(recipients.get(0), message, simSlot);
                } else {
                    viewModel.sendSmsToMultiple(recipients, message, simSlot);
                }
            });
        });
    }

    private boolean isSubscriptionActive(@Nullable SubscriptionHelper.SubscriptionStatus status) {
        if (status == null) {
            return false;
        }
        long now = System.currentTimeMillis();
        return status.getPremium() && (status.getPaidUntilMillis() == null || status.getPaidUntilMillis() > now);
    }

    private void selectMultipleContacts() {
        ContactSearchDialog dialog = ContactSearchDialog.newInstance(true, new ContactSearchDialog.OnContactSelectedListener() {
            @Override
            public void onContactSelected(ContactSearchDialog.ContactInfo contact) {
                // Not used for multiple selection
            }

            @Override
            public void onMultipleContactsSelected(List<ContactSearchDialog.ContactInfo> contacts) {
                applySelectedContacts(contacts);
            }
        });
        dialog.show(getParentFragmentManager(), "contact_search_multi");
    }

    private void requestContactsPermission(PendingContactAction action) {
        pendingContactAction = action;
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_CONTACTS)
                == PackageManager.PERMISSION_GRANTED) {
            runPendingContactAction();
            return;
        }
        requestPermissions(new String[] { Manifest.permission.READ_CONTACTS }, CONTACTS_PERMISSION_REQUEST_CODE);
    }

    private void runPendingContactAction() {
        PendingContactAction action = pendingContactAction;
        pendingContactAction = PendingContactAction.NONE;
        if (action == PendingContactAction.SINGLE) {
            selectContact();
        } else if (action == PendingContactAction.MULTIPLE) {
            selectMultipleContacts();
        }
    }

    private void applySelectedContacts(List<ContactSearchDialog.ContactInfo> contacts) {
        if (contacts == null || contacts.isEmpty()) {
            Toast.makeText(requireContext(), "No contacts selected", Toast.LENGTH_SHORT).show();
            return;
        }

        List<String> existing = parseRecipients(binding.inputPhoneNumber.getText() != null
                ? binding.inputPhoneNumber.getText().toString()
                : "");

        Map<String, String> unique = new LinkedHashMap<>();
        for (String number : existing) {
            String normalized = normalizeRecipient(number);
            if (!normalized.isEmpty()) {
                unique.put(normalized, normalized);
            }
        }

        int beforeCount = unique.size();
        if (!hasPremiumAccess() && beforeCount >= FREE_MULTI_LIMIT) {
            showFreeLimitDialog(FREE_MULTI_LIMIT);
            return;
        }

        for (ContactSearchDialog.ContactInfo contact : contacts) {
            String normalized = normalizeRecipient(contact.phoneNumber);
            if (!normalized.isEmpty()) {
                if (!hasPremiumAccess() && unique.size() >= FREE_MULTI_LIMIT) {
                    break;
                }
                unique.put(normalized, normalized);
            }
        }

        List<String> merged = new ArrayList<>(unique.values());
        binding.inputPhoneNumber.setText(TextUtils.join(", ", merged));

        int added = unique.size() - beforeCount;
        if (added > 0) {
            Toast.makeText(requireContext(), "Added " + added + " contact(s)", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(requireContext(), "Selected contacts already added", Toast.LENGTH_SHORT).show();
        }

        if (!hasPremiumAccess() && unique.size() >= FREE_MULTI_LIMIT && contacts.size() > added) {
            showFreeLimitDialog(FREE_MULTI_LIMIT);
        }
    }

    private List<String> parseRecipients(String input) {
        List<String> recipients = new ArrayList<>();
        if (input == null || input.trim().isEmpty()) {
            return recipients;
        }

        String[] parts = input.split("[,;\\n]+");
        Map<String, String> unique = new LinkedHashMap<>();
        for (String part : parts) {
            String normalized = normalizeRecipient(part);
            if (!normalized.isEmpty()) {
                unique.put(normalized, normalized);
            }
        }
        recipients.addAll(unique.values());
        return recipients;
    }

    private String normalizeRecipient(String value) {
        if (value == null) {
            return "";
        }
        String normalized = com.afriserve.smsmanager.data.utils.PhoneNumberUtils.normalizePhoneNumber(value);
        if (normalized == null || normalized.isEmpty()) {
            return "";
        }
        if (!com.afriserve.smsmanager.data.utils.PhoneNumberUtils.isValidPhoneNumber(normalized)) {
            return "";
        }
        return normalized;
    }

    private void requestRequiredPermissions() {
        if (hasSmsPermissions()) {
            updateSimSelectionAvailability();
            return;
        }
        requestPermissions(new String[] { Manifest.permission.SEND_SMS }, SMS_PERMISSION_REQUEST_CODE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
            @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == SMS_PERMISSION_REQUEST_CODE) {
            if (!hasSmsPermissions()) {
                Toast.makeText(requireContext(),
                        "SEND_SMS permission is required to send messages",
                        Toast.LENGTH_LONG).show();
            }
            if (!hasReadPhoneStatePermission()) {
                Toast.makeText(requireContext(),
                        "SIM 2 selection is unavailable without phone permission",
                        Toast.LENGTH_SHORT).show();
            }
            updateSimSelectionAvailability();
        } else if (requestCode == CONTACTS_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                runPendingContactAction();
            } else {
                pendingContactAction = PendingContactAction.NONE;
                Toast.makeText(requireContext(), "Contacts permission denied", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private boolean hasSmsPermissions() {
        return ContextCompat.checkSelfPermission(requireContext(),
                Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED;
    }

    private boolean hasReadPhoneStatePermission() {
        return ContextCompat.checkSelfPermission(requireContext(),
                Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED;
    }

    private void updateSimSelectionAvailability() {
        if (binding == null) {
            return;
        }

        boolean hasPhoneStatePermission = hasReadPhoneStatePermission();
        boolean sim2Available = false;

        if (hasPhoneStatePermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            try {
                SubscriptionManager subscriptionManager = (SubscriptionManager) requireContext()
                        .getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE);
                List<SubscriptionInfo> activeSubscriptions = subscriptionManager != null
                        ? subscriptionManager.getActiveSubscriptionInfoList()
                        : null;
                sim2Available = activeSubscriptions != null && activeSubscriptions.size() > 1;
            } catch (Exception ignored) {
                sim2Available = false;
            }
        }

        boolean enableSim2 = hasPhoneStatePermission && sim2Available;
        binding.radioSim2.setEnabled(enableSim2);
        binding.radioSim2.setAlpha(enableSim2 ? 1.0f : 0.5f);
        if (!enableSim2 && binding.radioSim2.isChecked()) {
            binding.radioSim1.setChecked(true);
        }
    }

    private boolean hasPremiumAccess() {
        Context context = getContext();
        return context != null && SubscriptionHelper.INSTANCE.hasActiveSubscription(context);
    }

    private void showFreeLimitDialog(int limit) {
        if (!isAdded()) {
            return;
        }
        Context context = requireContext();
        if (SubscriptionHelper.INSTANCE.isPaymentPending(context)) {
            Toast.makeText(requireContext(),
                    "Payment processing. Please refresh status.",
                    Toast.LENGTH_LONG).show();
            return;
        }

        new MaterialAlertDialogBuilder(context)
                .setTitle("Premium required")
                .setMessage("Free version allows up to " + limit + " recipients for multi-send.")
                .setPositiveButton("Subscribe", (dialog, which) ->
                        SubscriptionHelper.INSTANCE.launch(context))
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();

        // Clean up TextWatcher to prevent memory leaks
        if (binding != null && messageTextWatcher != null) {
            binding.inputMessage.removeTextChangedListener(messageTextWatcher);
        }

        pendingContactAction = PendingContactAction.NONE;
        // Clear binding reference
        binding = null;
        if (subscriptionExecutor != null && !subscriptionExecutor.isShutdown()) {
            subscriptionExecutor.shutdownNow();
        }
        subscriptionExecutor = null;
    }
}
