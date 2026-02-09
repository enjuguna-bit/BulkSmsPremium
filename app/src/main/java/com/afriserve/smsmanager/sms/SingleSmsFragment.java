package com.afriserve.smsmanager.ui.sms;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
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
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import com.afriserve.smsmanager.databinding.FragmentSmsSendBinding;
import com.afriserve.smsmanager.billing.SubscriptionHelper;
import com.afriserve.smsmanager.sms.DefaultSmsAppManager;
import com.afriserve.smsmanager.ui.contacts.ContactSearchDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import dagger.hilt.android.AndroidEntryPoint;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Single SMS Fragment for sending individual messages
 * Uses Hilt for dependency injection
 */
@AndroidEntryPoint
public class SingleSmsFragment extends Fragment {
    private static final int FREE_MULTI_LIMIT = 7;

    private FragmentSmsSendBinding binding;
    private SingleSmsViewModel viewModel;
    private ActivityResultLauncher<Intent> contactPickerLauncher;
    private TextWatcher messageTextWatcher;

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

        // Initialize ViewModel
        viewModel = new ViewModelProvider(this).get(SingleSmsViewModel.class);

        setupClickListeners();
        setupMessageWatcher();
        observeViewModel();

        // Request permissions if needed
        requestPermissions();
    }

    private void setupClickListeners() {
        binding.btnSendSms.setOnClickListener(v -> sendSms());
        binding.btnSelectContact.setOnClickListener(v -> selectContact());
        binding.btnSelectMultipleContacts.setOnClickListener(v -> selectMultipleContacts());
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
            Toast.makeText(requireContext(), "Please enter at least one phone number", Toast.LENGTH_SHORT).show();
            binding.inputPhoneNumber.setError("Required");
            return;
        }

        if (message.isEmpty()) {
            Toast.makeText(requireContext(), "Please enter a message", Toast.LENGTH_SHORT).show();
            binding.inputMessage.setError("Required");
            return;
        }

        if (recipients.size() > 1 && !hasPremiumAccess() && recipients.size() > FREE_MULTI_LIMIT) {
            showFreeLimitDialog(FREE_MULTI_LIMIT);
            return;
        }

        // Check permissions
        if (!hasSmsPermissions()) {
            requestPermissions();
            return;
        }

        // Send SMS via ViewModel
        if (recipients.size() == 1) {
            viewModel.sendSms(recipients.get(0), message, simSlot);
        } else {
            viewModel.sendSmsToMultiple(recipients, message, simSlot);
        }
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
            return value.trim().replaceAll("[\\s\\-()]", "");
        }
        return normalized;
    }

    private void requestPermissions() {
        if (!ensureDefaultSmsApp()) {
            return;
        }
        String[] permissions = {
                Manifest.permission.SEND_SMS,
                Manifest.permission.READ_SMS,
                Manifest.permission.READ_CONTACTS,
                Manifest.permission.READ_PHONE_STATE
        };

        boolean allGranted = true;
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(requireContext(), permission) != PackageManager.PERMISSION_GRANTED) {
                allGranted = false;
                break;
            }
        }

        if (!allGranted) {
            requestPermissions(permissions, 1002);
        }
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

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
            @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == 1002) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }

            if (!allGranted) {
                Toast.makeText(requireContext(),
                        "Permissions required to send SMS",
                        Toast.LENGTH_LONG).show();
            }
        }
    }

    private boolean hasSmsPermissions() {
        return ContextCompat.checkSelfPermission(requireContext(),
                Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED;
    }

    private boolean hasPremiumAccess() {
        return SubscriptionHelper.INSTANCE.hasActiveSubscription(requireContext());
    }

    private void showFreeLimitDialog(int limit) {
        if (SubscriptionHelper.INSTANCE.isPaymentPending(requireContext())) {
            Toast.makeText(requireContext(),
                    "Payment processing. Please refresh status.",
                    Toast.LENGTH_LONG).show();
            return;
        }

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Premium required")
                .setMessage("Free version allows up to " + limit + " recipients for multi-send.")
                .setPositiveButton("Subscribe", (dialog, which) ->
                        SubscriptionHelper.INSTANCE.launch(requireContext()))
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

        // Clear binding reference
        binding = null;
    }
}
