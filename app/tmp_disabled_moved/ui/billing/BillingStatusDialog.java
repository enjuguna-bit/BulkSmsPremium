package com.bulksms.smsmanager.ui.billing;

import android.app.AlertDialog;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import com.bulksms.smsmanager.R;


/**
 * Dialog showing billing status
 */
public class BillingStatusDialog {
    private final Context context;
    private AlertDialog dialog;
    
    public BillingStatusDialog(Context context) {
        this.context = context;
    }
    
    public void show() {
        androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(context);
        builder.setTitle("Subscription Status")
               .setMessage("Billing and subscription features have been removed.")
               .setPositiveButton("OK", (dialog, which) -> dismiss());
        dialog = builder.create();
        dialog.show();
    }
    
    public void dismiss() {
        if (dialog != null && dialog.isShowing()) {
            dialog.dismiss();
        }
    }
}
