package com.bulksms.smsmanager.billing;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import java.util.concurrent.CompletableFuture;

import javax.inject.Inject;

/**
 * Simplified Billing ViewModel
 */
public class BillingViewModel extends ViewModel {
    
    private final MutableLiveData<SubscriptionInfo> subscription = new MutableLiveData<>();
    private final MutableLiveData<UsageStats> usageStats = new MutableLiveData<>();
    private final MutableLiveData<Boolean> loading = new MutableLiveData<>();
    private final MutableLiveData<String> error = new MutableLiveData<>();
    
    private final FirebaseBillingManager billingManager;
    
    @Inject
    public BillingViewModel(FirebaseBillingManager billingManager) {
        this.billingManager = billingManager;
        refreshData();
    }
    
    public LiveData<SubscriptionInfo> getSubscription() {
        return subscription;
    }
    
    public LiveData<UsageStats> getUsageStats() {
        return usageStats;
    }
    
    public LiveData<Boolean> getLoading() {
        return loading;
    }
    
    public LiveData<String> getError() {
        return error;
    }
    
    public void refreshData() {
        loading.setValue(true);
        error.setValue(null);
        
        billingManager.getCurrentSubscription()
            .thenAccept(sub -> subscription.postValue(sub))
            .exceptionally(throwable -> {
                error.postValue(throwable.getMessage());
                return null;
            })
            .thenCompose(aVoid -> billingManager.getUsageStats())
            .thenAccept(usage -> usageStats.postValue(usage))
            .exceptionally(throwable -> {
                error.postValue(throwable.getMessage());
                return null;
            })
            .whenComplete((result, throwable) -> loading.postValue(false));
    }
    
    public CompletableFuture<BillingResult> activateSubscription(
        double amount, 
        String transactionId
    ) {
        MutableLiveData<BillingResult> resultLiveData = new MutableLiveData<>();
        
        // Validate transaction
        return billingManager.validateMpesaTransaction(amount, transactionId)
            .thenCompose(plan -> {
                // Create subscription
                return billingManager.createSubscription(
                    plan.getId(),
                    amount,
                    "mpesa",
                    transactionId,
                    false  // autoRenew for manual M-Pesa payments
                ).thenApply(subscriptionId -> {
                    refreshData(); // Refresh UI
                    return (BillingResult) new BillingResult.Success("Subscription activated successfully");
                });
            })
            .exceptionally(throwable -> {
                String message = throwable.getMessage() != null ? throwable.getMessage() : "Activation failed";
                return (BillingResult) new BillingResult.Error(message);
            });
    }
    
    public CompletableFuture<BillingResult> cancelSubscription(String subscriptionId, String reason) {
        return billingManager.cancelSubscription(subscriptionId, reason)
            .thenApply(aVoid -> (BillingResult) new BillingResult.Success("Subscription cancelled"))
            .exceptionally(throwable -> (BillingResult) new BillingResult.Error(throwable.getMessage()));
    }
    
    public CompletableFuture<Boolean> trackSmsUsage(int count) {
        return billingManager.trackSmsUsage(count)
            .thenApply(aVoid -> {
                // Refresh usage stats
                billingManager.getUsageStats()
                    .thenAccept(usage -> usageStats.postValue(usage));
                return true;
            })
            .exceptionally(throwable -> {
                error.postValue(throwable.getMessage());
                return false;
            });
    }
    
    // Factory for ViewModel creation
    public static class Factory extends ViewModelProvider.NewInstanceFactory {
        private final FirebaseBillingManager billingManager;
        
        @Inject
        public Factory(FirebaseBillingManager billingManager) {
            this.billingManager = billingManager;
        }
        
        @Override
        public <T extends ViewModel> T create(Class<T> modelClass) {
            if (modelClass.isAssignableFrom(BillingViewModel.class)) {
                return (T) new BillingViewModel(billingManager);
            }
            throw new IllegalArgumentException("Unknown ViewModel class");
        }
    }
    
    // Result classes
    public static abstract class BillingResult {
        public static class Success extends BillingResult {
            private final String message;
            
            public Success(String message) {
                this.message = message;
            }
            
            public String getMessage() {
                return message;
            }
        }
        
        public static class Error extends BillingResult {
            private final String message;
            
            public Error(String message) {
                this.message = message;
            }
            
            public String getMessage() {
                return message;
            }
        }
        
        public static class Loading extends BillingResult {
            public Loading() {}
        }
    }
}
