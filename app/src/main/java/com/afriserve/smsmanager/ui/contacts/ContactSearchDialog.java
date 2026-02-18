package com.afriserve.smsmanager.ui.contacts;

import android.Manifest;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.afriserve.smsmanager.R;
import com.afriserve.smsmanager.billing.SubscriptionHelper;
import com.afriserve.smsmanager.databinding.DialogContactSearchBinding;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import dagger.hilt.android.AndroidEntryPoint;

/**
 * Dialog fragment for contact search and selection
 */
@AndroidEntryPoint
public class ContactSearchDialog extends DialogFragment {
    private static final int FREE_MULTI_LIMIT = 7;
    
    private DialogContactSearchBinding binding;
    private ContactSearchViewModel viewModel;
    private ContactAdapter contactAdapter;
    private OnContactSelectedListener listener;
    private TextWatcher searchWatcher;
    private boolean allowMultipleSelection;
    private ExecutorService subscriptionExecutor;
    private volatile boolean premiumAccess;
    private volatile boolean premiumStatusInitialized;
    private volatile boolean premiumCheckInProgress;
    
    public interface OnContactSelectedListener {
        void onContactSelected(ContactInfo contact);
        void onMultipleContactsSelected(java.util.List<ContactInfo> contacts);
    }
    
    public static ContactSearchDialog newInstance(OnContactSelectedListener listener) {
        ContactSearchDialog dialog = new ContactSearchDialog();
        dialog.setListener(listener);
        return dialog;
    }
    
    public static ContactSearchDialog newInstance(boolean allowMultiple, OnContactSelectedListener listener) {
        ContactSearchDialog dialog = new ContactSearchDialog();
        dialog.setListener(listener);
        Bundle args = new Bundle();
        args.putBoolean("allow_multiple", allowMultiple);
        dialog.setArguments(args);
        return dialog;
    }
    
