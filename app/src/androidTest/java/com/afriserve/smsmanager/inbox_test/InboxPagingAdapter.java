package com.afriserve.smsmanager.ui.inbox;

import android.view.LayoutInflater;
import android.view.ViewGroup;
import androidx.paging.PagingDataAdapter;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;
import com.afriserve.smsmanager.data.entity.SmsEntity;
import com.afriserve.smsmanager.data.contacts.ContactResolver;
import com.afriserve.smsmanager.databinding.ItemSmsMessageBinding;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

public class InboxPagingAdapter extends PagingDataAdapter<SmsEntity, InboxPagingAdapter.MessageViewHolder> {
    
    private final OnMessageClickListener onMessageClick;
    private final OnMessageLongClickListener onMessageLongClick;
    private final ContactResolver contactResolver;
    private final CompositeDisposable disposables = new CompositeDisposable();
    
    private static final DiffUtil.ItemCallback<SmsEntity> MESSAGE_COMPARATOR = new DiffUtil.ItemCallback<SmsEntity>() {
        @Override
        public boolean areItemsTheSame(SmsEntity oldItem, SmsEntity newItem) {
            return oldItem.id == newItem.id;
        }
        
        @Override
        public boolean areContentsTheSame(SmsEntity oldItem, SmsEntity newItem) {
            return oldItem.equals(newItem);
        }
    };
    
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("h:mm a", Locale.getDefault());
    private final SimpleDateFormat todayFormat = new SimpleDateFormat("h:mm a", Locale.getDefault());
    
    public interface OnMessageClickListener {
        void onMessageClick(SmsEntity message);
    }
    
    public interface OnMessageLongClickListener {
        boolean onMessageLongClick(SmsEntity message);
    }
    
    public InboxPagingAdapter(OnMessageClickListener onMessageClick, 
                           OnMessageLongClickListener onMessageLongClick,
                           ContactResolver contactResolver) {
        super(MESSAGE_COMPARATOR);
        this.onMessageClick = onMessageClick;
        this.onMessageLongClick = onMessageLongClick;
        this.contactResolver = contactResolver;
    }
    
    public InboxPagingAdapter(OnMessageClickListener onMessageClick, ContactResolver contactResolver) {
        this(onMessageClick, null, contactResolver);
    }
    
    @Override
    public MessageViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        ItemSmsMessageBinding binding = ItemSmsMessageBinding.inflate(
            LayoutInflater.from(parent.getContext()),
            parent,
            false
        );
        return new MessageViewHolder(binding);
    }
    
    @Override
    public void onBindViewHolder(MessageViewHolder holder, int position) {
        SmsEntity message = getItem(position);
        if (message != null) {
            holder.bind(message);
        }
    }
    
    class MessageViewHolder extends RecyclerView.ViewHolder {
        
        private final ItemSmsMessageBinding binding;
        
        public MessageViewHolder(ItemSmsMessageBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
        
        public void bind(SmsEntity message) {
            // Set message details
            String phoneNumber = message.getAddress();
            
            // Resolve contact name asynchronously
            disposables.add(
                contactResolver.getContactNameAsync(phoneNumber)
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(
                        contactName -> {
                            binding.tvContactName.setText(contactName);
                            // Cache the resolved name for future use
                        },
                        error -> {
                            // Fallback to phone number if contact resolution fails
                            binding.tvContactName.setText(phoneNumber != null ? phoneNumber : "Unknown");
                        }
                    )
            );
            
            binding.tvMessagePreview.setText(message.getBody());
            binding.tvMessageDate.setText(formatDate(message.getDate()));
            
            // Load contact photo if available
            disposables.add(
                contactResolver.getContactPhotoUriAsync(phoneNumber)
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(
                        photoUri -> {
                            if (photoUri != null) {
                                binding.ivContactAvatar.setImageURI(photoUri);
                                binding.ivContactAvatar.setVisibility(android.view.View.VISIBLE);
                            } else {
                                binding.ivContactAvatar.setVisibility(android.view.View.GONE);
                            }
                        },
                        error -> {
                            binding.ivContactAvatar.setVisibility(android.view.View.GONE);
                        }
                    )
            );
            
            // Style based on read status
            if (message.isUnread()) {
                binding.tvContactName.setTypeface(null, android.graphics.Typeface.BOLD);
                binding.tvMessagePreview.setTypeface(null, android.graphics.Typeface.BOLD);
                binding.getRoot().setAlpha(1.0f);
            } else {
                binding.tvContactName.setTypeface(null, android.graphics.Typeface.NORMAL);
                binding.tvMessagePreview.setTypeface(null, android.graphics.Typeface.NORMAL);
                binding.getRoot().setAlpha(0.7f);
            }
            
            // Message type indicator
            String messageType = message.getType();
            if ("SENT".equals(messageType)) {
                binding.ivMessageType.setImageResource(android.R.drawable.ic_menu_send);
                binding.ivMessageType.setVisibility(android.view.View.VISIBLE);
                binding.ivMessageType.setRotation(90);
            } else {
                binding.ivMessageType.setVisibility(android.view.View.GONE);
            }
            
            // Click listeners
            binding.getRoot().setOnClickListener(v -> {
                if (onMessageClick != null) {
                    onMessageClick.onMessageClick(message);
                }
            });
            
            binding.getRoot().setOnLongClickListener(v -> {
                if (onMessageLongClick != null) {
                    return onMessageLongClick.onMessageLongClick(message);
                }
                return false;
            });
        }
        
        private String formatDate(long timestamp) {
            Date messageDate = new Date(timestamp);
            Calendar messageCalendar = Calendar.getInstance();
            messageCalendar.setTime(messageDate);
            
            Calendar today = Calendar.getInstance();
            Calendar yesterday = Calendar.getInstance();
            yesterday.add(Calendar.DAY_OF_YEAR, -1);
            
            if (isSameDay(messageCalendar, today)) {
                return "Today " + todayFormat.format(messageDate);
            } else if (isSameDay(messageCalendar, yesterday)) {
                return "Yesterday " + timeFormat.format(messageDate);
            } else if (isThisWeek(messageCalendar)) {
                String dayOfWeek = new SimpleDateFormat("EEEE", Locale.getDefault()).format(messageDate);
                return dayOfWeek + " " + timeFormat.format(messageDate);
            } else {
                return dateFormat.format(messageDate);
            }
        }
        
        private boolean isSameDay(Calendar cal1, Calendar cal2) {
            return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                   cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR);
        }
        
        private boolean isThisWeek(Calendar messageDate) {
            Calendar today = Calendar.getInstance();
            Calendar weekAgo = Calendar.getInstance();
            weekAgo.add(Calendar.DAY_OF_YEAR, -7);
            return messageDate.after(weekAgo) && messageDate.before(today);
        }
    }
    
    /**
     * Clean up RxJava disposables
     */
    public void cleanup() {
        disposables.clear();
    }
}
