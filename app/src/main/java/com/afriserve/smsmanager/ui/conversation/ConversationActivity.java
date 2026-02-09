package com.afriserve.smsmanager.ui.conversation;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.afriserve.smsmanager.R;
import com.afriserve.smsmanager.databinding.ActivityConversationBinding;

import dagger.hilt.android.AndroidEntryPoint;

/**
 * Activity for displaying SMS conversation
 */
@AndroidEntryPoint
public class ConversationActivity extends AppCompatActivity {
    
    private ActivityConversationBinding binding;
    private ConversationViewModel viewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityConversationBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        
        viewModel = new ViewModelProvider(this).get(ConversationViewModel.class);
        
        setupToolbar();
        setupFragment();
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
        // Get arguments from intent
        String address = getIntent().getStringExtra("address");
        String threadId = getIntent().getStringExtra("threadId");
        long threadIdLong = getIntent().getLongExtra("thread_id", -1L);
        
        // Create fragment with arguments
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
        
        // Add fragment to the container
        if (getSupportFragmentManager().findFragmentById(R.id.fragment_container) == null) {
            getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .commit();
        }
    }
}
