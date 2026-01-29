package com.bulksms.smsmanager.billing;

import android.content.Context;
import android.content.SharedPreferences;

import com.bulksms.smsmanager.utils.ToastUtils;

/**
 * Mock Subscription Manager for demonstration
 */
public class SubscriptionManager {
    private static SubscriptionManager instance;
    private final Context context;
    private final SharedPreferences prefs;
    
    private SubscriptionManager(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = context.getSharedPreferences("billing", Context.MODE_PRIVATE);
    }
    
    public static SubscriptionManager getInstance(Context context) {
        if (instance == null) {
            instance = new SubscriptionManager(context);
        }
        return instance;
    }
    
    public SubscriptionState getSubscriptionState() {
        // Mock implementation - in real app, this would connect to billing service
        String status = prefs.getString("subscription_status", "none");
        long expiryTime = prefs.getLong("subscription_expiry", 0);
        long graceTime = prefs.getLong("subscription_grace", 0);
        long trialTime = prefs.getLong("trial_end", 0);
        
        SubscriptionStatus subscriptionStatus = SubscriptionStatus.NONE;
        if ("active".equals(status)) {
            subscriptionStatus = SubscriptionStatus.ACTIVE;
        } else if ("expiring".equals(status)) {
            subscriptionStatus = SubscriptionStatus.EXPIRING;
        } else if ("grace".equals(status)) {
            subscriptionStatus = SubscriptionStatus.GRACE;
        } else if ("expired".equals(status)) {
            subscriptionStatus = SubscriptionStatus.EXPIRED;
        }
        
        Subscription subscription = new Subscription("premium", expiryTime, graceTime);
        return new SubscriptionState(subscriptionStatus, subscription, 
                                   calculateDaysRemaining(expiryTime), 
                                   calculateDaysRemaining(graceTime));
    }
    
    public String getPlanName(String planId) {
        switch (planId) {
            case "premium":
                return "Premium";
            case "pro":
                return "Professional";
            case "basic":
                return "Basic";
            default:
                return "Unknown";
        }
    }
    
    public boolean isInTrial() {
        long trialEnd = prefs.getLong("trial_end", 0);
        return trialEnd > System.currentTimeMillis();
    }
    
    public int getTrialDaysRemaining() {
        long trialEnd = prefs.getLong("trial_end", 0);
        return calculateDaysRemaining(trialEnd);
    }
    
    private int calculateDaysRemaining(long endTime) {
        if (endTime <= 0) return 0;
        long remaining = endTime - System.currentTimeMillis();
        return (int) Math.max(0, remaining / (24 * 60 * 60 * 1000));
    }
    
    // Mock methods for testing
    public void setActiveSubscription() {
        prefs.edit()
             .putString("subscription_status", "active")
             .putLong("subscription_expiry", System.currentTimeMillis() + (30L * 24 * 60 * 60 * 1000))
             .apply();
    }
    
    public void setTrial() {
        prefs.edit()
             .putString("subscription_status", "none")
             .putLong("trial_end", System.currentTimeMillis() + (7L * 24 * 60 * 60 * 1000))
             .apply();
    }
}
