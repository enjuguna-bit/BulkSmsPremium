package com.bulksms.smsmanager.ui.billing;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import java.util.Locale;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.bulksms.smsmanager.R;
import com.bulksms.smsmanager.analytics.FirebaseAnalyticsHelper;
import com.bulksms.smsmanager.auth.SecureStorageEnhanced;
import com.bulksms.smsmanager.billing.BillingViewModel;
import com.bulksms.smsmanager.billing.FirebaseBillingManager;
import com.bulksms.smsmanager.billing.PlanLimits;
import com.bulksms.smsmanager.billing.SubscriptionInfo;
import com.bulksms.smsmanager.billing.SubscriptionPlans;
import com.bulksms.smsmanager.billing.UsageStats;

import java.util.concurrent.CompletableFuture;

import javax.inject.Inject;

/**
 * Streamlined Billing Activity
 */
public class BillingActivity extends AppCompatActivity {
    
    @Inject
    FirebaseAnalyticsHelper analyticsHelper;
    
    @Inject
    FirebaseBillingManager billingManager;
    
    private SwipeRefreshLayout swipeRefreshLayout;
    private ProgressBar progressBar;
    private TextView tvCurrentPlan, tvPlanStatus, tvPlanPrice, tvPlanFeatures;
    private TextView tvDailyUsage, tvDailyLimit, tvMonthlyUsage, tvMonthlyLimit;
    private ProgressBar progressBarDaily, progressBarMonthly;
    private Button btnManualActivation, btnUpgrade, btnCancelSubscription;
    
