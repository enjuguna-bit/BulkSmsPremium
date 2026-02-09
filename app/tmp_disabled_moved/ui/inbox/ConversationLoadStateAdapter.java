package com.bulksms.smsmanager.ui.inbox;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.paging.LoadState;
import androidx.paging.LoadStateAdapter;
import androidx.recyclerview.widget.RecyclerView;
import com.bulksms.smsmanager.databinding.ItemLoadStateHeaderFooterBinding;

/**
 * LoadStateAdapter for handling both header and footer loading states
 */
public class ConversationLoadStateAdapter extends LoadStateAdapter<ConversationLoadStateAdapter.LoadStateViewHolder> {
    
    @Nullable
    private final View.OnClickListener retryCallback;
    
    public ConversationLoadStateAdapter(@Nullable View.OnClickListener retryCallback) {
        this.retryCallback = retryCallback;
    }
    
    @NonNull
    @Override
    public LoadStateViewHolder onCreateViewHolder(@NonNull ViewGroup parent, @NonNull LoadState loadState) {
        ItemLoadStateHeaderFooterBinding binding = ItemLoadStateHeaderFooterBinding.inflate(
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
    
    /**
     * ViewHolder for load state items
     */
    public static class LoadStateViewHolder extends RecyclerView.ViewHolder {
        private final ItemLoadStateHeaderFooterBinding binding;
        private final View.OnClickListener retryCallback;
        
        public LoadStateViewHolder(@NonNull ItemLoadStateHeaderFooterBinding binding, 
                                 @Nullable View.OnClickListener retryCallback) {
            super(binding.getRoot());
            this.binding = binding;
            this.retryCallback = retryCallback;
        }
        
        public void bind(@NonNull LoadState loadState) {
            if (loadState instanceof LoadState.Loading) {
                binding.progressBar.setVisibility(View.VISIBLE);
                binding.errorMessage.setVisibility(View.GONE);
                binding.retryButton.setVisibility(View.GONE);
            } else if (loadState instanceof LoadState.Error) {
                binding.progressBar.setVisibility(View.GONE);
                binding.errorMessage.setVisibility(View.VISIBLE);
                binding.retryButton.setVisibility(View.VISIBLE);
                
                Throwable error = ((LoadState.Error) loadState).getError();
                binding.errorMessage.setText(error.getMessage() != null ? error.getMessage() : "Unknown error");
                
                if (retryCallback != null) {
                    binding.retryButton.setOnClickListener(v -> retryCallback.onClick(v));
                }
            } else {
                // LoadState.NotLoading
                binding.progressBar.setVisibility(View.GONE);
                binding.errorMessage.setVisibility(View.GONE);
                binding.retryButton.setVisibility(View.GONE);
            }
        }
    }
}
