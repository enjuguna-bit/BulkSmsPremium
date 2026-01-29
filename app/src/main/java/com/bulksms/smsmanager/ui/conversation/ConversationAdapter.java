package com.bulksms.smsmanager.ui.conversation;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;
import com.bulksms.smsmanager.R;
import com.bulksms.smsmanager.data.entity.SmsEntity;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class ConversationAdapter extends ListAdapter<SmsEntity, ConversationAdapter.ViewHolder> {
    
    private final OnMessageClickListener listener;
    private final Context context;
    private final SimpleDateFormat timeFormat;
    
    public interface OnMessageClickListener {
        void onMessageClick(SmsEntity message);
    }
    
    public ConversationAdapter(OnMessageClickListener listener, Context context) {
        super(new DiffUtilCallback());
        this.listener = listener;
        this.context = context;
        this.timeFormat = new SimpleDateFormat("h:mm a", Locale.getDefault());
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
        private final TextView tvReceivedMessage;
        private final TextView tvReceivedTime;
        private final TextView tvSentMessage;
        private final TextView tvSentTime;
        
        ViewHolder(View itemView) {
            super(itemView);
            layoutReceived = itemView.findViewById(R.id.layoutReceived);
            layoutSent = itemView.findViewById(R.id.layoutSent);
            tvReceivedMessage = itemView.findViewById(R.id.tvReceivedMessage);
            tvReceivedTime = itemView.findViewById(R.id.tvReceivedTime);
            tvSentMessage = itemView.findViewById(R.id.tvSentMessage);
            tvSentTime = itemView.findViewById(R.id.tvSentTime);
        }
        
        void bind(SmsEntity message) {
            // Format time
            String time = timeFormat.format(new Date(message.createdAt));
            
            if ("SENT".equals(message.status)) {
                // Show sent message
                layoutSent.setVisibility(View.VISIBLE);
                layoutReceived.setVisibility(View.GONE);
                tvSentMessage.setText(message.message);
                tvSentTime.setText(time);
                
                // Set click listener
                layoutSent.setOnClickListener(v -> {
                    if (listener != null) {
                        listener.onMessageClick(message);
                    }
                });
            } else {
                // Show received message
                layoutReceived.setVisibility(View.VISIBLE);
                layoutSent.setVisibility(View.GONE);
                tvReceivedMessage.setText(message.message);
                tvReceivedTime.setText(time);
                
                // Set click listener
                layoutReceived.setOnClickListener(v -> {
                    if (listener != null) {
                        listener.onMessageClick(message);
                    }
                });
            }
        }
    }
    
    private static class DiffUtilCallback extends DiffUtil.ItemCallback<SmsEntity> {
        @Override
        public boolean areItemsTheSame(@NonNull SmsEntity oldItem, @NonNull SmsEntity newItem) {
            return oldItem.id == newItem.id;
        }
        
        @Override
        public boolean areContentsTheSame(@NonNull SmsEntity oldItem, @NonNull SmsEntity newItem) {
            return oldItem.message.equals(newItem.message) &&
                   oldItem.status.equals(newItem.status) &&
                   oldItem.createdAt == newItem.createdAt;
        }
    }
}
