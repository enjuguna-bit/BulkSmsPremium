package com.bulksms.smsmanager.ui.inbox;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.paging.PagingDataAdapter;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.bulksms.smsmanager.data.entity.ConversationEntity;
import com.bulksms.smsmanager.data.contacts.ContactResolver;
import com.bulksms.smsmanager.databinding.ItemConversationBinding;
import com.bulksms.smsmanager.databinding.ItemConversationPlaceholderBinding;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/**
 * Enhanced Paging 3 adapter with placeholders and efficient diffing
 * Provides smooth scrolling and optimal performance for large datasets
 */
public class EnhancedConversationPagingAdapter extends PagingDataAdapter<ConversationEntity, EnhancedConversationPagingAdapter.ConversationViewHolder> {
    
    private static final String TAG = "EnhancedConvAdapter";
    private static final DiffUtil.ItemCallback<ConversationEntity> CONVERSATION_COMPARATOR = new DiffUtil.ItemCallback<ConversationEntity>() {
        @Override
        public boolean areItemsTheSame(@NonNull ConversationEntity oldItem, @NonNull ConversationEntity newItem) {
            return oldItem.id == newItem.id;
        }
        
        @Override
        public boolean areContentsTheSame(@NonNull ConversationEntity oldItem, @NonNull ConversationEntity newItem) {
            // Efficient content comparison with null safety
            return oldItem.equals(newItem) &&
                   java.util.Objects.equals(oldItem.contactName, newItem.contactName) &&
                   java.util.Objects.equals(oldItem.phoneNumber, newItem.phoneNumber) &&
                   java.util.Objects.equals(oldItem.lastMessagePreview, newItem.lastMessagePreview) &&
                   oldItem.lastMessageTime == newItem.lastMessageTime &&
                   oldItem.unreadCount == newItem.unreadCount &&
                   oldItem.messageCount == newItem.messageCount &&
                   oldItem.isPinned == newItem.isPinned &&
                   oldItem.isArchived == newItem.isArchived;
        }
        
        @Nullable
        @Override
        public Object getChangePayload(@NonNull ConversationEntity oldItem, @NonNull ConversationEntity newItem) {
            // Return specific fields that changed for partial updates
            if (!areContentsTheSame(oldItem, newItem)) {
                return new ConversationChangePayload(oldItem, newItem);
            }
            return null;
        }
    };
    
    private final OnConversationClickListener onConversationClick;
    private final OnConversationLongClickListener onConversationLongClick;
    private final ContactResolver contactResolver;
    
    // Date formatters for efficient reuse
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("h:mm a", Locale.getDefault());
    private final SimpleDateFormat todayFormat = new SimpleDateFormat("h:mm a", Locale.getDefault());
    private final SimpleDateFormat dayFormat = new SimpleDateFormat("EEEE", Locale.getDefault());
    
    public interface OnConversationClickListener {
        void onConversationClick(ConversationEntity conversation);
    }
    
    public interface OnConversationLongClickListener {
        boolean onConversationLongClick(ConversationEntity conversation);
    }
    
    /**
     * Payload for efficient partial updates
     */
    public static class ConversationChangePayload {
        public final boolean contactNameChanged;
        public final boolean phoneNumberChanged;
        public final boolean lastMessageChanged;
        public final boolean timestampChanged;
        public final boolean unreadCountChanged;
        public final boolean messageCountChanged;
        public final boolean pinStatusChanged;
        public final boolean archiveStatusChanged;
        
        public ConversationChangePayload(ConversationEntity oldItem, ConversationEntity newItem) {
            contactNameChanged = !java.util.Objects.equals(oldItem.contactName, newItem.contactName);
            phoneNumberChanged = !java.util.Objects.equals(oldItem.phoneNumber, newItem.phoneNumber);
            lastMessageChanged = !java.util.Objects.equals(oldItem.lastMessagePreview, newItem.lastMessagePreview);
            timestampChanged = oldItem.lastMessageTime != newItem.lastMessageTime;
            unreadCountChanged = oldItem.unreadCount != newItem.unreadCount;
            messageCountChanged = oldItem.messageCount != newItem.messageCount;
            pinStatusChanged = oldItem.isPinned != newItem.isPinned;
            archiveStatusChanged = oldItem.isArchived != newItem.isArchived;
        }
    }
    
