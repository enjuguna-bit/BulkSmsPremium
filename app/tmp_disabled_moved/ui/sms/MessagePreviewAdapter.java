package com.bulksms.smsmanager.ui.sms;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.bulksms.smsmanager.R;
import com.bulksms.smsmanager.data.parser.TemplateVariableExtractor.MessagePreview;
import java.util.List;

/**
 * Adapter for message preview items
 * Shows how the template will be rendered for actual recipients
 */
public class MessagePreviewAdapter extends RecyclerView.Adapter<MessagePreviewAdapter.PreviewViewHolder> {
    
    private List<MessagePreview> previews;
    
    public MessagePreviewAdapter(List<MessagePreview> previews) {
        this.previews = previews;
    }
    
    @NonNull
    @Override
    public PreviewViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_message_preview, parent, false);
        return new PreviewViewHolder(view);
    }
    
    @Override
    public void onBindViewHolder(@NonNull PreviewViewHolder holder, int position) {
        MessagePreview preview = previews.get(position);
        holder.bind(preview);
    }
    
    @Override
    public int getItemCount() {
        return previews != null ? previews.size() : 0;
    }
    
    public void updatePreviews(List<MessagePreview> newPreviews) {
        this.previews = newPreviews;
        notifyDataSetChanged();
    }
    
    class PreviewViewHolder extends RecyclerView.ViewHolder {
        private TextView txtRecipientName;
        private TextView txtPhoneNumber;
        private TextView txtMessage;
        
        public PreviewViewHolder(@NonNull View itemView) {
            super(itemView);
            txtRecipientName = itemView.findViewById(R.id.txtRecipientName);
            txtPhoneNumber = itemView.findViewById(R.id.txtPhoneNumber);
            txtMessage = itemView.findViewById(R.id.txtMessage);
        }
        
        public void bind(MessagePreview preview) {
            txtRecipientName.setText(preview.recipientName);
            txtPhoneNumber.setText(preview.phoneNumber);
            txtMessage.setText(preview.message);
        }
    }
}
