package com.bulksms.smsmanager.ui.contacts;

import android.view.LayoutInflater;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;
import com.bulksms.smsmanager.databinding.ItemContactBinding;
import com.bulksms.smsmanager.R;
import com.bumptech.glide.Glide;
import java.util.List;

/**
 * RecyclerView adapter for contact list
 */
public class ContactAdapter extends ListAdapter<ContactSearchViewModel.ContactInfo, ContactAdapter.ContactViewHolder> {
    
    private final OnContactClickListener listener;
    
    public interface OnContactClickListener {
        void onContactClick(ContactSearchViewModel.ContactInfo contact);
    }
    
    public ContactAdapter(OnContactClickListener listener) {
        super(new ContactDiffCallback());
        this.listener = listener;
    }
    
    @NonNull
    @Override
    public ContactViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemContactBinding binding = ItemContactBinding.inflate(
            LayoutInflater.from(parent.getContext()), parent, false);
        return new ContactViewHolder(binding);
    }
    
    @Override
    public void onBindViewHolder(@NonNull ContactViewHolder holder, int position) {
        ContactSearchViewModel.ContactInfo contact = getItem(position);
        holder.bind(contact);
    }
    
    class ContactViewHolder extends RecyclerView.ViewHolder {
        private final ItemContactBinding binding;
        
        ContactViewHolder(ItemContactBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
        
        void bind(ContactSearchViewModel.ContactInfo contact) {
            binding.textViewName.setText(contact.name);
            binding.textViewPhone.setText(contact.phoneNumber);
            
            // Load contact photo if available
            if (contact.photoUri != null) {
                Glide.with(binding.imageViewContact.getContext())
                    .load(contact.photoUri)
                    .circleCrop()
                    .placeholder(R.drawable.ic_menu_contact)
                    .into(binding.imageViewContact);
            } else {
                binding.imageViewContact.setImageResource(R.drawable.ic_menu_contact);
            }
            
            // Set click listener
            binding.getRoot().setOnClickListener(v -> {
                if (listener != null) {
                    listener.onContactClick(contact);
                }
            });
        }
    }
    
    private static class ContactDiffCallback extends DiffUtil.ItemCallback<ContactSearchViewModel.ContactInfo> {
        @Override
        public boolean areItemsTheSame(@NonNull ContactSearchViewModel.ContactInfo oldItem, 
                                     @NonNull ContactSearchViewModel.ContactInfo newItem) {
            return oldItem.id == newItem.id;
        }
        
        @Override
        public boolean areContentsTheSame(@NonNull ContactSearchViewModel.ContactInfo oldItem, 
                                          @NonNull ContactSearchViewModel.ContactInfo newItem) {
            return oldItem.name.equals(newItem.name) && 
                   oldItem.phoneNumber.equals(newItem.phoneNumber);
        }
    }
}
