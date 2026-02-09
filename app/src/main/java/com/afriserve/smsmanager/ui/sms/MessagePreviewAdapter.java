package com.afriserve.smsmanager.ui.sms;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.afriserve.smsmanager.R;
import com.afriserve.smsmanager.data.parser.TemplateVariableExtractor;

import java.util.List;

public class MessagePreviewAdapter extends ListAdapter<TemplateVariableExtractor.MessagePreview, MessagePreviewAdapter.ViewHolder> {

    public MessagePreviewAdapter(List<TemplateVariableExtractor.MessagePreview> previews) {
        super(new DiffUtilCallback());
        submitList(previews);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_message_preview, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        TemplateVariableExtractor.MessagePreview preview = getItem(position);
        holder.bind(preview);
    }

    public void updatePreviews(List<TemplateVariableExtractor.MessagePreview> previews) {
        submitList(previews);
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        private TextView txtRecipientName, txtPhoneNumber, txtMessage;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            txtRecipientName = itemView.findViewById(R.id.txtRecipientName);
            txtPhoneNumber = itemView.findViewById(R.id.txtPhoneNumber);
            txtMessage = itemView.findViewById(R.id.txtMessage);
        }

        public void bind(TemplateVariableExtractor.MessagePreview preview) {
            txtRecipientName.setText(preview.recipientName);
            txtPhoneNumber.setText(preview.phoneNumber);
            txtMessage.setText(preview.processedMessage);
        }
    }

    private static class DiffUtilCallback extends DiffUtil.ItemCallback<TemplateVariableExtractor.MessagePreview> {
        @Override
        public boolean areItemsTheSame(@NonNull TemplateVariableExtractor.MessagePreview oldItem, @NonNull TemplateVariableExtractor.MessagePreview newItem) {
            return oldItem.recipientName.equals(newItem.recipientName);
        }

        @Override
        public boolean areContentsTheSame(@NonNull TemplateVariableExtractor.MessagePreview oldItem, @NonNull TemplateVariableExtractor.MessagePreview newItem) {
            return oldItem.recipientName.equals(newItem.recipientName) &&
                   oldItem.processedMessage.equals(newItem.processedMessage);
        }
    }
}
