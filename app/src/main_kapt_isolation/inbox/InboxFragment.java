package com.afriserve.smsmanager.ui.inbox;

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
import com.afriserve.smsmanager.R;
import com.afriserve.smsmanager.data.entity.ConversationEntity;
import com.afriserve.smsmanager.data.contacts.ContactResolver;
import com.afriserve.smsmanager.databinding.FragmentInboxBinding;
import com.facebook.shimmer.ShimmerFrameLayout;
import com.google.android.material.chip.Chip;
import dagger.hilt.android.AndroidEntryPoint;
import java.util.HashMap;
import java.util.Map;
import javax.inject.Inject;

/**
 * Premium Inbox Fragment with advanced features
 */
// @AndroidEntryPoint
public class InboxFragment extends Fragment {
    
    private FragmentInboxBinding binding;
    private SimpleInboxViewModel viewModel;
    
    private ConversationPagingAdapter adapter;
    private ConversationLoadStateAdapter headerLoadStateAdapter;
    private ConversationLoadStateAdapter footerLoadStateAdapter;
    private ShimmerFrameLayout shimmerContainer;
    
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
        
        viewModel = new ViewModelProvider(this).get(SimpleInboxViewModel.class);
        analytics = new InboxAnalytics(requireContext());
        
        // Initialize shimmer container
        shimmerContainer = binding.shimmerContainer;
        
        setupRecyclerView();
        setupSearch();
        setupFilters();
        setupSwipeRefresh();
        observeData();
        observeState();
        
        // Show shimmer loading immediately for better UX
        showShimmerLoading(true);
        
        // Messages will load automatically from cache via ViewModel
        // No need to trigger manual sync here - it's handled in ViewModel background
        
        // Database debug is now handled in ViewModel background
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
            
            // Hide shimmer when data starts loading
            if (!isRefreshing) {
                showShimmerLoading(false);
            }
            
            binding.progressBar.setVisibility(isRefreshing ? View.GONE : View.GONE); // Hide progress bar, use shimmer instead
            binding.swipeRefresh.setRefreshing(isRefreshing);
            
