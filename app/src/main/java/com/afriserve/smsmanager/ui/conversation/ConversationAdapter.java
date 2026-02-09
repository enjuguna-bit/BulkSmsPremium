package com.afriserve.smsmanager.ui.conversation;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.ImageView;
import android.net.Uri;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;
import com.afriserve.smsmanager.R;
import com.afriserve.smsmanager.data.entity.SmsEntity;
import android.provider.Telephony;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class ConversationAdapter extends ListAdapter<SmsEntity, ConversationAdapter.ViewHolder> {
    
    private final OnMessageClickListener listener;
    private final OnMessageLongClickListener longClickListener;
    private final Context context;
    private final SimpleDateFormat timeFormat;
    private final SimpleDateFormat headerDateFormat;
    
    public interface OnMessageClickListener {
        void onMessageClick(SmsEntity message);
    }

    public interface OnMessageLongClickListener {
        void onMessageLongClick(SmsEntity message);
    }
    
    public ConversationAdapter(OnMessageClickListener listener, OnMessageLongClickListener longClickListener, Context context) {
        super(new DiffUtilCallback());
        this.listener = listener;
        this.longClickListener = longClickListener;
        this.context = context;
        this.timeFormat = new SimpleDateFormat("h:mm a", Locale.getDefault());
        this.headerDateFormat = new SimpleDateFormat("EEE, MMM d", Locale.getDefault());
    }
    
    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_conversation_message, parent, false);
        return new ViewHolder(view);
    }
    
    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        SmsEntity message = getItem(position);
        holder.bind(message);
    }
    
    class ViewHolder extends RecyclerView.ViewHolder {
        private final View layoutReceived;
        private final View layoutSent;
        private final TextView tvDateHeader;
        private final TextView tvReceivedMessage;
        private final TextView tvReceivedTime;
        private final ImageView ivReceivedMedia;
        private final TextView tvSentMessage;
        private final TextView tvSentTime;
        private final ImageView ivSentMedia;
        private final TextView tvSentStatus;
        
        ViewHolder(View itemView) {
            super(itemView);
            layoutReceived = itemView.findViewById(R.id.layoutReceived);
            layoutSent = itemView.findViewById(R.id.layoutSent);
            tvDateHeader = itemView.findViewById(R.id.tvDateHeader);
            tvReceivedMessage = itemView.findViewById(R.id.tvReceivedMessage);
            tvReceivedTime = itemView.findViewById(R.id.tvReceivedTime);
            ivReceivedMedia = itemView.findViewById(R.id.ivReceivedMedia);
            tvSentMessage = itemView.findViewById(R.id.tvSentMessage);
            tvSentTime = itemView.findViewById(R.id.tvSentTime);
            ivSentMedia = itemView.findViewById(R.id.ivSentMedia);
            tvSentStatus = itemView.findViewById(R.id.tvSentStatus);
        }
        
        void bind(SmsEntity message) {
            // Format time
            String time = timeFormat.format(new Date(message.createdAt));
            int position = getBindingAdapterPosition();
            if (position == RecyclerView.NO_POSITION) {
                return;
            }

            boolean showHeader = shouldShowDateHeader(position);
            if (tvDateHeader != null) {
                tvDateHeader.setVisibility(showHeader ? View.VISIBLE : View.GONE);
                if (showHeader) {
                    tvDateHeader.setText(formatDateHeader(message.createdAt));
                }
            }
            
            if (isOutgoingMessage(message)) {
                // Show sent message
                layoutSent.setVisibility(View.VISIBLE);
                layoutReceived.setVisibility(View.GONE);
                tvSentMessage.setText(buildDisplayMessage(message));
                tvSentTime.setText(time);
                if (tvSentStatus != null) {
                    tvSentStatus.setText(mapStatus(message.status));
                }
                bindMediaPreview(ivSentMedia, message);
                
                // Set click listener
                layoutSent.setOnClickListener(v -> {
                    if (listener != null) {
                        listener.onMessageClick(message);
                    }
                });
                layoutSent.setOnLongClickListener(v -> {
                    if (longClickListener != null) {
                        longClickListener.onMessageLongClick(message);
                        return true;
                    }
                    return false;
                });
            } else {
                // Show received message
                layoutReceived.setVisibility(View.VISIBLE);
                layoutSent.setVisibility(View.GONE);
                tvReceivedMessage.setText(buildDisplayMessage(message));
                tvReceivedTime.setText(time);
                bindMediaPreview(ivReceivedMedia, message);
                
                // Set click listener
                layoutReceived.setOnClickListener(v -> {
                    if (listener != null) {
                        listener.onMessageClick(message);
                    }
                });
                layoutReceived.setOnLongClickListener(v -> {
                    if (longClickListener != null) {
                        longClickListener.onMessageLongClick(message);
                        return true;
                    }
                    return false;
                });
            }
        }

        private boolean shouldShowDateHeader(int position) {
            if (position == 0) return true;
            SmsEntity current = getItem(position);
            SmsEntity previous = getItem(position - 1);
            if (current == null || previous == null) return false;

            Calendar c1 = Calendar.getInstance();
            Calendar c2 = Calendar.getInstance();
            c1.setTimeInMillis(current.createdAt);
            c2.setTimeInMillis(previous.createdAt);
            return c1.get(Calendar.YEAR) != c2.get(Calendar.YEAR)
                || c1.get(Calendar.DAY_OF_YEAR) != c2.get(Calendar.DAY_OF_YEAR);
        }

        private String formatDateHeader(long timestamp) {
            Calendar now = Calendar.getInstance();
            Calendar messageTime = Calendar.getInstance();
            messageTime.setTimeInMillis(timestamp);

            if (now.get(Calendar.YEAR) == messageTime.get(Calendar.YEAR) &&
                now.get(Calendar.DAY_OF_YEAR) == messageTime.get(Calendar.DAY_OF_YEAR)) {
                return "Today";
            }

            Calendar yesterday = Calendar.getInstance();
            yesterday.add(Calendar.DAY_OF_YEAR, -1);
            if (yesterday.get(Calendar.YEAR) == messageTime.get(Calendar.YEAR) &&
                yesterday.get(Calendar.DAY_OF_YEAR) == messageTime.get(Calendar.DAY_OF_YEAR)) {
                return "Yesterday";
            }

            return headerDateFormat.format(new Date(timestamp));
        }

        private boolean isOutgoingMessage(SmsEntity message) {
            if (message == null) return false;
            if (message.boxType != null) {
                return message.boxType != Telephony.Sms.MESSAGE_TYPE_INBOX;
            }
            String status = message.status != null ? message.status : "";
            return "SENT".equals(status) || "DELIVERED".equals(status) || "FAILED".equals(status)
                || "PENDING".equals(status);
        }

        private String mapStatus(String status) {
            if (status == null) return "";
            switch (status) {
                case "DELIVERED":
                    return "Delivered";
                case "FAILED":
                    return "Failed";
                case "SENT":
                    return "Sent";
                case "PENDING":
                    return "Sending";
                default:
                    return status;
            }
        }

        private void bindMediaPreview(ImageView view, SmsEntity message) {
            if (view == null || message == null) {
                return;
            }
            if (message.mediaUri != null && !message.mediaUri.trim().isEmpty()) {
                try {
                    view.setImageURI(Uri.parse(message.mediaUri));
                    view.setVisibility(View.VISIBLE);
                } catch (Exception e) {
                    view.setVisibility(View.GONE);
                }
            } else {
                view.setVisibility(View.GONE);
            }
        }

        private String buildDisplayMessage(SmsEntity message) {
            if (message == null) return "";
            String text = message.message != null ? message.message : "";
            if (message.isMms != null && message.isMms && message.attachmentCount != null && message.attachmentCount > 0) {
                text = text + " (" + message.attachmentCount + " attachments)";
            }
            return text;
        }
    }
    
    private static class DiffUtilCallback extends DiffUtil.ItemCallback<SmsEntity> {
        @Override
        public boolean areItemsTheSame(@NonNull SmsEntity oldItem, @NonNull SmsEntity newItem) {
            return oldItem.id == newItem.id;
        }
        
        @Override
        public boolean areContentsTheSame(@NonNull SmsEntity oldItem, @NonNull SmsEntity newItem) {
            return safeEquals(oldItem.message, newItem.message) &&
                   safeEquals(oldItem.status, newItem.status) &&
                   safeEquals(oldItem.boxType, newItem.boxType) &&
                   safeEquals(oldItem.isRead, newItem.isRead) &&
                   safeEquals(oldItem.deliveredAt, newItem.deliveredAt) &&
                   safeEquals(oldItem.isMms, newItem.isMms) &&
                   safeEquals(oldItem.mediaUri, newItem.mediaUri) &&
                   safeEquals(oldItem.attachmentCount, newItem.attachmentCount) &&
                   oldItem.createdAt == newItem.createdAt;
        }

        private boolean safeEquals(Object left, Object right) {
            if (left == null) return right == null;
            return left.equals(right);
        }
    }
}
