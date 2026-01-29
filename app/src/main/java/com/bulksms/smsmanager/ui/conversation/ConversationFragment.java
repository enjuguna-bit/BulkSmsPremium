package com.bulksms.smsmanager.ui.conversation;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.bulksms.smsmanager.R;
import com.bulksms.smsmanager.data.entity.SmsEntity;
import com.bulksms.smsmanager.data.entity.ConversationEntity;
import com.bulksms.smsmanager.databinding.FragmentConversationBinding;
import com.google.android.material.snackbar.Snackbar;
import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class ConversationFragment extends Fragment {
    private static final String TAG = "ConversationFragment";
    
    private FragmentConversationBinding binding;
    private ConversationViewModel viewModel;
    private ConversationAdapter adapter;
    private String phoneNumber;
    
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentConversationBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }
    
    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        viewModel = new ViewModelProvider(this).get(ConversationViewModel.class);
        
        // Get phone number from arguments
        if (getArguments() != null) {
            phoneNumber = getArguments().getString("phone_number");
        }
        
        if (phoneNumber == null) {
            showError("No phone number provided");
            return;
        }
        
        setupToolbar();
        setupRecyclerView();
        setupMessageInput();
        observeViewModel();
        
        // Load conversation
        viewModel.loadConversation(phoneNumber);
    }
    
    private void setupToolbar() {
        binding.toolbar.setNavigationOnClickListener(v -> {
            requireActivity().getSupportFragmentManager().popBackStack();
        });
    }
    
    private void setupRecyclerView() {
        adapter = new ConversationAdapter(
            message -> showMessageOptions(message),
            requireContext()
        );
        
        binding.recyclerViewMessages.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.recyclerViewMessages.setAdapter(adapter);
        
        // Scroll to bottom when new messages are added
        adapter.registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() {
            @Override
            public void onItemRangeInserted(int positionStart, int itemCount) {
                if (binding.recyclerViewMessages != null && adapter.getItemCount() > 0) {
                    binding.recyclerViewMessages.post(() -> {
                        try {
                            binding.recyclerViewMessages.smoothScrollToPosition(adapter.getItemCount() - 1);
                        } catch (Exception e) {
                            Log.e(TAG, "Error scrolling to position", e);
                        }
                    });
                }
            }
        });
    }
    
    private void setupMessageInput() {
        binding.buttonSend.setOnClickListener(v -> {
            String message = binding.editTextMessage.getText().toString().trim();
            if (!message.isEmpty()) {
                viewModel.sendMessage(message);
                binding.editTextMessage.setText("");
            }
        });
    }
    
    private void observeViewModel() {
        // Observe conversation
        viewModel.conversation.observe(getViewLifecycleOwner(), conversation -> {
            if (conversation != null) {
                updateToolbar(conversation);
            }
        });
        
        // Observe messages
        viewModel.messages.observe(getViewLifecycleOwner(), messages -> {
            if (messages != null && binding != null) {
                adapter.submitList(messages);
                if (binding.textEmpty != null && binding.recyclerViewMessages != null) {
                    binding.textEmpty.setVisibility(messages.isEmpty() ? View.VISIBLE : View.GONE);
                    binding.recyclerViewMessages.setVisibility(messages.isEmpty() ? View.GONE : View.VISIBLE);
                }
            }
        });
        
        // Observe UI state
        viewModel.uiState.observe(getViewLifecycleOwner(), state -> {
            if (state == null) return;
            
            switch (state) {
                case LOADING:
                    binding.progressBar.setVisibility(View.VISIBLE);
                    break;
                case SENDING:
                    binding.progressBar.setVisibility(View.VISIBLE);
                    binding.buttonSend.setEnabled(false);
                    break;
                case SUCCESS:
                    binding.progressBar.setVisibility(View.GONE);
                    binding.buttonSend.setEnabled(true);
                    if (state.getMessage() != null) {
                        showSuccess(state.getMessage());
                    }
                    break;
                case ERROR:
                    binding.progressBar.setVisibility(View.GONE);
                    binding.buttonSend.setEnabled(true);
                    if (state.getMessage() != null) {
                        showError(state.getMessage());
                    }
                    break;
            }
        });
    }
    
    private void updateToolbar(ConversationEntity conversation) {
        String title = conversation.contactName != null && !conversation.contactName.isEmpty() 
            ? conversation.contactName 
            : conversation.phoneNumber;
        binding.toolbar.setTitle(title);
    }
    
    private void showMessageOptions(SmsEntity message) {
        String[] options = {"Delete", "Mark as unread", "Copy text"};
        
        new android.app.AlertDialog.Builder(requireContext())
            .setTitle("Message Options")
            .setItems(options, (dialog, which) -> {
                switch (which) {
                    case 0:
                        deleteMessage(message);
                        break;
                    case 1:
                        markAsUnread(message);
                        break;
                    case 2:
                        copyText(message);
                        break;
                }
            })
            .show();
    }
    
    private void deleteMessage(SmsEntity message) {
        new android.app.AlertDialog.Builder(requireContext())
            .setTitle("Delete Message")
            .setMessage("Are you sure you want to delete this message?")
            .setPositiveButton("Delete", (dialog, which) -> viewModel.deleteMessage(message))
            .setNegativeButton("Cancel", null)
            .show();
    }
    
    private void markAsUnread(SmsEntity message) {
        viewModel.markAsUnread(message);
    }
    
    private void copyText(SmsEntity message) {
        android.content.ClipboardManager clipboard = 
            (android.content.ClipboardManager) requireContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE);
        android.content.ClipData clip = android.content.ClipData.newPlainText("SMS", message.message);
        clipboard.setPrimaryClip(clip);
        Toast.makeText(requireContext(), "Message copied", Toast.LENGTH_SHORT).show();
    }
    
    private void showSuccess(String message) {
        Snackbar.make(binding.getRoot(), message, Snackbar.LENGTH_SHORT).show();
    }
    
    private void showError(String message) {
        Snackbar.make(binding.getRoot(), "Error: " + message, Snackbar.LENGTH_LONG).show();
    }
    
    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
    
    // Factory method for creating instance with arguments
    public static ConversationFragment newInstance(String phoneNumber) {
        ConversationFragment fragment = new ConversationFragment();
        Bundle args = new Bundle();
        args.putString("phone_number", phoneNumber);
        fragment.setArguments(args);
        return fragment;
    }
}
