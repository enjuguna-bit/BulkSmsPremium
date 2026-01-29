package com.bulksms.smsmanager.billing;

/**
 * Represents the current subscription state
 */
public class SubscriptionState {
    private final SubscriptionStatus status;
    private final Subscription subscription;
    private final int daysRemaining;
    private final int graceDaysRemaining;
    
    public SubscriptionState(SubscriptionStatus status, Subscription subscription, 
                           int daysRemaining, int graceDaysRemaining) {
        this.status = status;
        this.subscription = subscription;
        this.daysRemaining = daysRemaining;
        this.graceDaysRemaining = graceDaysRemaining;
    }
    
    public SubscriptionStatus getStatus() {
        return status;
    }
    
    public Subscription getSubscription() {
        return subscription;
    }
    
    public int getDaysRemaining() {
        return daysRemaining;
    }
    
    public int getGraceDaysRemaining() {
        return graceDaysRemaining;
    }
}
