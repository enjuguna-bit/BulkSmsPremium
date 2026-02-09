package com.bulksms.smsmanager.databinding;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import com.google.android.material.card.MaterialCardView;

public class ItemSmsMessageBindingManual {
    
    public final MaterialCardView root;
    public final ImageView ivContactAvatar;
    public final TextView tvContactName;
    public final TextView tvMessageDate;
    public final TextView tvMessagePreview;
    public final ImageView ivMessageType;
    
    private ItemSmsMessageBindingManual(MaterialCardView root, ImageView ivContactAvatar, 
                                 TextView tvContactName, TextView tvMessageDate, 
                                 TextView tvMessagePreview, ImageView ivMessageType) {
        this.root = root;
        this.ivContactAvatar = ivContactAvatar;
        this.tvContactName = tvContactName;
        this.tvMessageDate = tvMessageDate;
        this.tvMessagePreview = tvMessagePreview;
        this.ivMessageType = ivMessageType;
    }
    
    public static ItemSmsMessageBindingManual inflate(LayoutInflater inflater, ViewGroup parent, boolean attachToParent) {
        View root = inflater.inflate(com.bulksms.smsmanager.R.layout.item_sms_message, parent, false);
        if (attachToParent) {
            parent.addView(root);
        }
        
        MaterialCardView cardView = (MaterialCardView) root;
        ImageView ivContactAvatar = root.findViewById(com.bulksms.smsmanager.R.id.ivContactAvatar);
        TextView tvContactName = root.findViewById(com.bulksms.smsmanager.R.id.tvContactName);
        TextView tvMessageDate = root.findViewById(com.bulksms.smsmanager.R.id.tvMessageDate);
        TextView tvMessagePreview = root.findViewById(com.bulksms.smsmanager.R.id.tvMessagePreview);
        ImageView ivMessageType = root.findViewById(com.bulksms.smsmanager.R.id.ivMessageType);
        
        return new ItemSmsMessageBindingManual(cardView, ivContactAvatar, tvContactName, 
                                      tvMessageDate, tvMessagePreview, ivMessageType);
    }
    
    public MaterialCardView getRoot() {
        return root;
    }
}
