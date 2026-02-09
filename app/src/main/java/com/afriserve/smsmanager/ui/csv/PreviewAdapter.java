package com.afriserve.smsmanager.ui.csv;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.afriserve.smsmanager.R;
import com.afriserve.smsmanager.models.CsvRowModel;

public class PreviewAdapter extends ListAdapter<CsvRowModel, PreviewAdapter.ViewHolder> {

    public PreviewAdapter() {
        super(new DiffUtilCallback());
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_preview_message, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        CsvRowModel csvRow = getItem(position);
        holder.bind(csvRow);
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        private TextView textPhone, textMessage, textInfo;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            textPhone = itemView.findViewById(R.id.text_phone);
            textMessage = itemView.findViewById(R.id.text_message);
            textInfo = itemView.findViewById(R.id.text_info);
        }

        public void bind(CsvRowModel csvRow) {
            // Set phone number
            String phone = csvRow.getPhoneNumber();
            if (phone != null) {
                textPhone.setText(phone);
                textPhone.setVisibility(View.VISIBLE);
            } else {
                textPhone.setVisibility(View.GONE);
            }

            // Set preview message
            String message = csvRow.getPreviewMessage();
            if (message != null && !message.trim().isEmpty()) {
                textMessage.setText(message);
                textMessage.setVisibility(View.VISIBLE);
            } else {
                textMessage.setVisibility(View.GONE);
            }

            // Set additional info
            String info = csvRow.getDisplayInfo();
            if (info != null && !info.trim().isEmpty()) {
                textInfo.setText(info);
                textInfo.setVisibility(View.VISIBLE);
            } else {
                textInfo.setVisibility(View.GONE);
            }
        }
    }

    private static class DiffUtilCallback extends DiffUtil.ItemCallback<CsvRowModel> {
        @Override
        public boolean areItemsTheSame(@NonNull CsvRowModel oldItem, @NonNull CsvRowModel newItem) {
            return oldItem.getPhoneNumber() != null && 
                   oldItem.getPhoneNumber().equals(newItem.getPhoneNumber());
        }

        @Override
        public boolean areContentsTheSame(@NonNull CsvRowModel oldItem, @NonNull CsvRowModel newItem) {
            return oldItem.getPreviewMessage().equals(newItem.getPreviewMessage()) &&
                   oldItem.hasValidPhoneNumber() == newItem.hasValidPhoneNumber();
        }
    }
}