    public EnhancedConversationPagingAdapter(
        OnConversationClickListener onConversationClick, 
        OnConversationLongClickListener onConversationLongClick,
        ContactResolver contactResolver
    ) {
        super(CONVERSATION_COMPARATOR);
        this.onConversationClick = onConversationClick;
        this.onConversationLongClick = onConversationLongClick;
        this.contactResolver = contactResolver;
        
        // Enable stable IDs for better performance
        setHasStableIds(true);
    }
    
    @Override
    public long getItemId(int position) {
        ConversationEntity item = getItem(position);
        return item != null ? item.id : RecyclerView.NO_ID;
    }
    
    @NonNull
    @Override
    public ConversationViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == 0) {
            // Placeholder view
            ItemConversationPlaceholderBinding binding = ItemConversationPlaceholderBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
            return new PlaceholderViewHolder(binding);
        } else {
            // Regular conversation view
            ItemConversationBinding binding = ItemConversationBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
            return new ConversationViewHolder(binding);
        }
    }
    
    @Override
    public int getItemViewType(int position) {
        return getItem(position) == null ? 0 : 1;
    }
    
    @Override
    public void onBindViewHolder(@NonNull ConversationViewHolder holder, int position) {
        ConversationEntity conversation = getItem(position);
        if (conversation != null) {
            holder.bind(conversation);
        }
    }
    
    @Override
    public void onBindViewHolder(@NonNull ConversationViewHolder holder, int position, @NonNull List<Object> payloads) {
        ConversationEntity conversation = getItem(position);
        if (conversation == null) {
            return;
        }
        
        if (payloads.isEmpty()) {
            // Full bind
            holder.bind(conversation);
        } else {
            // Partial bind for efficiency
            for (Object payload : payloads) {
                if (payload instanceof ConversationChangePayload) {
                    holder.bindPartial(conversation, (ConversationChangePayload) payload);
                }
            }
        }
    }
    
    /**
     * Base ViewHolder class
     */
    abstract static class ConversationViewHolder extends RecyclerView.ViewHolder {
        public ConversationViewHolder(@NonNull ViewGroup parent) {
            super(parent);
        }
        
        public abstract void bind(ConversationEntity conversation);
        public void bindPartial(ConversationEntity conversation, ConversationChangePayload payload) {
            // Default to full bind for placeholder
            bind(conversation);
        }
    }
    
    /**
     * Regular conversation ViewHolder
     */
    class ConversationViewHolder extends ConversationViewHolder {
        private final ItemConversationBinding binding;
        
        ConversationViewHolder(@NonNull ItemConversationBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
        
        @Override
        public void bind(ConversationEntity conversation) {
            try {
                if (conversation == null) {
                    hideAllViews();
                    return;
                }
                
                // Set contact name with null safety
                String displayName = getDisplayName(conversation);
                if (binding.tvContactName != null) {
                    binding.tvContactName.setText(displayName);
                }
                
                // Set phone number
                if (binding.tvPhoneNumber != null) {
                    if (shouldShowPhoneNumber(conversation)) {
                        binding.tvPhoneNumber.setText(conversation.phoneNumber);
                        binding.tvPhoneNumber.setVisibility(android.view.View.VISIBLE);
                    } else {
                        binding.tvPhoneNumber.setVisibility(android.view.View.GONE);
                    }
                }
                
                // Set last message
                if (binding.tvLastMessage != null) {
                    String lastMessage = conversation.lastMessagePreview != null ? conversation.lastMessagePreview : "";
                    binding.tvLastMessage.setText(lastMessage);
                }
                
                // Set message count
                if (binding.tvMessageCount != null) {
                    if (conversation.messageCount > 1) {
                        binding.tvMessageCount.setText(conversation.messageCount + " messages");
                        binding.tvMessageCount.setVisibility(android.view.View.VISIBLE);
                    } else {
                        binding.tvMessageCount.setVisibility(android.view.View.GONE);
                    }
                }
                
                // Set unread count
                updateUnreadBadge(conversation);
                
                // Set timestamp
                if (binding.tvTimestamp != null) {
                    binding.tvTimestamp.setText(formatTimestamp(conversation.lastMessageTime));
                }
                
                // Set contact photo
                updateContactPhoto(displayName, conversation);
                
                // Set click listeners with error handling
                setClickListeners(conversation);
                
                // Set status indicators
                updateStatusIndicators(conversation);
                
            } catch (Exception e) {
                Log.e(TAG, "Error binding conversation", e);
                hideAllViews();
            }
        }
        
        @Override
        public void bindPartial(ConversationEntity conversation, ConversationChangePayload payload) {
            try {
                if (payload.contactNameChanged && binding.tvContactName != null) {
                    String displayName = getDisplayName(conversation);
                    binding.tvContactName.setText(displayName);
                }
                
                if (payload.phoneNumberChanged && binding.tvPhoneNumber != null) {
                    if (shouldShowPhoneNumber(conversation)) {
                        binding.tvPhoneNumber.setText(conversation.phoneNumber);
                        binding.tvPhoneNumber.setVisibility(android.view.View.VISIBLE);
                    } else {
                        binding.tvPhoneNumber.setVisibility(android.view.View.GONE);
                    }
                }
                
                if (payload.lastMessageChanged && binding.tvLastMessage != null) {
                    String lastMessage = conversation.lastMessagePreview != null ? conversation.lastMessagePreview : "";
                    binding.tvLastMessage.setText(lastMessage);
                }
                
                if (payload.timestampChanged && binding.tvTimestamp != null) {
                    binding.tvTimestamp.setText(formatTimestamp(conversation.lastMessageTime));
                }
                
                if (payload.unreadCountChanged) {
                    updateUnreadBadge(conversation);
                }
                
                if (payload.messageCountChanged && binding.tvMessageCount != null) {
                    if (conversation.messageCount > 1) {
                        binding.tvMessageCount.setText(conversation.messageCount + " messages");
                        binding.tvMessageCount.setVisibility(android.view.View.VISIBLE);
                    } else {
                        binding.tvMessageCount.setVisibility(android.view.View.GONE);
                    }
                }
                
                if (payload.pinStatusChanged && binding.ivPinned != null) {
                    binding.ivPinned.setVisibility(conversation.isPinned ? android.view.View.VISIBLE : android.view.View.GONE);
                }
                
                if (payload.archiveStatusChanged && binding.ivArchived != null) {
                    binding.ivArchived.setVisibility(conversation.isArchived ? android.view.View.VISIBLE : android.view.View.GONE);
                }
                
            } catch (Exception e) {
                Log.e(TAG, "Error in partial bind", e);
                bind(conversation); // Fallback to full bind
            }
        }
        
        private String getDisplayName(ConversationEntity conversation) {
            String contactName = conversation.contactName;
            if (contactName != null && !contactName.trim().isEmpty()) {
                return contactName.trim();
            }
            return conversation.phoneNumber != null ? conversation.phoneNumber : "Unknown";
        }
        
        private boolean shouldShowPhoneNumber(ConversationEntity conversation) {
            String contactName = conversation.contactName;
            return contactName != null && !contactName.trim().isEmpty() && 
                   conversation.phoneNumber != null && !conversation.phoneNumber.trim().isEmpty();
        }
        
        private void updateUnreadBadge(ConversationEntity conversation) {
            if (binding.tvUnreadCount != null && binding.tvUnreadBadge != null) {
                if (conversation.unreadCount > 0) {
                    binding.tvUnreadCount.setText(String.valueOf(conversation.unreadCount));
                    binding.tvUnreadCount.setVisibility(android.view.View.VISIBLE);
                    binding.tvUnreadBadge.setVisibility(android.view.View.VISIBLE);
                } else {
                    binding.tvUnreadCount.setVisibility(android.view.View.GONE);
                    binding.tvUnreadBadge.setVisibility(android.view.View.GONE);
                }
            }
        }
        
        private void updateContactPhoto(String displayName, ConversationEntity conversation) {
            if (binding.ivContactPhoto != null) {
                String firstLetter = displayName.length() > 0 ? displayName.substring(0, 1).toUpperCase() : "?";
                binding.ivContactPhoto.setText(firstLetter);
            }
        }
        
        private void setClickListeners(ConversationEntity conversation) {
            if (binding.getRoot() != null) {
                binding.getRoot().setOnClickListener(v -> {
                    try {
                        if (onConversationClick != null) {
                            onConversationClick.onConversationClick(conversation);
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error in click listener", e);
                    }
                });
                
                binding.getRoot().setOnLongClickListener(v -> {
                    try {
                        if (onConversationLongClick != null) {
                            return onConversationLongClick.onConversationLongClick(conversation);
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error in long click listener", e);
                    }
                    return false;
                });
            }
        }
        
        private void updateStatusIndicators(ConversationEntity conversation) {
            if (binding.ivPinned != null) {
                binding.ivPinned.setVisibility(conversation.isPinned ? android.view.View.VISIBLE : android.view.View.GONE);
            }
            
            if (binding.ivArchived != null) {
                binding.ivArchived.setVisibility(conversation.isArchived ? android.view.View.VISIBLE : android.view.View.GONE);
            }
        }
        
        private void hideAllViews() {
            if (binding != null) {
                if (binding.tvContactName != null) binding.tvContactName.setText("Loading...");
                if (binding.tvPhoneNumber != null) binding.tvPhoneNumber.setVisibility(android.view.View.GONE);
                if (binding.tvLastMessage != null) binding.tvLastMessage.setText("");
                if (binding.tvMessageCount != null) binding.tvMessageCount.setVisibility(android.view.View.GONE);
                if (binding.tvUnreadCount != null) binding.tvUnreadCount.setVisibility(android.view.View.GONE);
                if (binding.tvUnreadBadge != null) binding.tvUnreadBadge.setVisibility(android.view.View.GONE);
                if (binding.tvTimestamp != null) binding.tvTimestamp.setText("");
                if (binding.ivPinned != null) binding.ivPinned.setVisibility(android.view.View.GONE);
                if (binding.ivArchived != null) binding.ivArchived.setVisibility(android.view.View.GONE);
                if (binding.ivContactPhoto != null) binding.ivContactPhoto.setText("?");
            }
        }
        
        private String formatTimestamp(long timestamp) {
            try {
                Calendar now = Calendar.getInstance();
                Calendar messageTime = Calendar.getInstance();
                messageTime.setTimeInMillis(timestamp);
                
                // Check if it's today
                if (now.get(Calendar.YEAR) == messageTime.get(Calendar.YEAR) &&
                    now.get(Calendar.DAY_OF_YEAR) == messageTime.get(Calendar.DAY_OF_YEAR)) {
                    return "Today " + todayFormat.format(new Date(timestamp));
                }
                
                // Check if it's yesterday
                Calendar yesterday = Calendar.getInstance();
                yesterday.add(Calendar.DAY_OF_YEAR, -1);
                if (yesterday.get(Calendar.YEAR) == messageTime.get(Calendar.YEAR) &&
                    yesterday.get(Calendar.DAY_OF_YEAR) == messageTime.get(Calendar.DAY_OF_YEAR)) {
                    return "Yesterday " + timeFormat.format(new Date(timestamp));
                }
                
                // Check if it's within this week
                if (now.get(Calendar.YEAR) == messageTime.get(Calendar.YEAR) &&
                    now.get(Calendar.WEEK_OF_YEAR) == messageTime.get(Calendar.WEEK_OF_YEAR)) {
                    return dayFormat.format(new Date(timestamp));
                }
                
                // Otherwise show full date
                return dateFormat.format(new Date(timestamp));
                
            } catch (Exception e) {
                Log.e(TAG, "Error formatting timestamp", e);
                return "";
            }
        }
    }
    
    /**
     * Placeholder ViewHolder for loading state
     */
    class PlaceholderViewHolder extends ConversationViewHolder {
        private final ItemConversationPlaceholderBinding binding;
        
        PlaceholderViewHolder(@NonNull ItemConversationPlaceholderBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
        
        @Override
        public void bind(ConversationEntity conversation) {
            // Placeholder is already set in layout
            // No additional binding needed
        }
    }
    
    /**
     * Cleanup method
     */
    public void cleanup() {
        // Clean up any resources if needed
        Log.d(TAG, "EnhancedConversationPagingAdapter cleaned up");
    }
}
