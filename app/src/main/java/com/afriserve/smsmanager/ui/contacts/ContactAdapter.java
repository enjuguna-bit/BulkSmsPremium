package com.afriserve.smsmanager.ui.contacts;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.afriserve.smsmanager.R;
import com.afriserve.smsmanager.ui.contacts.ContactSearchDialog.ContactInfo;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Adapter for displaying contacts in search results
 */
public class ContactAdapter extends RecyclerView.Adapter<ContactAdapter.ViewHolder> {
    
    private List<ContactInfo> contacts;
    private Set<String> selectedContactIds;
    private Map<String, ContactInfo> knownContacts;
    private OnContactClickListener listener;
    private boolean allowMultipleSelection;
    
    public interface OnContactClickListener {
        void onContactClick(ContactInfo contact);
    }
    
    public ContactAdapter(List<ContactInfo> contacts, OnContactClickListener listener) {
        this.contacts = new ArrayList<>(contacts);
        this.listener = listener;
        this.selectedContactIds = new HashSet<>();
        this.knownContacts = new HashMap<>();
        for (ContactInfo contact : contacts) {
            knownContacts.put(contact.id, contact);
        }
        this.allowMultipleSelection = false;
    }
    
    public void setAllowMultipleSelection(boolean allowMultipleSelection) {
        this.allowMultipleSelection = allowMultipleSelection;
        if (!allowMultipleSelection) {
            selectedContactIds.clear();
        }
        notifyDataSetChanged();
    }
    
    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_contact, parent, false);
        return new ViewHolder(view);
    }
    
    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ContactInfo contact = contacts.get(position);
        holder.bind(contact);
    }
    
    @Override
    public int getItemCount() {
        return contacts.size();
    }
    
    public void updateContacts(List<ContactInfo> newContacts) {
        DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(new ContactDiffCallback(contacts, newContacts));
        contacts.clear();
        contacts.addAll(newContacts);
        for (ContactInfo contact : newContacts) {
            knownContacts.put(contact.id, contact);
        }
        diffResult.dispatchUpdatesTo(this);
    }
    
    public void toggleSelection(ContactInfo contact) {
        if (selectedContactIds.contains(contact.id)) {
            selectedContactIds.remove(contact.id);
        } else {
            selectedContactIds.add(contact.id);
            knownContacts.put(contact.id, contact);
        }
        notifyDataSetChanged();
    }

    public boolean isSelected(ContactInfo contact) {
        if (contact == null || contact.id == null) {
            return false;
        }
        return selectedContactIds.contains(contact.id);
    }

    public int getSelectedCount() {
        return selectedContactIds.size();
    }
    
    public List<ContactInfo> getSelectedContacts() {
        List<ContactInfo> selected = new ArrayList<>();
        for (String contactId : selectedContactIds) {
            ContactInfo contact = knownContacts.get(contactId);
            if (contact != null) {
                selected.add(contact);
            }
        }
        return selected;
    }
    
    public void clearSelection() {
        selectedContactIds.clear();
        notifyDataSetChanged();
    }
    
    class ViewHolder extends RecyclerView.ViewHolder {
        private ImageView imageViewContact;
        private TextView textViewName;
        private TextView textViewPhone;
        private ImageView imageViewSelected;
        
        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            imageViewContact = itemView.findViewById(R.id.imageViewContact);
            textViewName = itemView.findViewById(R.id.textViewName);
            textViewPhone = itemView.findViewById(R.id.textViewPhone);
            imageViewSelected = itemView.findViewById(R.id.imageViewSelected);
        }
        
        public void bind(ContactInfo contact) {
            textViewName.setText(contact.name != null ? contact.name : "Unknown");
            textViewPhone.setText(contact.phoneNumber != null ? contact.phoneNumber : "");
            
            // Load contact photo
            if (contact.photoUri != null && !contact.photoUri.isEmpty()) {
                try {
                    imageViewContact.setImageURI(android.net.Uri.parse(contact.photoUri));
                } catch (Exception e) {
                    // Use default avatar if photo loading fails
                    imageViewContact.setImageResource(R.drawable.ic_menu_contact);
                }
            } else {
                // Use default avatar
                imageViewContact.setImageResource(R.drawable.ic_menu_contact);
            }
            
            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onContactClick(contact);
                }
            });

            boolean isSelected = selectedContactIds.contains(contact.id);
            if (allowMultipleSelection) {
                imageViewSelected.setVisibility(View.VISIBLE);
                imageViewSelected.setAlpha(isSelected ? 1f : 0.3f);
            } else {
                imageViewSelected.setVisibility(View.GONE);
            }
        }
    }
    
    private static class ContactDiffCallback extends DiffUtil.Callback {
        private final List<ContactInfo> oldList;
        private final List<ContactInfo> newList;

        public ContactDiffCallback(List<ContactInfo> oldList, List<ContactInfo> newList) {
            this.oldList = oldList;
            this.newList = newList;
        }

        @Override
        public int getOldListSize() {
            return oldList != null ? oldList.size() : 0;
        }

        @Override
        public int getNewListSize() {
            return newList != null ? newList.size() : 0;
        }

        @Override
        public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
            ContactInfo oldItem = oldList.get(oldItemPosition);
            ContactInfo newItem = newList.get(newItemPosition);
            return oldItem.id.equals(newItem.id);
        }

        @Override
        public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
            ContactInfo oldItem = oldList.get(oldItemPosition);
            ContactInfo newItem = newList.get(newItemPosition);
            return (oldItem.name != null ? oldItem.name.equals(newItem.name) : newItem.name == null) &&
                   (oldItem.phoneNumber != null ? oldItem.phoneNumber.equals(newItem.phoneNumber) : newItem.phoneNumber == null) &&
                   (oldItem.email != null ? oldItem.email.equals(newItem.email) : newItem.email == null);
        }
    }
}
