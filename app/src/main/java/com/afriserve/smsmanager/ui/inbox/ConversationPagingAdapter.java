package com.afriserve.smsmanager.ui.inbox;

import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import androidx.paging.PagingDataAdapter;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;
import androidx.core.content.ContextCompat;
import com.google.android.material.color.MaterialColors;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
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
    private String searchQuery = "";
    
    private static final DiffUtil.ItemCallback<ConversationEntity> CONVERSATION_COMPARATOR = new DiffUtil.ItemCallback<ConversationEntity>() {
        @Override
        public boolean areItemsTheSame(ConversationEntity oldItem, ConversationEntity newItem) {
            return oldItem.id == newItem.id;
        }
        
        @Override
        public boolean areContentsTheSame(ConversationEntity oldItem, ConversationEntity newItem) {
            if (oldItem == newItem) {
                return true;
            }
            if (oldItem == null || newItem == null) {
                return false;
            }
            return oldItem.lastMessageTime == newItem.lastMessageTime
                && safeEquals(oldItem.lastMessagePreview, newItem.lastMessagePreview)
                && safeEquals(oldItem.lastMessageType, newItem.lastMessageType)
                && safeEquals(oldItem.contactName, newItem.contactName)
                && safeEquals(oldItem.contactPhotoUri, newItem.contactPhotoUri)
                && oldItem.messageCount == newItem.messageCount
                && oldItem.unreadCount == newItem.unreadCount
                && oldItem.isPinned == newItem.isPinned
                && oldItem.isArchived == newItem.isArchived;
        }

        private boolean safeEquals(String left, String right) {
            if (left == null) return right == null;
            return left.equals(right);
        }
    };
    
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("h:mm a", Locale.getDefault());
    private final SimpleDateFormat dayFormat = new SimpleDateFormat("EEE", Locale.getDefault());

    private int[] avatarColors;
    
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

    public void setSearchQuery(String query) {
        this.searchQuery = query != null ? query.trim() : "";
        notifyDataSetChanged();
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
                if (displayName.startsWith("thread:")) {
                    displayName = "Conversation";
                }
                    
                if (binding.tvContactName != null) {
                    binding.tvContactName.setText(applyHighlight(displayName));
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
                    if ("SENT".equalsIgnoreCase(conversation.lastMessageType)) {
                        lastMessage = "You: " + lastMessage;
                    }
                    binding.tvLastMessage.setText(applyHighlight(lastMessage));
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
                    String firstLetter = displayName.length() > 0 ? displayName.substring(0, 1).toUpperCase() : "?";
                    binding.ivContactPhoto.setText(firstLetter);
                    int color = getAvatarColor(displayName);
                    binding.ivContactPhoto.setBackgroundTintList(ColorStateList.valueOf(color));
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

                applyUnreadStyling(conversation.unreadCount > 0);
                
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
            if (timestamp <= 0) {
                return "";
            }
            Calendar now = Calendar.getInstance();
            Calendar messageTime = Calendar.getInstance();
            messageTime.setTimeInMillis(timestamp);

            long diffMs = Math.abs(System.currentTimeMillis() - timestamp);
            if (diffMs < 60_000) {
                return "Now";
            }
            
            // Check if it's today
            if (now.get(Calendar.YEAR) == messageTime.get(Calendar.YEAR) &&
                now.get(Calendar.DAY_OF_YEAR) == messageTime.get(Calendar.DAY_OF_YEAR)) {
                return timeFormat.format(new Date(timestamp));
            }
            
            // Check if it's yesterday
            Calendar yesterday = Calendar.getInstance();
            yesterday.add(Calendar.DAY_OF_YEAR, -1);
            if (yesterday.get(Calendar.YEAR) == messageTime.get(Calendar.YEAR) &&
                yesterday.get(Calendar.DAY_OF_YEAR) == messageTime.get(Calendar.DAY_OF_YEAR)) {
                return "Yesterday";
            }
            
            // Check if it's within this week
            if (now.get(Calendar.YEAR) == messageTime.get(Calendar.YEAR) &&
                now.get(Calendar.WEEK_OF_YEAR) == messageTime.get(Calendar.WEEK_OF_YEAR)) {
                return dayFormat.format(new Date(timestamp));
            }
            
            // Otherwise show full date
            return dateFormat.format(new Date(timestamp));
        }

        private int getAvatarColor(String key) {
            ensureAvatarColors();
            int hash = key != null ? key.hashCode() : 0;
            int index = Math.abs(hash) % avatarColors.length;
            return avatarColors[index];
        }

        private void ensureAvatarColors() {
            if (avatarColors != null && avatarColors.length > 0) {
                return;
            }
            android.content.Context context = binding.getRoot().getContext();
            avatarColors = new int[] {
                ContextCompat.getColor(context, com.afriserve.smsmanager.R.color.avatar_blue),
                ContextCompat.getColor(context, com.afriserve.smsmanager.R.color.avatar_green),
                ContextCompat.getColor(context, com.afriserve.smsmanager.R.color.avatar_orange),
                ContextCompat.getColor(context, com.afriserve.smsmanager.R.color.avatar_pink),
                ContextCompat.getColor(context, com.afriserve.smsmanager.R.color.avatar_purple),
                ContextCompat.getColor(context, com.afriserve.smsmanager.R.color.avatar_teal)
            };
        }

        private void applyUnreadStyling(boolean hasUnread) {
            if (binding == null) return;
            int style = hasUnread ? Typeface.BOLD : Typeface.NORMAL;
            if (binding.tvContactName != null) binding.tvContactName.setTypeface(null, style);
            if (binding.tvLastMessage != null) binding.tvLastMessage.setTypeface(null, style);
            if (binding.tvTimestamp != null) binding.tvTimestamp.setTypeface(null, style);
            try {
                if (binding.getRoot() instanceof com.google.android.material.card.MaterialCardView) {
                    int color = hasUnread
                        ? ContextCompat.getColor(binding.getRoot().getContext(),
                            com.afriserve.smsmanager.R.color.conversation_unread_bg)
                        : MaterialColors.getColor(binding.getRoot(),
                            com.google.android.material.R.attr.colorSurface);
                    ((com.google.android.material.card.MaterialCardView) binding.getRoot()).setCardBackgroundColor(color);
                }
            } catch (Exception ignored) {
            }
        }

        private CharSequence applyHighlight(String text) {
            if (text == null) return "";
            if (searchQuery == null || searchQuery.isEmpty()) {
                return text;
            }
            String lowerText = text.toLowerCase(Locale.getDefault());
            String lowerQuery = searchQuery.toLowerCase(Locale.getDefault());
            int start = lowerText.indexOf(lowerQuery);
            if (start < 0) {
                return text;
            }
            SpannableString spannable = new SpannableString(text);
            int highlightColor = MaterialColors.getColor(binding.getRoot(),
                com.google.android.material.R.attr.colorPrimary);
            while (start >= 0) {
                int end = start + lowerQuery.length();
                spannable.setSpan(new ForegroundColorSpan(highlightColor), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                spannable.setSpan(new StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                start = lowerText.indexOf(lowerQuery, end);
            }
            return spannable;
        }
    }
    
    public void cleanup() {
        // Clean up any resources if needed
    }
}
