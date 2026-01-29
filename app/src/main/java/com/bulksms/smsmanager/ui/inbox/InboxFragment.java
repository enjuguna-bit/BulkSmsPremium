package com.bulksms.smsmanager.ui.inbox;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import androidx.appcompat.widget.SearchView;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.paging.LoadState;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.bulksms.smsmanager.R;
import com.bulksms.smsmanager.data.entity.SmsEntity;
import com.bulksms.smsmanager.data.contacts.ContactResolver;
import com.bulksms.smsmanager.databinding.FragmentInboxBinding;
import com.google.android.material.chip.Chip;
import dagger.hilt.android.AndroidEntryPoint;
import javax.inject.Inject;

/**
 * Premium Inbox Fragment with advanced features
 */
@AndroidEntryPoint
public class InboxFragment extends Fragment {
    
    private FragmentInboxBinding binding;
    private SimpleInboxViewModel viewModel;
    
    private InboxPagingAdapter adapter;
    private MessageLoadStateAdapter loadStateAdapter;
    
    @Inject
    ContactResolver contactResolver;
    
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentInboxBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }
    
    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        viewModel = new ViewModelProvider(this).get(SimpleInboxViewModel.class);
        
        setupRecyclerView();
        setupSearch();
        setupFilters();
        setupSwipeRefresh();
        observeData();
        observeState();
    }
    
    private void setupRecyclerView() {
        // Create adapter with contact resolver
        adapter = new InboxPagingAdapter(
            message -> openMessage(message),
            message -> {
                showMessageOptions(message);
                return true;
            },
            contactResolver
        );
        
        // Create load state adapter for footer
        loadStateAdapter = new MessageLoadStateAdapter(v -> adapter.retry());
        
        // Setup RecyclerView
        binding.recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        
        // Add divider
        binding.recyclerView.addItemDecoration(
            new DividerItemDecoration(requireContext(), DividerItemDecoration.VERTICAL)
        );
        
        // Set adapter with load state footer
        binding.recyclerView.setAdapter(
            adapter.withLoadStateFooter(loadStateAdapter)
        );
        
        // Optimize
        binding.recyclerView.setHasFixedSize(true);
        binding.recyclerView.setItemAnimator(null);
        
        // Handle load states
        adapter.addLoadStateListener(loadState -> {
            // Show loading spinner only on initial load
            binding.progressBar.setVisibility(
                loadState.getRefresh() instanceof LoadState.Loading ? View.VISIBLE : View.GONE
            );
            
            // Show empty state
            boolean isEmpty = loadState.getRefresh() instanceof LoadState.NotLoading && 
                            adapter.getItemCount() == 0;
            binding.emptyView.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
            
            return null; // Return null for LoadStateListener
        });
    }
    
    private void setupSearch() {
        binding.searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                viewModel.search(query);
                return true;
            }
            
            @Override
            public boolean onQueryTextChange(String newText) {
                if (newText.isEmpty()) {
                    viewModel.clearSearch();
                } else if (newText.length() >= 2) {
                    viewModel.search(newText);
                }
                return true;
            }
        });
    }
    
    private void setupFilters() {
        binding.chipAll.setOnClickListener(v -> {
            viewModel.setFilter(SimpleInboxViewModel.FilterType.ALL);
            updateFilterChips(binding.chipAll);
        });
        
        binding.chipInbox.setOnClickListener(v -> {
            viewModel.setFilter(SimpleInboxViewModel.FilterType.INBOX);
            updateFilterChips(binding.chipInbox);
        });
        
        binding.chipSent.setOnClickListener(v -> {
            viewModel.setFilter(SimpleInboxViewModel.FilterType.SENT);
            updateFilterChips(binding.chipSent);
        });
        
        binding.chipUnread.setOnClickListener(v -> {
            viewModel.setFilter(SimpleInboxViewModel.FilterType.UNREAD);
            updateFilterChips(binding.chipUnread);
        });
    }
    
    private void updateFilterChips(Chip selectedChip) {
        binding.chipAll.setChecked(selectedChip == binding.chipAll);
        binding.chipInbox.setChecked(selectedChip == binding.chipInbox);
        binding.chipSent.setChecked(selectedChip == binding.chipSent);
        binding.chipUnread.setChecked(selectedChip == binding.chipUnread);
    }
    
    private void setupSwipeRefresh() {
        binding.swipeRefresh.setOnRefreshListener(() -> viewModel.syncMessages());
    }
    
    private void observeData() {
        // Observe messages using LiveData
        viewModel.messages.observe(getViewLifecycleOwner(), pagingData -> {
            if (pagingData != null) {
                adapter.submitData(getLifecycle(), pagingData);
            }
        });
        
        // Observe statistics using LiveData
        viewModel.unreadCount.observe(getViewLifecycleOwner(), count -> {
            if (binding != null && binding.tvUnreadCount != null) {
                binding.tvUnreadCount.setText("Unread: " + count);
                updateUnreadBadge(count);
            }
        });
        
        viewModel.totalCount.observe(getViewLifecycleOwner(), count -> {
            binding.tvTotalCount.setText("Total: " + count);
        });
    }
    
    private void observeState() {
        // Observe UI state using LiveData
        viewModel.uiState.observe(getViewLifecycleOwner(), state -> {
            switch (state.type) {
                case LOADING:
                    // Initial loading handled by load state
                    break;
                case SYNCING:
                    binding.swipeRefresh.setRefreshing(true);
                    break;
                case SUCCESS:
                    binding.swipeRefresh.setRefreshing(false);
                    showSuccess(state.message);
                    break;
                case ERROR:
                    binding.swipeRefresh.setRefreshing(false);
                    showError(state.message);
                    break;
                case STATS_LOADED:
                    binding.swipeRefresh.setRefreshing(false);
                    if (state.stats != null) {
                        updateStatistics(state.stats);
                    }
                    break;
            }
        });
    }
    
    private void openMessage(SmsEntity message) {
        // Validate message data
        if (message == null) {
            Toast.makeText(requireContext(), "Error: Message is null", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Get phone number (try both phoneNumber and address)
        String phoneNumber = message.phoneNumber;
        if (phoneNumber == null || phoneNumber.trim().isEmpty()) {
            phoneNumber = message.getAddress();
        }
        
        if (phoneNumber == null || phoneNumber.trim().isEmpty()) {
            Toast.makeText(requireContext(), "Error: Invalid phone number", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Mark as read
        viewModel.markAsRead(message.id);
        
        // Navigate to conversation
        try {
            NavController navController = Navigation.findNavController(requireView());
            Bundle args = new Bundle();
            args.putString("phone_number", phoneNumber.trim());
            navController.navigate(R.id.nav_conversation, args);
        } catch (Exception e) {
            Toast.makeText(requireContext(), "Navigation error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
    
    private void showMessageOptions(SmsEntity message) {
        String[] options = {"Delete", "Mark as unread", "Copy text", "Forward"};
        
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
                    case 3:
                        forwardMessage(message);
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
        viewModel.markAsUnread(message.id);
        Toast.makeText(requireContext(), "Marked as unread", Toast.LENGTH_SHORT).show();
    }
    
    private void copyText(SmsEntity message) {
        android.content.ClipboardManager clipboard = 
            (android.content.ClipboardManager) requireContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE);
        android.content.ClipData clip = android.content.ClipData.newPlainText("SMS", message.message);
        clipboard.setPrimaryClip(clip);
        Toast.makeText(requireContext(), "Message copied", Toast.LENGTH_SHORT).show();
    }
    
    private void forwardMessage(SmsEntity message) {
        // Implementation for forwarding message
        Toast.makeText(requireContext(), "Forward functionality - TODO", Toast.LENGTH_SHORT).show();
    }
    
    private void updateUnreadBadge(int count) {
        binding.chipUnread.setText("Unread (" + count + ")");
    }
    
    private void updateStatistics(MessageStatistics stats) {
        binding.tvTotalCount.setText("Total: " + stats.total);
        binding.tvUnreadCount.setText("Unread: " + stats.unread);
    }
    
    private void showSuccess(String message) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
    }
    
    private void showError(String message) {
        Toast.makeText(requireContext(), "Error: " + message, Toast.LENGTH_LONG).show();
    }
    
    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // Clean up adapter disposables
        if (adapter != null) {
            adapter.cleanup();
        }
        binding = null;
    }
}
