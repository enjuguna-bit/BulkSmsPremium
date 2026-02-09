package com.afriserve.smsmanager.ui.inbox;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import androidx.appcompat.widget.SearchView;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.paging.LoadState;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.afriserve.smsmanager.R;
import com.afriserve.smsmanager.data.entity.ConversationEntity;
import com.afriserve.smsmanager.data.contacts.ContactResolver;
import com.afriserve.smsmanager.databinding.FragmentInboxBinding;
import com.afriserve.smsmanager.data.blocks.BlockListManager;
import com.facebook.shimmer.ShimmerFrameLayout;
import com.google.android.material.chip.Chip;
import com.google.android.material.snackbar.Snackbar;
import dagger.hilt.android.AndroidEntryPoint;
import java.util.HashMap;
import java.util.Map;
import javax.inject.Inject;
import android.os.Build;
import android.provider.Telephony;
import android.app.role.RoleManager;

/**
 * Premium Inbox Fragment with advanced features
 */
@AndroidEntryPoint
public class InboxFragment extends Fragment {
    
    private static final String TAG = "InboxFragment";
    private static final int SMS_PERMISSION_REQUEST_CODE = 1001;
    
    private FragmentInboxBinding binding;
    private SimpleInboxViewModel viewModel;
    
    private ConversationPagingAdapter adapter;
    private ConversationLoadStateAdapter headerLoadStateAdapter;
    private ConversationLoadStateAdapter footerLoadStateAdapter;
    private ShimmerFrameLayout shimmerContainer;
    
    @Inject
    ContactResolver contactResolver;
    
    private InboxAnalytics analytics;
    private Map<String, Long> lastAccessTimes = new HashMap<>();
    private BlockListManager blockListManager;
    
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
        blockListManager = new BlockListManager(requireContext());
        
        // Initialize shimmer container
        shimmerContainer = binding.shimmerContainer;
        
        setupRecyclerView();
        setupSearch();
        setupFilters();
        setupSwipeRefresh();
        observeData();
        observeState();
        updateEmptyState(false);
        
        // Show shimmer loading immediately for better UX
        showShimmerLoading(true);
        
        // Messages will load automatically from cache via ViewModel
        // No need to trigger manual sync here - it's handled in ViewModel background
        
        // Track fragment view (analytics placeholder)
        Log.d(TAG, "Fragment viewed: inbox");
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
        
        // Setup RecyclerView with paging
        binding.recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.recyclerView.addItemDecoration(new DividerItemDecoration(requireContext(), DividerItemDecoration.VERTICAL));
        
        // Set adapters with load state
        binding.recyclerView.setAdapter(adapter.withLoadStateHeaderAndFooter(
            headerLoadStateAdapter,
            footerLoadStateAdapter
        ));
        
        // Optimize performance
        binding.recyclerView.setHasFixedSize(true);
        binding.recyclerView.setItemAnimator(null);

        attachSwipeActions();
        
