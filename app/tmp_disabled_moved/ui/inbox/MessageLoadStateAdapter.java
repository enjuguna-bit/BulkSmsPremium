package com.bulksms.smsmanager.ui.inbox;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.paging.LoadState;
import androidx.paging.LoadStateAdapter;
import androidx.recyclerview.widget.RecyclerView;
import com.bulksms.smsmanager.databinding.ItemLoadStateFooterBinding;

public class MessageLoadStateAdapter extends LoadStateAdapter<MessageLoadStateAdapter.LoadStateViewHolder> {
    
    private final View.OnClickListener retryCallback;
    
    public MessageLoadStateAdapter(View.OnClickListener retryCallback) {
        this.retryCallback = retryCallback;
    }
    
    @NonNull
    @Override
    public LoadStateViewHolder onCreateViewHolder(@NonNull ViewGroup parent, @NonNull LoadState loadState) {
        ItemLoadStateFooterBinding binding = ItemLoadStateFooterBinding.inflate(
            LayoutInflater.from(parent.getContext()),
            parent,
            false
        );
        return new LoadStateViewHolder(binding, retryCallback);
    }
    
    @Override
    public void onBindViewHolder(@NonNull LoadStateViewHolder holder, @NonNull LoadState loadState) {
        holder.bind(loadState);
    }
    
    public static class LoadStateViewHolder extends RecyclerView.ViewHolder {
        
        private final ItemLoadStateFooterBinding binding;
        
        public LoadStateViewHolder(@NonNull ItemLoadStateFooterBinding binding, View.OnClickListener retryCallback) {
            super(binding.getRoot());
            this.binding = binding;
            
            binding.retryButton.setOnClickListener(retryCallback);
        }
        
        public void bind(LoadState loadState) {
            if (loadState instanceof LoadState.Loading) {
                binding.progressBar.setVisibility(View.VISIBLE);
                binding.retryButton.setVisibility(View.GONE);
                binding.errorMessage.setVisibility(View.GONE);
            } else if (loadState instanceof LoadState.Error) {
                binding.progressBar.setVisibility(View.GONE);
                binding.retryButton.setVisibility(View.VISIBLE);
                binding.errorMessage.setVisibility(View.VISIBLE);
                
                // Handle LoadState.Error properly
                LoadState.Error errorState = (LoadState.Error) loadState;
                Throwable error = errorState.getError();
                if (error != null) {
                    String errorMessage = error.getLocalizedMessage();
                    if (errorMessage == null) {
                        errorMessage = "Unknown error occurred";
                    }
                    binding.errorMessage.setText(errorMessage);
                } else {
                    binding.errorMessage.setText("Unknown error occurred");
                }
            } else {
                binding.progressBar.setVisibility(View.GONE);
                binding.retryButton.setVisibility(View.GONE);
                binding.errorMessage.setVisibility(View.GONE);
            }
        }
    }
}
