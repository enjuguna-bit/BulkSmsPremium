package com.bulksms.smsmanager.ui.inbox;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import androidx.appcompat.widget.SearchView;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.paging.LoadState;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.bulksms.smsmanager.R;
import com.bulksms.smsmanager.data.entity.ConversationEntity;
import com.bulksms.smsmanager.data.contacts.ContactResolver;
import com.bulksms.smsmanager.databinding.FragmentInboxBinding;
import com.google.android.material.chip.Chip;
import dagger.hilt.android.AndroidEntryPoint;
import java.util.HashMap;
import java.util.Map;
import javax.inject.Inject;

/**
 * Optimized Inbox Fragment using coroutines and background operations
 * Shows cached messages immediately while syncing in background
 */
// @AndroidEntryPoint
public class OptimizedInboxFragment extends Fragment {
    
    private FragmentInboxBinding binding;
    private OptimizedInboxViewModel viewModel;
    
    private ConversationPagingAdapter adapter;
    private ConversationLoadStateAdapter headerLoadStateAdapter;
    private ConversationLoadStateAdapter footerLoadStateAdapter;
    
    @Inject
    ContactResolver contactResolver;
    
    private InboxAnalytics analytics;
    
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentInboxBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }
    
    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        // Check SMS permissions first
        if (!checkSmsPermissions()) {
            return;
        }
        
        viewModel = new ViewModelProvider(this).get(OptimizedInboxViewModel.class);
        analytics = new InboxAnalytics(requireContext());
        
        setupRecyclerView();
        setupSearch();
        setupFilters();
        setupSwipeRefresh();
        observeData();
        observeState();
        
        // Messages load automatically from cache via ViewModel
        // No blocking operations on main thread
        
        Log.d("OptimizedInboxFragment", "Fragment setup completed - cached messages loading immediately");
    }
    
    private void setupRecyclerView() {
        // Create adapter with contact resolver
        adapter = new ConversationPagingAdapter(
            conversation -> openConversation(conversation),
            conversation -> {
                showConversationOptions(conversation);
                return true;
            },
            contactResolver
        );
        
        // Create load state adapters for header and footer
        headerLoadStateAdapter = new ConversationLoadStateAdapter(v -> adapter.retry());
        footerLoadStateAdapter = new ConversationLoadStateAdapter(v -> adapter.retry());
        
        // Setup RecyclerView
        binding.recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        
        // Add divider
        binding.recyclerView.addItemDecoration(
            new DividerItemDecoration(requireContext(), DividerItemDecoration.VERTICAL)
        );
        
        // Set adapter with load state header and footer
        binding.recyclerView.setAdapter(
            adapter.withLoadStateHeaderAndFooter(headerLoadStateAdapter, footerLoadStateAdapter)
        );
        
        // Optimize
        binding.recyclerView.setHasFixedSize(true);
        binding.recyclerView.setItemAnimator(null);
        
        // Handle load states (refresh / prepend / append)
        adapter.addLoadStateListener(loadStates -> {
            long startTime = System.currentTimeMillis();
            
            // CombinedLoadStates: handle refresh loading specially (initial load / swipe to refresh)
            boolean isRefreshing = loadStates.getRefresh() instanceof LoadState.Loading;
            binding.progressBar.setVisibility(isRefreshing ? View.VISIBLE : View.GONE);
            binding.swipeRefresh.setRefreshing(isRefreshing);
            
            // Show error state on refresh
            LoadState refresh = loadStates.getRefresh();
            if (refresh instanceof LoadState.Error) {
                Throwable throwable = ((LoadState.Error) refresh).getError();
                analytics.trackLoadStateError(
                    InboxAnalytics.LoadStateType.REFRESH, 
                    (LoadState.Error) refresh, 
                    "Initial load or swipe refresh"
                );
                String userMessage = analytics.getUserFriendlyErrorMessage(throwable);
                Toast.makeText(requireContext(), userMessage, Toast.LENGTH_LONG).show();
            } else if (refresh instanceof LoadState.NotLoading) {
                analytics.trackLoadStateSuccess(
                    InboxAnalytics.LoadStateType.REFRESH, 
                    adapter.getItemCount()
                );
            }
            
            // Handle prepend errors (loading at top)
            LoadState prepend = loadStates.getPrepend();
            if (prepend instanceof LoadState.Error) {
                analytics.trackLoadStateError(
                    InboxAnalytics.LoadStateType.PREPEND, 
                    (LoadState.Error) prepend, 
                    "Loading newer conversations"
                );
            }
            
            // Handle append errors (loading at bottom)
            LoadState append = loadStates.getAppend();
            if (append instanceof LoadState.Error) {
                analytics.trackLoadStateError(
                    InboxAnalytics.LoadStateType.APPEND, 
                    (LoadState.Error) append, 
                    "Loading older conversations"
                );
            }
            
            // Show empty state when not loading and adapter has zero items
            boolean isEmpty = refresh instanceof LoadState.NotLoading && adapter.getItemCount() == 0;
            binding.emptyView.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
            
            // Track performance
            long duration = System.currentTimeMillis() - startTime;
            analytics.trackPerformance("load_state_update", duration);
            
            // Paging uses Kotlin functions; must return Unit
            return kotlin.Unit.INSTANCE;
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
            viewModel.setFilter(OptimizedInboxViewModel.FilterType.ALL);
            updateFilterChips(binding.chipAll);
        });
        
        binding.chipInbox.setOnClickListener(v -> {
            viewModel.setFilter(OptimizedInboxViewModel.FilterType.INBOX);
            updateFilterChips(binding.chipInbox);
        });
        
        binding.chipSent.setOnClickListener(v -> {
            viewModel.setFilter(OptimizedInboxViewModel.FilterType.SENT);
            updateFilterChips(binding.chipSent);
        });
        
        binding.chipUnread.setOnClickListener(v -> {
            viewModel.setFilter(OptimizedInboxViewModel.FilterType.UNREAD);
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
        Log.d("OptimizedInboxFragment", "Setting up data observers");
        
        // Observe conversations using Flow
        viewModel.conversations
            .asLiveData()
            .observe(getViewLifecycleOwner(), pagingData -> {
                Log.d("OptimizedInboxFragment", "Conversations observer triggered - PagingData: " + (pagingData != null ? "non-null" : "null"));
                if (pagingData != null) {
                    Log.d("OptimizedInboxFragment", "Submitting PagingData to adapter");
                    adapter.submitData(getLifecycle(), pagingData);
                } else {
                    Log.w("OptimizedInboxFragment", "Received null PagingData");
                }
            });
        
        // Observe statistics using LiveData
        viewModel.unreadCount.observe(getViewLifecycleOwner(), count -> {
            Log.d("OptimizedInboxFragment", "Unread count updated: " + count);
            if (binding != null && binding.tvUnreadCount != null) {
                binding.tvUnreadCount.setText("Unread: " + count);
                updateUnreadBadge(count);
            }
        });
        
        viewModel.totalCount.observe(getViewLifecycleOwner(), count -> {
            Log.d("OptimizedInboxFragment", "Total count updated: " + count);
            binding.tvTotalCount.setText("Total: " + count);
        });
    }
    
    private void observeState() {
        // Observe UI state using StateFlow
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
        
        // Observe error state
        viewModel.errorState.observe(getViewLifecycleOwner(), error -> {
            if (error != null) {
                showError(error);
            }
        });
    }
    
    private void openConversation(ConversationEntity conversation) {
        long startTime = System.currentTimeMillis();
        
        try {
            // Validate conversation data
            if (conversation == null) {
                String error = "Conversation is null";
                analytics.trackConversationOpenError(null, new IllegalArgumentException(error));
                Toast.makeText(requireContext(), "Error: " + error, Toast.LENGTH_SHORT).show();
                return;
            }
            
            // Get phone number
            String phoneNumber = conversation.phoneNumber;
            if (phoneNumber == null || phoneNumber.trim().isEmpty()) {
                String error = "Invalid phone number";
                analytics.trackConversationOpenError(conversation, new IllegalArgumentException(error));
                Toast.makeText(requireContext(), "Error: " + error, Toast.LENGTH_SHORT).show();
                return;
            }
            
            // Mark conversation as read
            viewModel.markAsRead(conversation.id);
            
            // Navigate to conversation
            NavController navController = Navigation.findNavController(requireView());
            Bundle args = new Bundle();
            args.putString("phone_number", phoneNumber.trim());
            navController.navigate(R.id.nav_conversation, args);
            
            // Track successful conversation open
            Map<String, String> params = new HashMap<>();
            params.put("conversation_id", String.valueOf(conversation.id));
            params.put("phone_number", phoneNumber);
            analytics.trackUserInteraction("conversation_opened", params);
            
            // Track performance
            long duration = System.currentTimeMillis() - startTime;
            analytics.trackPerformance("conversation_open", duration);
            
        } catch (Exception e) {
            analytics.trackConversationOpenError(conversation, e);
            String userMessage = analytics.getUserFriendlyErrorMessage(e);
            Toast.makeText(requireContext(), "Navigation error: " + userMessage, Toast.LENGTH_LONG).show();
        }
    }
    
    private void showConversationOptions(ConversationEntity conversation) {
        String[] options = {"Delete conversation", "Mark as unread", "Archive conversation", "Pin/Unpin"};
        
        new android.app.AlertDialog.Builder(requireContext())
            .setTitle("Conversation Options")
            .setItems(options, (dialog, which) -> {
                switch (which) {
                    case 0:
                        deleteConversation(conversation);
                        break;
                    case 1:
                        markAsUnread(conversation);
                        break;
                    case 2:
                        archiveConversation(conversation);
                        break;
                    case 3:
                        togglePinStatus(conversation);
                        break;
                }
            })
            .show();
    }
    
    private void deleteConversation(ConversationEntity conversation) {
        new android.app.AlertDialog.Builder(requireContext())
            .setTitle("Delete Conversation")
            .setMessage("Are you sure you want to delete this conversation and all its messages?")
            .setPositiveButton("Delete", (dialog, which) -> viewModel.deleteConversation(conversation))
            .setNegativeButton("Cancel", null)
            .show();
    }
    
    private void markAsUnread(ConversationEntity conversation) {
        viewModel.markAsUnread(conversation.id);
        Toast.makeText(requireContext(), "Marked as unread", Toast.LENGTH_SHORT).show();
    }
    
    private void archiveConversation(ConversationEntity conversation) {
        // Implementation for archiving conversation
        Toast.makeText(requireContext(), "Archive functionality - TODO", Toast.LENGTH_SHORT).show();
    }
    
    private void togglePinStatus(ConversationEntity conversation) {
        // Implementation for pinning/unpinning conversation
        Toast.makeText(requireContext(), "Pin functionality - TODO", Toast.LENGTH_SHORT).show();
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
    
    private boolean checkSmsPermissions() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_SMS) 
            != PackageManager.PERMISSION_GRANTED) {
            
            Toast.makeText(requireContext(), "SMS permission required to view messages", Toast.LENGTH_LONG).show();
            
            // Request permissions
            requestPermissions(new String[]{Manifest.permission.READ_SMS}, 1003);
            return false;
        }
        return true;
    }
    
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        
        if (requestCode == 1003) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted, retry setup
                if (viewModel == null) {
                    viewModel = new ViewModelProvider(this).get(OptimizedInboxViewModel.class);
                    setupRecyclerView();
                    setupSearch();
                    setupFilters();
                    setupSwipeRefresh();
                    observeData();
                    observeState();
                }
            } else {
                Toast.makeText(requireContext(), "SMS permission denied. Cannot view messages.", Toast.LENGTH_LONG).show();
            }
        }
    }
    
    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // Clean up adapter and view references to avoid leaks
        if (binding != null) {
            binding.recyclerView.setAdapter(null);
        }
        if (adapter != null) {
            adapter.cleanup();
        }
        adapter = null;
        headerLoadStateAdapter = null;
        footerLoadStateAdapter = null;
        binding = null;
    }
}
