package com.bulksms.smsmanager.databinding;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

public class ItemLoadStateFooterBindingManual {
    
    public final LinearLayout root;
    public final ProgressBar progressBar;
    public final TextView errorMessage;
    public final Button retryButton;
    
    private ItemLoadStateFooterBindingManual(LinearLayout root, ProgressBar progressBar, 
                                       TextView errorMessage, Button retryButton) {
        this.root = root;
        this.progressBar = progressBar;
        this.errorMessage = errorMessage;
        this.retryButton = retryButton;
    }
    
    public static ItemLoadStateFooterBindingManual inflate(LayoutInflater inflater, ViewGroup parent, boolean attachToParent) {
        View root = inflater.inflate(com.bulksms.smsmanager.R.layout.item_load_state_footer, parent, false);
        if (attachToParent) {
            parent.addView(root);
        }
        
        LinearLayout linearLayout = (LinearLayout) root;
        ProgressBar progressBar = root.findViewById(com.bulksms.smsmanager.R.id.progressBar);
        TextView errorMessage = root.findViewById(com.bulksms.smsmanager.R.id.errorMessage);
        Button retryButton = root.findViewById(com.bulksms.smsmanager.R.id.retryButton);
        
        return new ItemLoadStateFooterBindingManual(linearLayout, progressBar, errorMessage, retryButton);
    }
    
    public LinearLayout getRoot() {
        return root;
    }
}