        // Handle load states (refresh / prepend / append)
        adapter.addLoadStateListener(loadStates -> {
            long startTime = System.currentTimeMillis();
            
            // CombinedLoadStates: handle refresh loading specially (initial load / swipe to refresh)
            boolean isRefreshing = loadStates.getRefresh() instanceof LoadState.Loading;
            
            // Hide shimmer when data starts loading
            if (!isRefreshing) {
                showShimmerLoading(false);
            }
            
            binding.progressBar.setVisibility(isRefreshing ? View.VISIBLE : View.GONE);
            binding.swipeRefresh.setRefreshing(isRefreshing);
            
            // Show error state on refresh
            LoadState refresh = loadStates.getRefresh();
            if (refresh instanceof LoadState.Error) {
                showShimmerLoading(false); // Hide shimmer on error
                Throwable throwable = ((LoadState.Error) refresh).getError();
                // Log load state error (analytics disabled)
                Log.e(TAG, "Load state error during refresh", throwable);
                String userMessage = throwable != null ? throwable.getMessage() : "An error occurred";
                Toast.makeText(requireContext(), userMessage, Toast.LENGTH_LONG).show();
            } else if (refresh instanceof LoadState.NotLoading) {
                showShimmerLoading(false); // Hide shimmer when loaded
                Log.d(TAG, "Load state success: items=" + adapter.getItemCount());
            }
            LoadState prepend = loadStates.getPrepend();
            if (prepend instanceof LoadState.Error) {
                // Log prepend error (analytics disabled)
                Log.e(TAG, "Load state error during prepend", ((LoadState.Error) prepend).getError());
            }
            
            // Handle append errors (loading at bottom)
            LoadState append = loadStates.getAppend();
            if (append instanceof LoadState.Error) {
                Log.e(TAG, "Load state error during append", ((LoadState.Error) append).getError());
            }
            
            // Show empty state when not loading and adapter has zero items
            boolean isEmpty = refresh instanceof LoadState.NotLoading && adapter.getItemCount() == 0;
            binding.emptyView.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
            
            // Track performance (logged locally)
            long duration = System.currentTimeMillis() - startTime;
            Log.d(TAG, "load_state_update duration=" + duration);
            
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
                            if (adapter != null) {
                                adapter.setSearchQuery(query);
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
                                    if (adapter != null) {
                                        adapter.setSearchQuery("");
                                    }
                                } else if (newText.length() >= 2) {
                                    viewModel.search(newText);
                                    if (adapter != null) {
                                        adapter.setSearchQuery(newText);
                                    }
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
                            adapter.submitData(getViewLifecycleOwner().getLifecycle(), pagingData);
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
                viewModel.markAsRead(conversation.phoneNumber);
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
                if (conversation.threadId != null && conversation.threadId > 0) {
                    args.putLong("thread_id", conversation.threadId);
                }
                
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
        boolean isBlocked = blockListManager != null && blockListManager.isBlocked(conversation.phoneNumber);
        String[] options = {
            "Delete conversation",
            conversation.unreadCount > 0 ? "Mark as read" : "Mark as unread",
            conversation.isArchived ? "Unarchive conversation" : "Archive conversation",
            conversation.isPinned ? "Unpin conversation" : "Pin conversation",
            isBlocked ? "Unblock" : "Block"
        };
        
        new android.app.AlertDialog.Builder(requireContext())
            .setTitle("Conversation Options")
            .setItems(options, (dialog, which) -> {
                switch (which) {
                    case 0:
                        deleteConversation(conversation);
                        break;
                    case 1:
                        if (conversation.unreadCount > 0) {
                            markAsRead(conversation);
                        } else {
                            markAsUnread(conversation);
                        }
                        break;
                    case 2:
                        archiveConversation(conversation);
                        break;
                    case 3:
                        togglePinStatus(conversation);
                        break;
                    case 4:
                        toggleBlockStatus(conversation);
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
        if (viewModel != null) {
            viewModel.setUnreadCount(conversation.phoneNumber, 1);
        }
        Toast.makeText(requireContext(), "Marked as unread", Toast.LENGTH_SHORT).show();
    }

    private void markAsRead(ConversationEntity conversation) {
        if (viewModel != null) {
            viewModel.markAsRead(conversation.phoneNumber);
        }
        Toast.makeText(requireContext(), "Marked as read", Toast.LENGTH_SHORT).show();
    }
    
    private void archiveConversation(ConversationEntity conversation) {
        if (viewModel != null) {
            viewModel.setArchived(conversation.id, !conversation.isArchived);
        }
        Toast.makeText(requireContext(), conversation.isArchived ? "Conversation unarchived" : "Conversation archived", Toast.LENGTH_SHORT).show();
    }
    
    private void togglePinStatus(ConversationEntity conversation) {
        if (viewModel != null) {
            viewModel.setPinned(conversation.id, !conversation.isPinned);
        }
        Toast.makeText(requireContext(), conversation.isPinned ? "Conversation unpinned" : "Conversation pinned", Toast.LENGTH_SHORT).show();
    }

    private void toggleBlockStatus(ConversationEntity conversation) {
        if (blockListManager == null) return;
        if (blockListManager.isBlocked(conversation.phoneNumber)) {
            blockListManager.unblock(conversation.phoneNumber);
            Toast.makeText(requireContext(), "Number unblocked", Toast.LENGTH_SHORT).show();
        } else {
            blockListManager.block(conversation.phoneNumber);
            Toast.makeText(requireContext(), "Number blocked", Toast.LENGTH_SHORT).show();
        }
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
                updateEmptyState(true);
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

            if (!isDefaultSmsApp()) {
                updateEmptyState(true);
                return false;
            }
            
            boolean hasRead = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_SMS)
                == PackageManager.PERMISSION_GRANTED;
            boolean hasReceive = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECEIVE_SMS)
                == PackageManager.PERMISSION_GRANTED;

            if (!hasRead || !hasReceive) {
                
                if (isAdded() && getContext() != null) {
                    showSmsPermissionNotice(false);
                }
                
                // Request permissions
                requestPermissions(new String[]{
                    Manifest.permission.READ_SMS,
                    Manifest.permission.RECEIVE_SMS
                }, SMS_PERMISSION_REQUEST_CODE);
                updateEmptyState(true);
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
        
        if (requestCode == SMS_PERMISSION_REQUEST_CODE) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            if (grantResults.length > 0 && allGranted) {
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
                showSmsPermissionNotice(true);
            }
        }
    }

    private void showSmsPermissionNotice(boolean denied) {
        if (!isAdded() || getContext() == null) return;
        int messageRes = denied
            ? R.string.inbox_permission_denied
            : R.string.inbox_permission_required;
        Toast.makeText(requireContext(), getString(messageRes), Toast.LENGTH_LONG).show();
    }

    @Override
    public void onResume() {
        super.onResume();
        updateEmptyState(false);
    }

    private void updateEmptyState(boolean forceShow) {
        try {
            if (binding == null || !isAdded()) return;

            boolean hasReadSms = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_SMS)
                == PackageManager.PERMISSION_GRANTED;
            boolean hasReceiveSms = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECEIVE_SMS)
                == PackageManager.PERMISSION_GRANTED;
            boolean isDefaultSms = isDefaultSmsApp();

            if (!isDefaultSms) {
                setEmptyState(
                    R.drawable.ic_message,
                    "Set as default SMS app",
                    "To receive messages in real time, set this app as your default SMS handler.",
                    "Make default",
                    v -> requestDefaultSmsRole()
                );
                if (forceShow && binding.emptyView != null) binding.emptyView.setVisibility(View.VISIBLE);
                return;
            }

            if (!hasReadSms || !hasReceiveSms) {
                setEmptyState(
                    R.drawable.ic_message_empty,
                    "Permission required",
                    "Grant SMS access to view and sync messages.",
                    "Grant permission",
                    v -> requestPermissions(new String[]{
                        Manifest.permission.READ_SMS,
                        Manifest.permission.RECEIVE_SMS
                    }, SMS_PERMISSION_REQUEST_CODE)
                );
                if (forceShow && binding.emptyView != null) binding.emptyView.setVisibility(View.VISIBLE);
                return;
            }

            // Default empty state (no messages)
            setEmptyState(
                R.drawable.ic_message_empty,
                "No messages yet",
                "When you receive messages, they’ll appear here.",
                "Refresh",
                v -> {
                    if (viewModel != null) {
                        viewModel.syncMessages();
                    }
                }
            );
        } catch (Exception e) {
            Log.e("InboxFragment", "Error updating empty state", e);
        }
    }

    private void setEmptyState(int iconRes, String title, String subtitle, String actionText, View.OnClickListener action) {
        if (binding == null) return;
        if (binding.ivEmptyIcon != null) {
            binding.ivEmptyIcon.setImageResource(iconRes);
        }
        if (binding.tvEmptyTitle != null) {
            binding.tvEmptyTitle.setText(title);
        }
        if (binding.tvEmptySubtitle != null) {
            binding.tvEmptySubtitle.setText(subtitle);
        }
        if (binding.btnEmptyAction != null) {
            binding.btnEmptyAction.setText(actionText);
            binding.btnEmptyAction.setOnClickListener(action);
            binding.btnEmptyAction.setVisibility(View.VISIBLE);
        }
    }

    private boolean isDefaultSmsApp() {
        if (!isAdded()) return false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            RoleManager roleManager = requireContext().getSystemService(RoleManager.class);
            return roleManager != null && roleManager.isRoleHeld(RoleManager.ROLE_SMS);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            String defaultSmsApp = Telephony.Sms.getDefaultSmsPackage(requireContext());
            return requireContext().getPackageName().equals(defaultSmsApp);
        }
        return false;
    }

