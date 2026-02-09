package com.bulksms.smsmanager.ui.contacts;

import android.content.Intent;
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
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.bulksms.smsmanager.R;
import com.bulksms.smsmanager.databinding.DialogContactSearchBinding;
import com.bulksms.smsmanager.ui.contacts.ContactSearchViewModel.ContactInfo;
import com.google.android.material.textfield.TextInputEditText;
import dagger.hilt.android.AndroidEntryPoint;

/**
 * Dialog fragment for contact search and selection
 */
// @AndroidEntryPoint
public class ContactSearchDialog extends DialogFragment {
    
    private DialogContactSearchBinding binding;
    private ContactSearchViewModel viewModel;
    private ContactAdapter contactAdapter;
    private OnContactSelectedListener listener;
    private TextWatcher searchWatcher;
    
    public interface OnContactSelectedListener {
        void onContactSelected(ContactInfo contact);
    }
    
    public static ContactSearchDialog newInstance(OnContactSelectedListener listener) {
        ContactSearchDialog dialog = new ContactSearchDialog();
        dialog.setListener(listener);
        return dialog;
    }
    
    public void setListener(OnContactSelectedListener listener) {
        this.listener = listener;
    }
    
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, 
                           @Nullable ViewGroup container, 
                           @Nullable Bundle savedInstanceState) {
        binding = DialogContactSearchBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }
    
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        viewModel = new ViewModelProvider(this).get(ContactSearchViewModel.class);
        
        setupRecyclerView();
        setupSearchInput();
        setupClickListeners();
        observeViewModel();
        
        // Load recent contacts initially
        viewModel.getRecentContacts();
    }
    
    private void setupRecyclerView() {
        contactAdapter = new ContactAdapter(contact -> {
            if (listener != null) {
                listener.onContactSelected(contact);
                dismiss();
            }
        });
        
        binding.recyclerViewContacts.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.recyclerViewContacts.setAdapter(contactAdapter);
    }
    
    private void setupSearchInput() {
        searchWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                // Debounce search - only search when user stops typing
                binding.editTextSearch.removeCallbacks(searchRunnable);
                binding.editTextSearch.postDelayed(searchRunnable, 300);
            }
            
            @Override
            public void afterTextChanged(Editable s) {}
        };
        
        binding.editTextSearch.addTextChangedListener(searchWatcher);
    }
    
    private final Runnable searchRunnable = new Runnable() {
        @Override
        public void run() {
            String query = binding.editTextSearch.getText().toString().trim();
            viewModel.searchContacts(query);
        }
    };
    
    private void setupClickListeners() {
        binding.buttonOpenContacts.setOnClickListener(v -> openNativeContacts());
        
        binding.buttonClear.setOnClickListener(v -> {
            binding.editTextSearch.setText("");
            binding.editTextSearch.requestFocus();
        });
        
        binding.buttonClose.setOnClickListener(v -> dismiss());
    }
    
    private void observeViewModel() {
        viewModel.getContacts().observe(getViewLifecycleOwner(), contacts -> {
            if (contacts != null) {
                contactAdapter.submitList(contacts);
                binding.textViewEmpty.setVisibility(contacts.isEmpty() ? View.VISIBLE : View.GONE);
                binding.recyclerViewContacts.setVisibility(contacts.isEmpty() ? View.GONE : View.VISIBLE);
            }
        });
        
        viewModel.getIsLoading().observe(getViewLifecycleOwner(), isLoading -> {
            binding.progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        });
        
        viewModel.getError().observe(getViewLifecycleOwner(), error -> {
            if (error != null && !error.isEmpty()) {
                Toast.makeText(getContext(), error, Toast.LENGTH_SHORT).show();
            }
        });
    }
    
    private void openNativeContacts() {
        try {
            Intent intent = new Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI);
            startActivityForResult(intent, 1001);
        } catch (Exception e) {
            Toast.makeText(getContext(), "Unable to open contacts", Toast.LENGTH_SHORT).show();
        }
    }
    
    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        
        if (requestCode == 1001 && resultCode == android.app.Activity.RESULT_OK && data != null) {
            // Handle native contact picker result
            handleNativeContactResult(data);
        }
    }
    
    private void handleNativeContactResult(Intent data) {
        // This would handle the native contact picker result
        // For now, just dismiss the dialog
        dismiss();
    }
    
    @Override
    public void onDestroyView() {
        super.onDestroyView();
        
        // Clean up TextWatcher
        if (binding != null && searchWatcher != null) {
            binding.editTextSearch.removeTextChangedListener(searchWatcher);
        }
        
        // Clear callbacks
        if (binding != null) {
            binding.editTextSearch.removeCallbacks(searchRunnable);
        }
        
        binding = null;
    }
}
