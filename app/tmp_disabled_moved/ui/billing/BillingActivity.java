package com.bulksms.smsmanager.ui.billing;

import android.os.Bundle;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

/**
 * Billing UI removed - placeholder activity
 */
public class BillingActivity extends AppCompatActivity {
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        TextView tv = new TextView(this);
        tv.setText("Billing and subscription features have been removed.");
        tv.setPadding(32, 32, 32, 32);
        setContentView(tv);
    }
}