    private BillingViewModel viewModel;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_billing);
        
        // Initialize dependencies (in real app, this would be handled by Dagger)
        initializeDependencies();
        
        setupUI();
        observeViewModel();
    }
    
    private void initializeDependencies() {
        // Mock dependency injection - in real app, use Dagger Hilt
        analyticsHelper = new FirebaseAnalyticsHelper(this);
        billingManager = new FirebaseBillingManager(new SecureStorageEnhanced(this));
        
        // Create ViewModel
        BillingViewModel.Factory factory = new BillingViewModel.Factory(billingManager);
        viewModel = new ViewModelProvider(this, factory).get(BillingViewModel.class);
    }
    
    private void setupUI() {
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout);
        progressBar = findViewById(R.id.progressBar);
        tvCurrentPlan = findViewById(R.id.tvCurrentPlan);
        tvPlanStatus = findViewById(R.id.tvPlanStatus);
        tvPlanPrice = findViewById(R.id.tvPlanPrice);
        tvPlanFeatures = findViewById(R.id.tvPlanFeatures);
        tvDailyUsage = findViewById(R.id.tvDailyUsage);
        tvDailyLimit = findViewById(R.id.tvDailyLimit);
        tvMonthlyUsage = findViewById(R.id.tvMonthlyUsage);
        tvMonthlyLimit = findViewById(R.id.tvMonthlyLimit);
        progressBarDaily = findViewById(R.id.progressBarDaily);
        progressBarMonthly = findViewById(R.id.progressBarMonthly);
        btnManualActivation = findViewById(R.id.btnManualActivation);
        btnUpgrade = findViewById(R.id.btnUpgrade);
        btnCancelSubscription = findViewById(R.id.btnCancelSubscription);
        
        swipeRefreshLayout.setOnRefreshListener(() -> viewModel.refreshData());
        
        btnManualActivation.setOnClickListener(v -> showMpesaDialog());
        btnUpgrade.setOnClickListener(v -> showPlansDialog());
        btnCancelSubscription.setOnClickListener(v -> showCancelConfirmation());
    }
    
    private void observeViewModel() {
        viewModel.getSubscription().observe(this, this::updateSubscriptionUI);
        viewModel.getUsageStats().observe(this, this::updateUsageUI);
        viewModel.getLoading().observe(this, isLoading -> {
            swipeRefreshLayout.setRefreshing(isLoading);
            progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        });
        viewModel.getError().observe(this, this::showError);
    }
    
    private void updateSubscriptionUI(SubscriptionInfo subscription) {
        if (subscription != null) {
            tvCurrentPlan.setText(getPlanName(subscription.getPlanId()));
            tvPlanStatus.setText(capitalize(subscription.getStatus()));
            tvPlanStatus.setTextColor(getStatusColor(subscription.getStatus()));
            tvPlanPrice.setText("KES " + subscription.getAmount() + " / " + getBillingPeriod(subscription.getPlanId()));
            
            // Features
            tvPlanFeatures.setText(getPlanFeatures(subscription.getPlanId()));
            
            // Show/hide buttons
            switch (subscription.getStatus()) {
                case FirebaseBillingManager.STATUS_ACTIVE:
                    btnCancelSubscription.setVisibility(View.VISIBLE);
                    btnUpgrade.setText("Change Plan");
                    break;
                case FirebaseBillingManager.STATUS_EXPIRED:
                    btnCancelSubscription.setVisibility(View.GONE);
                    btnUpgrade.setText("Renew");
                    break;
                default:
                    btnCancelSubscription.setVisibility(View.GONE);
                    btnUpgrade.setText("Upgrade");
                    break;
            }
        }
    }
    
    private void updateUsageUI(UsageStats usage) {
        SubscriptionInfo subscription = viewModel.getSubscription().getValue();
        if (subscription != null) {
            PlanLimits limits = SubscriptionPlans.getLimitsForPlan(subscription.getPlanId());
            
            // Daily
            tvDailyUsage.setText(usage.getDailySms() + " SMS");
            tvDailyLimit.setText("/ " + limits.getDailySms());
            progressBarDaily.setProgress(calculateProgress(usage.getDailySms(), limits.getDailySms()));
            
            // Monthly
            tvMonthlyUsage.setText(usage.getMonthlySms() + " SMS");
            tvMonthlyLimit.setText("/ " + limits.getMonthlySms());
            progressBarMonthly.setProgress(calculateProgress(usage.getMonthlySms(), limits.getMonthlySms()));
        }
    }
    
    private void showMpesaDialog() {
        final EditText input = new EditText(this);
        input.setHint("Paste your M-Pesa confirmation message");
        
        new AlertDialog.Builder(this)
            .setTitle("Activate Subscription")
            .setView(input)
            .setPositiveButton("Activate", (dialog, which) -> {
                String message = input.getText().toString();
                if (message != null && !message.trim().isEmpty()) {
                    processMpesaMessage(message.trim());
                } else {
                    Toast.makeText(this, "Please enter M-Pesa message", Toast.LENGTH_SHORT).show();
                }
            })
            .setNegativeButton("Cancel", null)
            .show();
    }
    
    private void processMpesaMessage(String message) {
        // Parse M-Pesa message (implement your parsing logic)
        MpesaTransaction parsedTransaction = parseMpesaMessage(message);
        if (parsedTransaction != null) {
            progressBar.setVisibility(View.VISIBLE);
            
            viewModel.activateSubscription(
                parsedTransaction.getAmount(),
                parsedTransaction.getTransactionId()
            ).thenAccept(result -> {
                progressBar.setVisibility(View.GONE);
                
                if (result instanceof BillingViewModel.BillingResult.Success) {
                    BillingViewModel.BillingResult.Success success = 
                        (BillingViewModel.BillingResult.Success) result;
                    Toast.makeText(this, success.getMessage(), Toast.LENGTH_LONG).show();
                } else if (result instanceof BillingViewModel.BillingResult.Error) {
                    BillingViewModel.BillingResult.Error error = 
                        (BillingViewModel.BillingResult.Error) result;
                    Toast.makeText(this, error.getMessage(), Toast.LENGTH_LONG).show();
                }
            }).exceptionally(throwable -> {
                progressBar.setVisibility(View.GONE);
                Toast.makeText(this, "Error: " + throwable.getMessage(), Toast.LENGTH_LONG).show();
                return null;
            });
        } else {
            Toast.makeText(this, "Could not parse M-Pesa message", Toast.LENGTH_LONG).show();
        }
    }
    
    private void showPlansDialog() {
        // Show available plans dialog
        String[] plans = {"Basic - KES 500/month", "Pro - KES 1500/month", "Enterprise - KES 5000/month"};
        
        new AlertDialog.Builder(this)
            .setTitle("Choose Plan")
            .setItems(plans, (dialog, which) -> {
                String planId;
                switch (which) {
                    case 0: planId = FirebaseBillingManager.PLAN_BASIC; break;
                    case 1: planId = FirebaseBillingManager.PLAN_PRO; break;
                    case 2: planId = FirebaseBillingManager.PLAN_ENTERPRISE; break;
                    default: planId = FirebaseBillingManager.PLAN_BASIC; break;
                }
                showMpesaDialog(); // Show payment dialog
            })
            .setNegativeButton("Cancel", null)
            .show();
    }
    
    private void showCancelConfirmation() {
        SubscriptionInfo currentSubscription = viewModel.getSubscription().getValue();
        if (currentSubscription == null) {
            Toast.makeText(this, "No active subscription", Toast.LENGTH_SHORT).show();
            return;
        }
        
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Cancel Subscription");
        builder.setMessage("Are you sure you want to cancel your subscription? You will lose access to premium features at the end of your billing period.");
        
        // Add refund information
        if (currentSubscription.getAmount() > 0) {
            long daysLeft = (currentSubscription.getEndDate() - System.currentTimeMillis()) / (24 * 60 * 60 * 1000);
            if (daysLeft > 0) {
                double refundAmount = currentSubscription.getAmount() * (daysLeft / 30.0); // Approximate
                builder.setMessage("Are you sure you want to cancel your subscription? You will lose access to premium features at the end of your billing period." + 
                    String.format(Locale.getDefault(), "\n\nRefund: KES %.2f", refundAmount));
            }
        }
        
        builder.setPositiveButton("Cancel Subscription", (dialog, which) -> {
            progressBar.setVisibility(View.VISIBLE);
            
            viewModel.cancelSubscription(currentSubscription.getId(), "user_requested")
                .thenAccept(result -> {
                    progressBar.setVisibility(View.GONE);
                    if (result instanceof BillingViewModel.BillingResult.Success) {
                        Toast.makeText(this, "Subscription cancelled successfully", Toast.LENGTH_LONG).show();
                        viewModel.refreshData();
                    } else {
                        Toast.makeText(this, "Failed to cancel subscription", Toast.LENGTH_LONG).show();
                    }
                })
                .exceptionally(throwable -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(this, "Error: " + throwable.getMessage(), Toast.LENGTH_LONG).show();
                    return null;
                });
        });
        
        builder.setNegativeButton("Keep Subscription", null);
        builder.show();
    }
    
    // Helper methods
    private String getPlanName(String planId) {
        switch (planId) {
            case FirebaseBillingManager.PLAN_FREE: return "Free Plan";
            case FirebaseBillingManager.PLAN_BASIC: return "Basic Plan";
            case FirebaseBillingManager.PLAN_PRO: return "Pro Plan";
            case FirebaseBillingManager.PLAN_ENTERPRISE: return "Enterprise Plan";
            default: return "Unknown Plan";
        }
    }
    
    private int getStatusColor(String status) {
        switch (status) {
            case FirebaseBillingManager.STATUS_ACTIVE: 
                return ContextCompat.getColor(this, R.color.success_green);
            case FirebaseBillingManager.STATUS_EXPIRED: 
                return ContextCompat.getColor(this, R.color.error_red);
            default: 
                return ContextCompat.getColor(this, R.color.warning_orange);
        }
    }
    
    private String getBillingPeriod(String planId) {
        switch (planId) {
            case FirebaseBillingManager.PLAN_FREE: return "forever";
            case FirebaseBillingManager.PLAN_BASIC:
            case FirebaseBillingManager.PLAN_PRO:
            case FirebaseBillingManager.PLAN_ENTERPRISE: return "month";
            default: return "period";
        }
    }
    
    private String getPlanFeatures(String planId) {
        switch (planId) {
            case FirebaseBillingManager.PLAN_FREE:
                return "• 50 SMS per day\n• 500 SMS per month\n• Basic templates";
            case FirebaseBillingManager.PLAN_BASIC:
                return "• 500 SMS per day\n• 10,000 SMS per month\n• Advanced templates\n• Analytics";
            case FirebaseBillingManager.PLAN_PRO:
                return "• 2,000 SMS per day\n• 50,000 SMS per month\n• All features\n• Priority support";
            case FirebaseBillingManager.PLAN_ENTERPRISE:
                return "• Unlimited SMS\n• All features\n• Custom sender ID\n• Dedicated support";
            default:
                return "• Limited features";
        }
    }
    
    private int calculateProgress(int current, int max) {
        return max > 0 ? (int) ((float) current / max * 100) : 0;
    }
    
    private void showError(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }
    
    private String capitalize(String str) {
        if (str == null || str.isEmpty()) return str;
        return str.substring(0, 1).toUpperCase(Locale.ROOT) + str.substring(1).toLowerCase(Locale.ROOT);
    }
    
    // Mock M-Pesa parsing - implement real parsing logic
    private MpesaTransaction parseMpesaMessage(String message) {
        // This is a simplified parser - implement real logic based on M-Pesa message format
        if (message.contains("M-PESA") && message.contains("received")) {
            // Extract amount and transaction ID
            // This is just a placeholder implementation
            return new MpesaTransaction(500.0, "ABC123XYZ");
        }
        return null;
    }
    
    // Mock MpesaTransaction class
    private static class MpesaTransaction {
        private final double amount;
        private final String transactionId;
        
        public MpesaTransaction(double amount, String transactionId) {
            this.amount = amount;
            this.transactionId = transactionId;
        }
        
        public double getAmount() { return amount; }
        public String getTransactionId() { return transactionId; }
    }
}
