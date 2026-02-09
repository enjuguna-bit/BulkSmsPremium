package com.afriserve.smsmanager.ui.inbox;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.afriserve.smsmanager.R;
import com.afriserve.smsmanager.databinding.ActivityInboxBinding;

import dagger.hilt.android.AndroidEntryPoint;

/**
 * Activity for displaying SMS inbox
 */
@AndroidEntryPoint
public class InboxActivity extends AppCompatActivity {
    
    private ActivityInboxBinding binding;
    private InboxViewModel viewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityInboxBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        
        viewModel = new ViewModelProvider(this).get(InboxViewModel.class);
        
        setupToolbar();
        setupFragment(savedInstanceState);
    }
    
    private void setupToolbar() {
        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Inbox");
        }
        
        binding.toolbar.setNavigationOnClickListener(v -> finish());
    }
    
    private void setupFragment(@androidx.annotation.Nullable Bundle savedInstanceState) {
        // Add InboxFragment to the container
        if (savedInstanceState == null) {
            getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, new InboxFragment())
                .commit();
        }
    }
}
