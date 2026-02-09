package com.afriserve.smsmanager.ui.inbox;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.afriserve.smsmanager.R;
import com.afriserve.smsmanager.models.SmsModel;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import android.graphics.Typeface;

public class InboxAdapter extends ListAdapter<SmsModel, InboxAdapter.ViewHolder> {
    
    private OnItemClickListener onItemClickListener;

    public interface OnItemClickListener {
        void onItemClick(SmsModel smsModel);
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.onItemClickListener = listener;
    }

    public InboxAdapter() {
        super(new DiffUtilCallback());
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_conversation, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        SmsModel smsModel = getItem(position);
        holder.bind(smsModel);
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        private TextView tvContactName, tvLastMessage, tvTimestamp, tvUnreadBadge;
        private TextView ivContactPhoto;
        private ImageView ivPinned, ivArchived;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvContactName = itemView.findViewById(R.id.tvContactName);
            tvLastMessage = itemView.findViewById(R.id.tvLastMessage);
            tvTimestamp = itemView.findViewById(R.id.tvTimestamp);
            tvUnreadBadge = itemView.findViewById(R.id.tvUnreadBadge);
            ivContactPhoto = itemView.findViewById(R.id.ivContactPhoto);
            ivPinned = itemView.findViewById(R.id.ivPinned);
            ivArchived = itemView.findViewById(R.id.ivArchived);
        }

        public void bind(SmsModel smsModel) {
            // Set sender/recipient
            String sender = smsModel.getAddress();
            if (sender == null || sender.trim().isEmpty()) {
                sender = "Unknown";
            }
            tvContactName.setText(sender);

            // Set message preview
            String body = smsModel.getBody();
            if (body == null || body.trim().isEmpty()) {
                tvLastMessage.setText("(No content)");
            } else {
                tvLastMessage.setText(body);
                if (body.length() > 50) {
                    tvLastMessage.setText(body.substring(0, 50) + "...");
                }
            }

            // Set time
            String time = formatTime(smsModel.getDate());
            tvTimestamp.setText(time);

            // Set contact avatar (first letter of name)
            String firstLetter = sender.isEmpty() ? "U" : sender.substring(0, 1).toUpperCase();
            ivContactPhoto.setText(firstLetter);

            // Set unread badge
            if (!smsModel.isRead() && smsModel.getType() == 1) { // TYPE_INBOX = 1
                tvUnreadBadge.setVisibility(View.VISIBLE);
            } else {
                tvUnreadBadge.setVisibility(View.GONE);
            }

            // Hide pinned and archived indicators for now
            ivPinned.setVisibility(View.GONE);
            ivArchived.setVisibility(View.GONE);

            // Set click listener
            itemView.setOnClickListener(v -> {
                if (onItemClickListener != null) {
                    onItemClickListener.onItemClick(smsModel);
                }
            });

            // Set unread styling
            if (!smsModel.isRead() && smsModel.getType() == 1) {
                tvContactName.setTypeface(null, Typeface.BOLD);
                tvLastMessage.setTypeface(null, Typeface.BOLD);
            } else {
                tvContactName.setTypeface(null, Typeface.NORMAL);
                tvLastMessage.setTypeface(null, Typeface.NORMAL);
            }
        }

        private String formatTime(String timestamp) {
            try {
                long time = Long.parseLong(timestamp);
                Date date = new Date(time);
                
                SimpleDateFormat sdf;
                long now = System.currentTimeMillis();
                long diff = now - time;
                
                // If less than 24 hours, show time only
                if (diff < 24 * 60 * 60 * 1000) {
                    sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
                } else if (diff < 7 * 24 * 60 * 60 * 1000) {
                    // If less than a week, show day of week
                    sdf = new SimpleDateFormat("EEE", Locale.getDefault());
                } else {
                    // Otherwise show date
                    sdf = new SimpleDateFormat("MMM dd", Locale.getDefault());
                }
                
                return sdf.format(date);
            } catch (Exception e) {
                return "";
            }
        }
    }

    private static class DiffUtilCallback extends DiffUtil.ItemCallback<SmsModel> {
        @Override
        public boolean areItemsTheSame(@NonNull SmsModel oldItem, @NonNull SmsModel newItem) {
            return oldItem.getId() != null && oldItem.getId().equals(newItem.getId());
        }

        @Override
        public boolean areContentsTheSame(@NonNull SmsModel oldItem, @NonNull SmsModel newItem) {
            return oldItem.getAddress().equals(newItem.getAddress()) &&
                   oldItem.getBody().equals(newItem.getBody()) &&
                   oldItem.getDate().equals(newItem.getDate()) &&
                   oldItem.isRead() == newItem.isRead() &&
                   oldItem.getType() == newItem.getType();
        }
    }
}
