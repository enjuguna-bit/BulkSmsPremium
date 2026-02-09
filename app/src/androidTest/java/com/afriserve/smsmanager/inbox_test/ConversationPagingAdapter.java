package com.afriserve.smsmanager.ui.inbox;

import android.view.LayoutInflater;
import android.view.ViewGroup;
import androidx.paging.PagingDataAdapter;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;
import com.afriserve.smsmanager.data.entity.ConversationEntity;
import com.afriserve.smsmanager.data.contacts.ContactResolver;
import com.afriserve.smsmanager.databinding.ItemConversationBinding;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class ConversationPagingAdapter extends PagingDataAdapter<ConversationEntity, ConversationPagingAdapter.ConversationViewHolder> {
    
    private final OnConversationClickListener onConversationClick;
    private final OnConversationLongClickListener onConversationLongClick;
    private final ContactResolver contactResolver;
    
    private static final DiffUtil.ItemCallback<ConversationEntity> CONVERSATION_COMPARATOR = new DiffUtil.ItemCallback<ConversationEntity>() {
        @Override
        public boolean areItemsTheSame(ConversationEntity oldItem, ConversationEntity newItem) {
            return oldItem.id == newItem.id;
        }
        
        @Override
        public boolean areContentsTheSame(ConversationEntity oldItem, ConversationEntity newItem) {
            return oldItem.equals(newItem);
        }
    };
    
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("h:mm a", Locale.getDefault());
    private final SimpleDateFormat todayFormat = new SimpleDateFormat("h:mm a", Locale.getDefault());
    
    public interface OnConversationClickListener {
        void onConversationClick(ConversationEntity conversation);
    }
    
    public interface OnConversationLongClickListener {
        boolean onConversationLongClick(ConversationEntity conversation);
    }
    
    public ConversationPagingAdapter(OnConversationClickListener onConversationClick, 
                                   OnConversationLongClickListener onConversationLongClick,
                                   ContactResolver contactResolver) {
        super(CONVERSATION_COMPARATOR);
        this.onConversationClick = onConversationClick;
        this.onConversationLongClick = onConversationLongClick;
        this.contactResolver = contactResolver;
    }
    
    @Override
    public ConversationViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        ItemConversationBinding binding = ItemConversationBinding.inflate(
            LayoutInflater.from(parent.getContext()), parent, false);
        return new ConversationViewHolder(binding);
    }
    
    @Override
    public void onBindViewHolder(ConversationViewHolder holder, int position) {
        ConversationEntity conversation = getItem(position);
        if (conversation != null) {
            holder.bind(conversation);
        }
    }
    
    class ConversationViewHolder extends RecyclerView.ViewHolder {
        private final ItemConversationBinding binding;
        
        ConversationViewHolder(ItemConversationBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
        
        void bind(ConversationEntity conversation) {
            try {
                if (conversation == null) {
                    // Hide all views if conversation is null
                    hideAllViews();
                    return;
                }
                
                // Set contact name or phone number with null guards
                String displayName = conversation.contactName != null && !conversation.contactName.trim().isEmpty() 
                    ? conversation.contactName.trim() 
                    : (conversation.phoneNumber != null ? conversation.phoneNumber : "Unknown");
                    
                if (binding.tvContactName != null) {
                    binding.tvContactName.setText(displayName);
                }
                
                // Set phone number if contact name is available
                if (binding.tvPhoneNumber != null) {
                    if (conversation.contactName != null && !conversation.contactName.trim().isEmpty() 
                        && conversation.phoneNumber != null) {
                        binding.tvPhoneNumber.setText(conversation.phoneNumber);
                        binding.tvPhoneNumber.setVisibility(android.view.View.VISIBLE);
                    } else {
                        binding.tvPhoneNumber.setVisibility(android.view.View.GONE);
                    }
                }
                
                // Set last message preview with null guard
                if (binding.tvLastMessage != null) {
                    String lastMessage = conversation.lastMessagePreview != null ? conversation.lastMessagePreview : "";
                    binding.tvLastMessage.setText(lastMessage);
                }
                
                // Set message count with null guard
                if (binding.tvMessageCount != null) {
                    if (conversation.messageCount > 1) {
                        binding.tvMessageCount.setText(conversation.messageCount + " messages");
                        binding.tvMessageCount.setVisibility(android.view.View.VISIBLE);
                    } else {
                        binding.tvMessageCount.setVisibility(android.view.View.GONE);
                    }
                }
                
                // Set unread count with null guard
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
                
                // Set timestamp with null guard
                if (binding.tvTimestamp != null) {
                    String timeText = formatTimestamp(conversation.lastMessageTime);
                    binding.tvTimestamp.setText(timeText);
                }
                
                // Set contact photo with null guard
                if (binding.ivContactPhoto != null) {
                    if (conversation.contactPhotoUri != null && !conversation.contactPhotoUri.trim().isEmpty()) {
                        // Load contact photo using Glide or similar
                        // For now, just show the first letter of name
                        String firstLetter = displayName.length() > 0 ? displayName.substring(0, 1).toUpperCase() : "?";
                        binding.ivContactPhoto.setText(firstLetter);
                    } else {
                        // Show first letter of name/phone
                        String firstLetter = displayName.length() > 0 ? displayName.substring(0, 1).toUpperCase() : "?";
                        binding.ivContactPhoto.setText(firstLetter);
                    }
                }
                
                // Set click listeners with null guards
                if (binding.getRoot() != null) {
                    binding.getRoot().setOnClickListener(v -> {
                        try {
                            if (onConversationClick != null && conversation != null) {
                                onConversationClick.onConversationClick(conversation);
                            }
                        } catch (Exception e) {
                            // Log error but don't crash
                            android.util.Log.e("ConversationAdapter", "Error in click listener", e);
                        }
                    });
                    
                    binding.getRoot().setOnLongClickListener(v -> {
                        try {
                            if (onConversationLongClick != null && conversation != null) {
                                return onConversationLongClick.onConversationLongClick(conversation);
                            }
                        } catch (Exception e) {
                            // Log error but don't crash
                            android.util.Log.e("ConversationAdapter", "Error in long click listener", e);
                        }
                        return false;
                    });
                }
                
                // Set pinned/archived status with null guards
                if (binding.ivPinned != null) {
                    binding.ivPinned.setVisibility(conversation.isPinned ? android.view.View.VISIBLE : android.view.View.GONE);
                }
                
                if (binding.ivArchived != null) {
                    binding.ivArchived.setVisibility(conversation.isArchived ? android.view.View.VISIBLE : android.view.View.GONE);
                }
                
            } catch (Exception e) {
                // Catch any unexpected errors to prevent crashes
                android.util.Log.e("ConversationAdapter", "Error binding conversation", e);
                hideAllViews();
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
                SimpleDateFormat dayFormat = new SimpleDateFormat("EEEE", Locale.getDefault());
                return dayFormat.format(new Date(timestamp));
            }
            
            // Otherwise show full date
            return dateFormat.format(new Date(timestamp));
        }
    }
    
    public void cleanup() {
        // Clean up any resources if needed
    }
}
