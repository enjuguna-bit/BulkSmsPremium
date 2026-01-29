package com.bulksms.smsmanager.ui.billing;

import android.app.AlertDialog;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import com.bulksms.smsmanager.R;
import com.bulksms.smsmanager.billing.SubscriptionManager;

/**
 * Dialog showing billing status
 */
public class BillingStatusDialog {
    private final Context context;
    private final SubscriptionManager subscriptionManager;
    private AlertDialog dialog;
    
    public BillingStatusDialog(Context context) {
        this.context = context;
        this.subscriptionManager = SubscriptionManager.getInstance(context);
    }
    
    public void show() {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        LayoutInflater inflater = LayoutInflater.from(context);
        View view = inflater.inflate(R.layout.dialog_billing_status, null);
        
        TextView txtStatus = view.findViewById(R.id.txtStatus);
        TextView txtPlan = view.findViewById(R.id.txtPlan);
        TextView txtExpiry = view.findViewById(R.id.txtExpiry);
        TextView txtFeatures = view.findViewById(R.id.txtFeatures);
        
        // Mock data - in real implementation, get from subscription manager
        txtStatus.setText("Status: Active");
        txtPlan.setText("Plan: Premium");
        txtExpiry.setText("Expires: 30 days");
        txtFeatures.setText("• Unlimited SMS\n• Advanced analytics\n• Priority support");
        
        builder.setView(view)
               .setTitle("Subscription Status")
               .setPositiveButton("OK", (dialog, which) -> dismiss())
               .setNegativeButton("Upgrade", (dialog, which) -> {
                   // Handle upgrade
                   dismiss();
               });
        
        dialog = builder.create();
        dialog.show();
    }
    
    public void dismiss() {
        if (dialog != null && dialog.isShowing()) {
            dialog.dismiss();
        }
    }
}
