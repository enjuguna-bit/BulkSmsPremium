package com.bulksms.smsmanager.ui.sms;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import android.content.BroadcastReceiver;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import com.bulksms.smsmanager.R;
import com.bulksms.smsmanager.databinding.FragmentSmsSendBinding;
import dagger.hilt.android.AndroidEntryPoint;
import com.bulksms.smsmanager.ui.sms.SingleSmsViewModel;
import com.bulksms.smsmanager.ui.contacts.ContactSearchDialog;

/**
 * Single SMS Fragment for sending individual messages
 * Uses Hilt for dependency injection
 */
@AndroidEntryPoint
public class SingleSmsFragment extends Fragment {
    
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
            }
        );
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
    }
    
    private void setupMessageWatcher() {
        messageTextWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                int length = s.length();
                binding.txtCharCount.setText(length + " / 160 characters");
                
                // Show warning if message is long
                if (length > 160) {
                    int parts = (int) Math.ceil(length / 153.0);
                    binding.txtCharCount.setText(length + " / 160 characters (" + parts + " SMS)");
                    binding.txtCharCount.setTextColor(
                        ContextCompat.getColor(requireContext(), android.R.color.holo_orange_dark)
                    );
                } else {
                    binding.txtCharCount.setTextColor(
                        ContextCompat.getColor(requireContext(), android.R.color.darker_gray)
                    );
                }
            }
            
            @Override
            public void afterTextChanged(Editable s) {}
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
                binding.txtStatus.setVisibility(View.VISIBLE);
            } else {
                binding.txtStatus.setVisibility(View.GONE);
            }
        });
        
        viewModel.getError().observe(getViewLifecycleOwner(), error -> {
            if (error != null && !error.isEmpty()) {
                Toast.makeText(requireContext(), "Error: " + error, Toast.LENGTH_LONG).show();
            }
        });
    }
    
    private void selectContact() {
        Intent intent = new Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI);
        contactPickerLauncher.launch(intent);
    }
    
    private void handleContactSelection(Intent data) {
        Uri contactUri = data.getData();
        if (contactUri != null) {
            android.database.Cursor cursor = requireContext().getContentResolver().query(
                contactUri,
                new String[]{ContactsContract.CommonDataKinds.Phone.NUMBER},
                null, null, null
            );
            
            if (cursor != null && cursor.moveToFirst()) {
                int numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER);
                if (numberIndex >= 0) {
                    String phoneNumber = cursor.getString(numberIndex);
                    binding.inputPhoneNumber.setText(phoneNumber);
                }
                cursor.close();
            }
        }
    }
    
    private void sendSms() {
        String phoneNumber = binding.inputPhoneNumber.getText().toString().trim();
        String message = binding.inputMessage.getText().toString().trim();
        int simSlot = binding.radioSim1.isChecked() ? 0 : 1;
        
        // Validation
        if (phoneNumber.isEmpty()) {
            Toast.makeText(requireContext(), "Please enter a phone number", Toast.LENGTH_SHORT).show();
            return;
        }
        
        if (message.isEmpty()) {
            Toast.makeText(requireContext(), "Please enter a message", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Check permissions
        if (!hasSmsPermissions()) {
            requestPermissions();
            return;
        }
        
        // Send SMS via ViewModel
        viewModel.sendSms(phoneNumber, message, simSlot);
    }
    
    private void requestPermissions() {
        String[] permissions = {
            Manifest.permission.SEND_SMS,
            Manifest.permission.READ_SMS,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.READ_PHONE_STATE  // Add this for SIM selection
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
            requestPermissions(permissions, 1002);
        }
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
        return ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.SEND_SMS) 
               == PackageManager.PERMISSION_GRANTED;
    }
    
    @Override
    public void onDestroyView() {
        super.onDestroyView();
        
        // Clean up TextWatcher to prevent memory leaks
        if (binding != null && messageTextWatcher != null) {
            binding.inputMessage.removeTextChangedListener(messageTextWatcher);
        }
        
        // Clear ViewModel observers
        if (viewModel != null) {
            viewModel.getIsLoading().removeObservers(getViewLifecycleOwner());
            viewModel.getStatus().removeObservers(getViewLifecycleOwner());
            viewModel.getError().removeObservers(getViewLifecycleOwner());
        }
        
        // Clear binding reference
        binding = null;
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        
        // Cancel contact picker if still active
        if (contactPickerLauncher != null) {
            // No direct way to cancel ActivityResultLauncher
            // This is handled automatically by the system
        }
    }
}
