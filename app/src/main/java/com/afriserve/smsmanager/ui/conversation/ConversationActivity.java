package com.afriserve.smsmanager.ui.conversation;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.afriserve.smsmanager.R;
import com.afriserve.smsmanager.databinding.ActivityConversationBinding;
import com.google.android.material.button.MaterialButton;

import dagger.hilt.android.AndroidEntryPoint;

/**
 * Activity for displaying SMS conversation.
 * Includes a direct Call button to dial the contact from the inbox.
 */
@AndroidEntryPoint
public class ConversationActivity extends AppCompatActivity {
    
    private ActivityConversationBinding binding;
    private ConversationViewModel viewModel;
    private String contactAddress;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityConversationBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        
        viewModel = new ViewModelProvider(this).get(ConversationViewModel.class);
        
        setupToolbar();
        setupFragment();
        setupCallButton();
    }
    
    private void setupToolbar() {
        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Conversation");
        }
        binding.toolbar.setNavigationOnClickListener(v -> finish());
    }
    
    private void setupFragment() {
        String address = getIntent().getStringExtra("address");
        String threadId = getIntent().getStringExtra("threadId");
        long threadIdLong = getIntent().getLongExtra("thread_id", -1L);

        // Store address for the call button
        contactAddress = address;

        // Update toolbar title to show contact name/number
        if (address != null && !address.isEmpty()) {
            if (getSupportActionBar() != null) {
                getSupportActionBar().setTitle(address);
            }
        }
        
        ConversationFragment fragment = new ConversationFragment();
        Bundle args = new Bundle();
        if (address != null) {
            args.putString("address", address);
        }
        if (threadIdLong > 0) {
            args.putLong("thread_id", threadIdLong);
        } else if (threadId != null) {
            try {
                long parsed = Long.parseLong(threadId);
                if (parsed > 0) {
                    args.putLong("thread_id", parsed);
                }
            } catch (NumberFormatException ignored) {
                args.putString("threadId", threadId);
            }
        }
        fragment.setArguments(args);
        
        if (getSupportFragmentManager().findFragmentById(R.id.fragment_container) == null) {
            getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .commit();
        }
    }

    private void setupCallButton() {
        MaterialButton btnCall = binding.getRoot().findViewById(R.id.btnCall);
        if (btnCall == null) return;

        if (contactAddress == null || contactAddress.trim().isEmpty()) {
            btnCall.setVisibility(View.GONE);
            return;
        }

        btnCall.setVisibility(View.VISIBLE);
        btnCall.setOnClickListener(v -> initiateCall(contactAddress));
    }

    private void initiateCall(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.trim().isEmpty()) {
            Toast.makeText(this, "No phone number available", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            String cleanNumber = phoneNumber.trim();
            Intent callIntent = new Intent(Intent.ACTION_DIAL);
            callIntent.setData(Uri.parse("tel:" + Uri.encode(cleanNumber)));
            startActivity(callIntent);
        } catch (Exception e) {
            Toast.makeText(this, "Unable to open dialler", Toast.LENGTH_SHORT).show();
        }
    }
}