    public void setListener(OnContactSelectedListener listener) {
        this.listener = listener;
    }
    
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, 
                           @Nullable Bundle savedInstanceState) {
        binding = DialogContactSearchBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        subscriptionExecutor = Executors.newSingleThreadExecutor();
    }

    @Override
    public void onStart() {
        super.onStart();
        if (getDialog() != null && getDialog().getWindow() != null) {
            getDialog().getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            getDialog().getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
    }
    
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        viewModel = new ViewModelProvider(this).get(ContactSearchViewModel.class);
        allowMultipleSelection = getArguments() != null && getArguments().getBoolean("allow_multiple", false);
        binding.layoutMultiSelectActions.setVisibility(allowMultipleSelection ? View.VISIBLE : View.GONE);
        if (allowMultipleSelection) {
            binding.buttonAddSelected.setEnabled(false);
        }
        
        setupRecyclerView();
        setupSearchListener();
        setupClickListeners();
        observeViewModel();

        if (allowMultipleSelection) {
            updateSelectionCount();
        }

        if (allowMultipleSelection) {
            refreshPremiumStatus(false, null);
        }
        
        // Check permissions and load contacts
        if (hasContactsPermission()) {
            viewModel.loadContacts();
        } else {
            requestContactsPermission();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (allowMultipleSelection) {
            refreshPremiumStatus(true, null);
        }
    }
    
    private void setupRecyclerView() {
        contactAdapter = new ContactAdapter(new java.util.ArrayList<>(), contact -> {
            if (allowMultipleSelection) {
                if (!hasPremiumAccess() && !contactAdapter.isSelected(contact)
                        && contactAdapter.getSelectedCount() >= FREE_MULTI_LIMIT) {
                    verifyPremiumBeforeLimitAction(FREE_MULTI_LIMIT, () -> {
                        contactAdapter.toggleSelection(contact);
                        updateSelectionCount();
                    });
                    return;
                }
                contactAdapter.toggleSelection(contact);
                updateSelectionCount();
            } else {
                if (listener != null) {
                    listener.onContactSelected(contact);
                }
                dismiss();
            }
        });
        contactAdapter.setAllowMultipleSelection(allowMultipleSelection);
        
        binding.recyclerViewContacts.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.recyclerViewContacts.setAdapter(contactAdapter);
    }
    
    private void setupSearchListener() {
        searchWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                viewModel.searchContacts(s.toString());
            }
            
            @Override
            public void afterTextChanged(Editable s) {}
        };
        
        binding.editTextSearch.addTextChangedListener(searchWatcher);
    }
    
    private void setupClickListeners() {
        binding.buttonClose.setOnClickListener(v -> dismiss());
        
        binding.buttonOpenContacts.setOnClickListener(v -> {
            if (hasContactsPermission()) {
                viewModel.loadContacts();
            } else {
                requestContactsPermission();
            }
        });
        
        binding.buttonClear.setOnClickListener(v -> {
            binding.editTextSearch.setText("");
        });

        if (allowMultipleSelection) {
            binding.buttonAddSelected.setOnClickListener(v -> {
                java.util.List<ContactInfo> selected = contactAdapter.getSelectedContacts();
                if (selected.isEmpty()) {
                    Toast.makeText(requireContext(), "Select at least one contact", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (!hasPremiumAccess() && selected.size() > FREE_MULTI_LIMIT) {
                    verifyPremiumBeforeLimitAction(FREE_MULTI_LIMIT, () -> {
                        java.util.List<ContactInfo> updatedSelected = contactAdapter.getSelectedContacts();
                        if (listener != null) {
                            listener.onMultipleContactsSelected(updatedSelected);
                        }
                        dismiss();
                    });
                    return;
                }
                if (listener != null) {
                    listener.onMultipleContactsSelected(selected);
                }
                dismiss();
            });
        }
    }
    
    private void observeViewModel() {
        viewModel.getContacts().observe(getViewLifecycleOwner(), contacts -> {
            contactAdapter.updateContacts(contacts);
            
            if (contacts.isEmpty()) {
                binding.textViewEmpty.setVisibility(View.VISIBLE);
                binding.recyclerViewContacts.setVisibility(View.GONE);
            } else {
                binding.textViewEmpty.setVisibility(View.GONE);
                binding.recyclerViewContacts.setVisibility(View.VISIBLE);
            }
            
            updateSelectionCount();
        });
        
        viewModel.getIsLoading().observe(getViewLifecycleOwner(), isLoading -> {
            binding.progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
            binding.buttonOpenContacts.setEnabled(!isLoading);
        });
        
        viewModel.getError().observe(getViewLifecycleOwner(), error -> {
            if (error != null && !error.isEmpty()) {
                Toast.makeText(requireContext(), error, Toast.LENGTH_LONG).show();
            }
        });
    }
    
    private void updateSelectionCount() {
        if (!allowMultipleSelection || binding == null) {
            return;
        }
        int count = contactAdapter.getSelectedCount();
        binding.textViewSelectedCount.setText(getString(R.string.contact_selected_count, count));
        binding.buttonAddSelected.setEnabled(count > 0);
    }

    private boolean hasPremiumAccess() {
        if (premiumStatusInitialized) {
            return premiumAccess;
        }
        boolean cached = SubscriptionHelper.INSTANCE.hasActiveSubscription(requireContext());
        premiumAccess = cached;
        premiumStatusInitialized = true;
        return cached;
    }

    private void verifyPremiumBeforeLimitAction(int limit, @NonNull Runnable onPremiumGranted) {
        if (hasPremiumAccess()) {
            onPremiumGranted.run();
            return;
        }

        if (premiumCheckInProgress) {
            Toast.makeText(requireContext(), "Checking subscription status...", Toast.LENGTH_SHORT).show();
            return;
        }

        refreshPremiumStatus(true, hasPremium -> {
            if (hasPremium) {
                onPremiumGranted.run();
            } else {
                showFreeLimitDialog(limit);
            }
        });
    }

    private void refreshPremiumStatus(boolean forceRefresh, @Nullable PremiumStatusCallback callback) {
        if (!isAdded()) {
            return;
        }

        premiumCheckInProgress = true;
        getSubscriptionExecutor().execute(() -> {
            boolean hasPremium;
            try {
                SubscriptionHelper.SubscriptionStatus status =
                        SubscriptionHelper.INSTANCE.refreshSubscriptionStatusBlocking(requireContext(), forceRefresh);
                hasPremium = isSubscriptionActive(status);
            } catch (Exception e) {
                hasPremium = SubscriptionHelper.INSTANCE.hasActiveSubscription(requireContext());
            }

            premiumAccess = hasPremium;
            premiumStatusInitialized = true;
            final boolean hasPremiumFinal = hasPremium;

            if (!isAdded()) {
                premiumCheckInProgress = false;
                return;
            }

            requireActivity().runOnUiThread(() -> {
                premiumCheckInProgress = false;
                if (!isAdded()) {
                    return;
                }
                if (callback != null) {
                    callback.onChecked(hasPremiumFinal);
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

    @NonNull
    private ExecutorService getSubscriptionExecutor() {
        if (subscriptionExecutor == null || subscriptionExecutor.isShutdown()) {
            subscriptionExecutor = Executors.newSingleThreadExecutor();
        }
        return subscriptionExecutor;
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
                .setMessage("Free version allows up to " + limit + " recipients.")
                .setPositiveButton("Subscribe", (dialog, which) ->
                        SubscriptionHelper.INSTANCE.launch(requireContext()))
                .setNegativeButton("Cancel", null)
                .show();
    }
    
    private boolean hasContactsPermission() {
        return ContextCompat.checkSelfPermission(requireContext(),
                Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED;
    }
    
    private void requestContactsPermission() {
        requestPermissions(new String[]{Manifest.permission.READ_CONTACTS}, 1001);
    }
    
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, 
                                         @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        
        if (requestCode == 1001) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                viewModel.loadContacts();
            } else {
                Toast.makeText(requireContext(), 
                    "Contacts permission required for contact search", Toast.LENGTH_LONG).show();
                // Show empty state and instruct user via Toast (empty layout is a container)
                binding.textViewEmpty.setVisibility(View.VISIBLE);
                binding.recyclerViewContacts.setVisibility(View.GONE);
            }
        }
    }
    
    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (binding != null && binding.editTextSearch != null && searchWatcher != null) {
            binding.editTextSearch.removeTextChangedListener(searchWatcher);
        }
        binding = null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (subscriptionExecutor != null) {
            subscriptionExecutor.shutdown();
            subscriptionExecutor = null;
        }
    }

    private interface PremiumStatusCallback {
        void onChecked(boolean hasPremium);
    }
    
    /**
     * Contact information model
     */
    public static class ContactInfo {
        public final String id;
        public final String name;
        public final String phoneNumber;
        public final String email;
        public final String photoUri;
        
        public ContactInfo(String id, String name, String phoneNumber, String email, String photoUri) {
            this.id = id;
            this.name = name;
            this.phoneNumber = phoneNumber;
            this.email = email;
            this.photoUri = photoUri;
        }
        
        @Override
        public String toString() {
            return name + " (" + phoneNumber + ")";
        }
        
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ContactInfo that = (ContactInfo) o;
            return id != null ? id.equals(that.id) : that.id == null;
        }
        
        @Override
        public int hashCode() {
            return id != null ? id.hashCode() : 0;
        }
    }
}