    private void requestDefaultSmsRole() {
        if (!isAdded()) return;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                RoleManager roleManager = requireContext().getSystemService(RoleManager.class);
                if (roleManager != null && !roleManager.isRoleHeld(RoleManager.ROLE_SMS)) {
                    startActivity(roleManager.createRequestRoleIntent(RoleManager.ROLE_SMS));
                    return;
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                Intent intent = new Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT);
                intent.putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, requireContext().getPackageName());
                startActivity(intent);
            }
        } catch (Exception e) {
            Log.e("InboxFragment", "Failed to request default SMS role", e);
            Toast.makeText(requireContext(), "Unable to open default SMS settings", Toast.LENGTH_LONG).show();
        }
    }

    private void attachSwipeActions() {
        if (binding == null || binding.recyclerView == null) return;

        ItemTouchHelper.SimpleCallback callback = new ItemTouchHelper.SimpleCallback(0,
            ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT) {
            @Override
            public boolean onMove(androidx.recyclerview.widget.RecyclerView recyclerView,
                                  androidx.recyclerview.widget.RecyclerView.ViewHolder viewHolder,
                                  androidx.recyclerview.widget.RecyclerView.ViewHolder target) {
                return false;
            }

            @Override
            public void onSwiped(androidx.recyclerview.widget.RecyclerView.ViewHolder viewHolder, int direction) {
                int position = viewHolder.getBindingAdapterPosition();
                if (position == androidx.recyclerview.widget.RecyclerView.NO_POSITION) {
                    return;
                }
                ConversationEntity conversation = null;
                if (adapter != null) {
                    try {
                        conversation = adapter.peek(position);
                    } catch (Exception ignored) {
                    }
                }
                if (conversation == null) {
                    if (adapter != null) adapter.notifyItemChanged(position);
                    return;
                }
                final ConversationEntity conversationFinal = conversation;

                if (direction == ItemTouchHelper.LEFT) {
                    boolean newArchived = !conversationFinal.isArchived;
                    if (viewModel != null) {
                        viewModel.setArchived(conversationFinal.id, newArchived);
                    }
                    Snackbar.make(binding.getRoot(),
                            newArchived ? "Conversation archived" : "Conversation unarchived",
                            Snackbar.LENGTH_LONG)
                        .setAction("Undo", v -> {
                            if (viewModel != null) {
                                viewModel.setArchived(conversationFinal.id, conversationFinal.isArchived);
                            }
                        })
                        .show();
                } else {
                    if (conversationFinal.phoneNumber != null) {
                        if (conversationFinal.unreadCount > 0) {
                            final int previous = conversationFinal.unreadCount;
                            if (viewModel != null) {
                                viewModel.markAsRead(conversationFinal.phoneNumber);
                            }
                            Snackbar.make(binding.getRoot(), "Marked as read", Snackbar.LENGTH_LONG)
                                .setAction("Undo", v -> {
                                    if (viewModel != null) {
                                        viewModel.setUnreadCount(conversationFinal.phoneNumber, previous);
                                    }
                                })
                                .show();
                        } else {
                            if (viewModel != null) {
                                viewModel.setUnreadCount(conversationFinal.phoneNumber, 1);
                            }
                            Snackbar.make(binding.getRoot(), "Marked as unread", Snackbar.LENGTH_LONG)
                                .setAction("Undo", v -> {
                                    if (viewModel != null) {
                                        viewModel.setUnreadCount(conversationFinal.phoneNumber, 0);
                                    }
                                })
                                .show();
                        }
                    }
                }
                if (adapter != null) adapter.notifyItemChanged(position);
            }

            @Override
            public void onChildDraw(Canvas c,
                                    androidx.recyclerview.widget.RecyclerView recyclerView,
                                    androidx.recyclerview.widget.RecyclerView.ViewHolder viewHolder,
                                    float dX, float dY, int actionState, boolean isCurrentlyActive) {
                if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE) {
                    View itemView = viewHolder.itemView;
                    float height = itemView.getBottom() - itemView.getTop();
                    float width = height / 3;

                    Paint paint = new Paint();
                    Drawable icon;
                    int iconMargin = (int) ((height - width) / 2);

                    if (dX > 0) {
                        paint.setColor(ContextCompat.getColor(requireContext(), R.color.sms_delivered));
                        RectF background = new RectF(itemView.getLeft(), itemView.getTop(),
                            itemView.getLeft() + dX, itemView.getBottom());
                        c.drawRect(background, paint);
                        icon = ContextCompat.getDrawable(requireContext(), R.drawable.ic_check_circle);
                        if (icon != null) {
                            int iconTop = itemView.getTop() + iconMargin;
                            int iconLeft = itemView.getLeft() + iconMargin;
                            int iconRight = iconLeft + (int) width;
                            int iconBottom = iconTop + (int) width;
                            icon.setBounds(iconLeft, iconTop, iconRight, iconBottom);
                            icon.setTint(android.graphics.Color.WHITE);
                            icon.draw(c);
                        }
                    } else if (dX < 0) {
                        paint.setColor(ContextCompat.getColor(requireContext(), R.color.sms_pending));
                        RectF background = new RectF(itemView.getRight() + dX, itemView.getTop(),
                            itemView.getRight(), itemView.getBottom());
                        c.drawRect(background, paint);
                        icon = ContextCompat.getDrawable(requireContext(), R.drawable.ic_archive);
                        if (icon != null) {
                            int iconTop = itemView.getTop() + iconMargin;
                            int iconLeft = itemView.getRight() - iconMargin - (int) width;
                            int iconRight = itemView.getRight() - iconMargin;
                            int iconBottom = iconTop + (int) width;
                            icon.setBounds(iconLeft, iconTop, iconRight, iconBottom);
                            icon.setTint(android.graphics.Color.WHITE);
                            icon.draw(c);
                        }
                    }
                }
                super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive);
            }
        };

        new ItemTouchHelper(callback).attachToRecyclerView(binding.recyclerView);
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
                // No explicit unbind for view binding; clear reference
                binding = null;
            }
            
            Log.d("InboxFragment", "onDestroyView cleanup completed");
        }
    }
}