            // Show error state on refresh
            LoadState refresh = loadStates.getRefresh();
            if (refresh instanceof LoadState.Error) {
                showShimmerLoading(false); // Hide shimmer on error
                Throwable throwable = ((LoadState.Error) refresh).getError();
                analytics.trackLoadStateError(
                    InboxAnalytics.LoadStateType.REFRESH, 
                    (LoadState.Error) refresh, 
                    "Initial load or swipe refresh"
                );
                String userMessage = analytics.getUserFriendlyErrorMessage(throwable);
                Toast.makeText(requireContext(), userMessage, Toast.LENGTH_LONG).show();
            } else if (refresh instanceof LoadState.NotLoading) {
                showShimmerLoading(false); // Hide shimmer when loaded
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
        try {
            if (binding != null && binding.searchView != null) {
                binding.searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
                    @Override
                    public boolean onQueryTextSubmit(String query) {
                        try {
                            if (viewModel != null) {
                                viewModel.search(query);
                            }
                        } catch (Exception e) {
                            Log.e("InboxFragment", "Error in search submit", e);
                        }
                        return true;
                    }
                    
                    @Override
                    public boolean onQueryTextChange(String newText) {
                        try {
                            if (viewModel != null) {
                                if (newText == null || newText.isEmpty()) {
                                    viewModel.clearSearch();
                                } else if (newText.length() >= 2) {
                                    viewModel.search(newText);
                                }
                            }
                        } catch (Exception e) {
                            Log.e("InboxFragment", "Error in search text change", e);
                        }
                        return true;
                    }
                });
            } else {
                Log.w("InboxFragment", "SearchView or binding is null");
            }
        } catch (Exception e) {
            Log.e("InboxFragment", "Error setting up search", e);
        }
    }
    
    private void setupFilters() {
        try {
            if (binding != null) {
                if (binding.chipAll != null) {
                    binding.chipAll.setOnClickListener(v -> {
                        try {
                            if (viewModel != null) {
                                viewModel.setFilter(SimpleInboxViewModel.FilterType.ALL);
                                updateFilterChips(binding.chipAll);
                            }
                        } catch (Exception e) {
                            Log.e("InboxFragment", "Error in chipAll click", e);
                        }
                    });
                }
                
                if (binding.chipInbox != null) {
                    binding.chipInbox.setOnClickListener(v -> {
                        try {
                            if (viewModel != null) {
                                viewModel.setFilter(SimpleInboxViewModel.FilterType.INBOX);
                                updateFilterChips(binding.chipInbox);
                            }
                        } catch (Exception e) {
                            Log.e("InboxFragment", "Error in chipInbox click", e);
                        }
                    });
                }
                
                if (binding.chipSent != null) {
                    binding.chipSent.setOnClickListener(v -> {
                        try {
                            if (viewModel != null) {
                                viewModel.setFilter(SimpleInboxViewModel.FilterType.SENT);
                                updateFilterChips(binding.chipSent);
                            }
                        } catch (Exception e) {
                            Log.e("InboxFragment", "Error in chipSent click", e);
                        }
                    });
                }
                
                if (binding.chipUnread != null) {
                    binding.chipUnread.setOnClickListener(v -> {
                        try {
                            if (viewModel != null) {
                                viewModel.setFilter(SimpleInboxViewModel.FilterType.UNREAD);
                                updateFilterChips(binding.chipUnread);
                            }
                        } catch (Exception e) {
                            Log.e("InboxFragment", "Error in chipUnread click", e);
                        }
                    });
                }
            } else {
                Log.w("InboxFragment", "Binding is null in setupFilters");
            }
        } catch (Exception e) {
            Log.e("InboxFragment", "Error setting up filters", e);
        }
    }
    
    private void updateFilterChips(Chip selectedChip) {
        try {
            if (binding != null) {
                if (binding.chipAll != null) binding.chipAll.setChecked(selectedChip == binding.chipAll);
                if (binding.chipInbox != null) binding.chipInbox.setChecked(selectedChip == binding.chipInbox);
                if (binding.chipSent != null) binding.chipSent.setChecked(selectedChip == binding.chipSent);
                if (binding.chipUnread != null) binding.chipUnread.setChecked(selectedChip == binding.chipUnread);
            }
        } catch (Exception e) {
            Log.e("InboxFragment", "Error updating filter chips", e);
        }
    }
    
    private void setupSwipeRefresh() {
        try {
            if (binding != null && binding.swipeRefresh != null) {
                binding.swipeRefresh.setOnRefreshListener(() -> {
                    try {
                        if (viewModel != null) {
                            viewModel.syncMessages();
                        }
                    } catch (Exception e) {
                        Log.e("InboxFragment", "Error in swipe refresh", e);
                        if (binding.swipeRefresh != null) {
                            binding.swipeRefresh.setRefreshing(false);
                        }
                    }
                });
            } else {
                Log.w("InboxFragment", "SwipeRefresh or binding is null");
            }
        } catch (Exception e) {
            Log.e("InboxFragment", "Error setting up swipe refresh", e);
        }
    }
    
    private void observeData() {
        Log.d("InboxFragment", "Setting up data observers");
        
        try {
            // Observe messages using LiveData with comprehensive null safety
            if (viewModel != null && viewModel.messages != null) {
                viewModel.messages.observe(getViewLifecycleOwner(), pagingData -> {
                    try {
                        Log.d("InboxFragment", "Messages observer triggered - PagingData: " + (pagingData != null ? "non-null" : "null"));
                        
                        // Validate fragment state before updating UI
                        if (!isAdded() || binding == null || adapter == null) {
                            Log.w("InboxFragment", "Fragment not properly attached, skipping UI update");
                            return;
                        }
                        
                        if (pagingData != null) {
                            Log.d("InboxFragment", "Submitting PagingData to adapter");
                            adapter.submitData(getLifecycle(), pagingData);
                        } else {
                            Log.w("InboxFragment", "Received null PagingData, showing empty state");
                            showEmptyState();
                        }
                    } catch (Exception e) {
                        Log.e("InboxFragment", "Error in messages observer", e);
                        if (isAdded() && getContext() != null) {
                            Toast.makeText(getContext(), "Error loading messages", Toast.LENGTH_SHORT).show();
                        }
                    }
                });
            } else {
                Log.w("InboxFragment", "ViewModel or messages LiveData is null");
            }
            
            // Observe statistics using LiveData with null safety
            if (viewModel != null && viewModel.unreadCount != null) {
                viewModel.unreadCount.observe(getViewLifecycleOwner(), count -> {
                    try {
                        Log.d("InboxFragment", "Unread count updated: " + count);
                        
                        if (binding != null && binding.tvUnreadCount != null && isAdded()) {
                            binding.tvUnreadCount.setText("Unread: " + (count != null ? count : 0));
                            updateUnreadBadge(count != null ? count : 0);
                        }
                    } catch (Exception e) {
                        Log.e("InboxFragment", "Error in unread count observer", e);
                    }
                });
            }
            
            // Observe total count with null safety
            if (viewModel != null && viewModel.totalCount != null) {
                viewModel.totalCount.observe(getViewLifecycleOwner(), count -> {
                    try {
                        Log.d("InboxFragment", "Total count updated: " + count);
                        
                        if (binding != null && binding.tvTotalCount != null && isAdded()) {
                            binding.tvTotalCount.setText("Total: " + (count != null ? count : 0));
                        }
                    } catch (Exception e) {
                        Log.e("InboxFragment", "Error in total count observer", e);
                    }
                });
            }
            
        } catch (Exception e) {
            Log.e("InboxFragment", "Error setting up data observers", e);
        }
    }
    
    private void observeState() {
        try {
            // Observe UI state using LiveData with null safety
            if (viewModel != null && viewModel.uiState != null) {
                viewModel.uiState.observe(getViewLifecycleOwner(), state -> {
                    try {
                        if (state == null) {
                            Log.w("InboxFragment", "Received null UI state");
                            return;
                        }
                        
                        // Validate fragment state before updating UI
                        if (!isAdded() || binding == null) {
                            Log.w("InboxFragment", "Fragment not properly attached, skipping UI state update");
                            return;
                        }
                        
                        switch (state.type) {
                            case LOADING:
                                // Initial loading handled by load state
                                break;
                            case SYNCING:
                                if (binding.swipeRefresh != null) {
                                    binding.swipeRefresh.setRefreshing(true);
                                }
                                break;
                            case SUCCESS:
                                if (binding.swipeRefresh != null) {
                                    binding.swipeRefresh.setRefreshing(false);
                                }
                                if (state.message != null && isAdded() && getContext() != null) {
                                    showSuccess(state.message);
                                }
                                break;
                            case ERROR:
                                if (binding.swipeRefresh != null) {
                                    binding.swipeRefresh.setRefreshing(false);
                                }
                                if (state.message != null && isAdded() && getContext() != null) {
                                    showError(state.message);
                                }
                                break;
                            case STATS_LOADED:
                                if (binding.swipeRefresh != null) {
                                    binding.swipeRefresh.setRefreshing(false);
                                }
                                if (state.stats != null) {
                                    updateStatistics(state.stats);
                                }
                                break;
                        }
                    } catch (Exception e) {
                        Log.e("InboxFragment", "Error in UI state observer", e);
                    }
                });
            } else {
                Log.w("InboxFragment", "ViewModel or uiState LiveData is null");
            }
            
            // Observe error state with null safety
            if (viewModel != null && viewModel.errorState != null) {
                viewModel.errorState.observe(getViewLifecycleOwner(), error -> {
                    try {
                        if (error != null && !error.trim().isEmpty() && isAdded() && getContext() != null) {
                            Log.e("InboxFragment", "Error state: " + error);
                            showError(error);
                        }
                    } catch (Exception e) {
                        Log.e("InboxFragment", "Error in error state observer", e);
                    }
                });
            }
            
        } catch (Exception e) {
            Log.e("InboxFragment", "Error setting up state observers", e);
        }
    }
    
    private void openConversation(ConversationEntity conversation) {
        long startTime = System.currentTimeMillis();
        
        try {
            // Validate fragment state
            if (!isAdded() || getContext() == null || getView() == null) {
                Log.w("InboxFragment", "Fragment not attached, skipping conversation open");
                return;
            }
            
            // Validate conversation data
            if (conversation == null) {
                String error = "Conversation is null";
                analytics.trackConversationOpenError(null, new IllegalArgumentException(error));
                if (isAdded() && getContext() != null) {
                    Toast.makeText(getContext(), "Error: " + error, Toast.LENGTH_SHORT).show();
                }
                return;
            }
            
            // Get phone number with validation
            String phoneNumber = conversation.phoneNumber;
            if (phoneNumber == null || phoneNumber.trim().isEmpty()) {
                String error = "Invalid phone number";
                analytics.trackConversationOpenError(conversation, new IllegalArgumentException(error));
                if (isAdded() && getContext() != null) {
                    Toast.makeText(getContext(), "Error: " + error, Toast.LENGTH_SHORT).show();
                }
                return;
            }
            
            // Mark conversation as read safely
            if (viewModel != null) {
                viewModel.markAsRead(conversation.id);
            }
            
            // Navigate safely with validation
            NavController navController = null;
            try {
                navController = Navigation.findNavController(requireView());
            } catch (Exception e) {
                Log.e("InboxFragment", "Failed to get NavController", e);
                if (isAdded() && getContext() != null) {
                    Toast.makeText(getContext(), "Navigation error: Cannot find navigation controller", Toast.LENGTH_LONG).show();
                }
                return;
            }
            
            if (navController != null) {
                Bundle args = new Bundle();
                args.putString("phone_number", phoneNumber.trim());
                
                // Validate navigation destination
                try {
                    navController.navigate(R.id.nav_conversation, args);
                } catch (Exception e) {
                    Log.e("InboxFragment", "Navigation failed", e);
                    analytics.trackConversationOpenError(conversation, e);
                    if (isAdded() && getContext() != null) {
                        String userMessage = analytics.getUserFriendlyErrorMessage(e);
                        Toast.makeText(getContext(), "Navigation error: " + userMessage, Toast.LENGTH_LONG).show();
                    }
                    return;
                }
            }
            
            // Track successful conversation open
            if (analytics != null) {
                Map<String, String> params = new HashMap<>();
                params.put("conversation_id", String.valueOf(conversation.id));
                params.put("phone_number", phoneNumber);
                analytics.trackUserInteraction("conversation_opened", params);
                
                // Track performance
                long duration = System.currentTimeMillis() - startTime;
                analytics.trackPerformance("conversation_open", duration);
            }
            
        } catch (Exception e) {
            Log.e("InboxFragment", "Unexpected error opening conversation", e);
            if (analytics != null) {
                analytics.trackConversationOpenError(conversation, e);
            }
            if (isAdded() && getContext() != null) {
                String userMessage = analytics != null ? analytics.getUserFriendlyErrorMessage(e) : "Unknown error";
                Toast.makeText(getContext(), "Navigation error: " + userMessage, Toast.LENGTH_LONG).show();
            }
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
        try {
            if (binding != null && binding.chipUnread != null) {
                binding.chipUnread.setText("Unread (" + count + ")");
            }
        } catch (Exception e) {
            Log.e("InboxFragment", "Error updating unread badge", e);
        }
    }
    
    private void updateStatistics(MessageStatistics stats) {
        try {
            if (binding != null) {
                if (stats != null) {
                    if (binding.tvTotalCount != null) {
                        binding.tvTotalCount.setText("Total: " + stats.total);
                    }
                    if (binding.tvUnreadCount != null) {
                        binding.tvUnreadCount.setText("Unread: " + stats.unread);
                    }
                } else {
                    Log.w("InboxFragment", "Statistics object is null");
                }
            }
        } catch (Exception e) {
            Log.e("InboxFragment", "Error updating statistics", e);
        }
    }
    
    private void showEmptyState() {
        try {
            if (binding != null && binding.emptyView != null) {
                binding.emptyView.setVisibility(View.VISIBLE);
                if (binding.recyclerView != null) {
                    binding.recyclerView.setVisibility(View.GONE);
                }
            }
        } catch (Exception e) {
            Log.e("InboxFragment", "Error showing empty state", e);
        }
    }
    
    private void hideEmptyState() {
        try {
            if (binding != null && binding.emptyView != null) {
                binding.emptyView.setVisibility(View.GONE);
                if (binding.recyclerView != null) {
                    binding.recyclerView.setVisibility(View.VISIBLE);
                }
            }
        } catch (Exception e) {
            Log.e("InboxFragment", "Error hiding empty state", e);
        }
    }
    
    private void showSuccess(String message) {
        try {
            if (message != null && isAdded() && getContext() != null) {
                Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Log.e("InboxFragment", "Error showing success message", e);
        }
    }
    
    private void showError(String message) {
        try {
            if (message != null && isAdded() && getContext() != null) {
                Toast.makeText(getContext(), "Error: " + message, Toast.LENGTH_LONG).show();
            }
        } catch (Exception e) {
            Log.e("InboxFragment", "Error showing error message", e);
        }
    }
    
    private void showShimmerLoading(boolean show) {
        try {
            if (shimmerContainer != null) {
                if (show) {
                    shimmerContainer.setVisibility(View.VISIBLE);
                    shimmerContainer.startShimmer();
                    if (binding != null && binding.recyclerView != null) {
                        binding.recyclerView.setVisibility(View.GONE);
                    }
                } else {
                    shimmerContainer.stopShimmer();
                    shimmerContainer.setVisibility(View.GONE);
                    if (binding != null && binding.recyclerView != null) {
                        binding.recyclerView.setVisibility(View.VISIBLE);
                    }
                }
            }
        } catch (Exception e) {
            Log.e("InboxFragment", "Error in showShimmerLoading", e);
        }
    }
    
    private boolean checkSmsPermissions() {
        try {
            if (!isAdded() || getContext() == null) {
                Log.w("InboxFragment", "Fragment not attached, cannot check permissions");
                return false;
            }
            
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_SMS) 
                != PackageManager.PERMISSION_GRANTED) {
                
                if (isAdded() && getContext() != null) {
                    Toast.makeText(getContext(), "SMS permission required to view messages", Toast.LENGTH_LONG).show();
                }
                
                // Request permissions
                requestPermissions(new String[]{Manifest.permission.READ_SMS}, 1003);
                return false;
            }
            
            return true;
        } catch (Exception e) {
            Log.e("InboxFragment", "Error checking SMS permissions", e);
            return false;
        }
    }
    
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        
        if (requestCode == 1003) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted, retry setup
                if (viewModel == null) {
                    viewModel = new ViewModelProvider(this).get(SimpleInboxViewModel.class);
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
        
        try {
            // Clean up shimmer first to stop animations
            if (shimmerContainer != null) {
                shimmerContainer.stopShimmer();
                shimmerContainer.setVisibility(View.GONE);
            }
            
            // Clear adapter data to prevent memory leaks
            if (adapter != null) {
                try {
                    // Submit empty data to clear current items
                    if (getViewLifecycleOwner().getLifecycle().getCurrentState().isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)) {
                        adapter.submitData(getLifecycle(), androidx.paging.PagingData.empty());
                    }
                } catch (Exception e) {
                    Log.w("InboxFragment", "Error clearing adapter data", e);
                }
                
                // Clean up adapter resources
                adapter.cleanup();
            }
            
            // Clean up RecyclerView
            if (binding != null && binding.recyclerView != null) {
                try {
                    binding.recyclerView.setAdapter(null);
                    binding.recyclerView.clearOnScrollListeners();
                } catch (Exception e) {
                    Log.w("InboxFragment", "Error cleaning up RecyclerView", e);
                }
            }
            
            // Clear SwipeRefresh listeners
            if (binding != null && binding.swipeRefresh != null) {
                try {
                    binding.swipeRefresh.setOnRefreshListener(null);
                } catch (Exception e) {
                    Log.w("InboxFragment", "Error clearing SwipeRefresh listener", e);
                }
            }
            
            // Clear SearchView listeners
            if (binding != null && binding.searchView != null) {
                try {
                    binding.searchView.setOnQueryTextListener(null);
                } catch (Exception e) {
                    Log.w("InboxFragment", "Error clearing SearchView listener", e);
                }
            }
            
            // Clear chip click listeners
            if (binding != null) {
                try {
                    if (binding.chipAll != null) binding.chipAll.setOnClickListener(null);
                    if (binding.chipInbox != null) binding.chipInbox.setOnClickListener(null);
                    if (binding.chipSent != null) binding.chipSent.setOnClickListener(null);
                    if (binding.chipUnread != null) binding.chipUnread.setOnClickListener(null);
                } catch (Exception e) {
                    Log.w("InboxFragment", "Error clearing chip listeners", e);
                }
            }
            
        } catch (Exception e) {
            Log.e("InboxFragment", "Error during onDestroyView cleanup", e);
        } finally {
            // Nullify all references to prevent memory leaks
            adapter = null;
            headerLoadStateAdapter = null;
            footerLoadStateAdapter = null;
            shimmerContainer = null;
            analytics = null;
            
            // Clear binding last
            if (binding != null) {
                binding.unbind();
                binding = null;
            }
            
            Log.d("InboxFragment", "onDestroyView cleanup completed");
        }
    }
}
