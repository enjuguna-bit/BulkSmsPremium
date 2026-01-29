package com.bulksms.smsmanager.billing;

/**
 * Represents a subscription
 */
public class Subscription {
    private final String planId;
    private final long expiryTime;
    private final long gracePeriodEnd;
    
    public Subscription(String planId, long expiryTime, long gracePeriodEnd) {
        this.planId = planId;
        this.expiryTime = expiryTime;
        this.gracePeriodEnd = gracePeriodEnd;
    }
    
    public String getPlanId() {
        return planId;
    }
    
    public long getExpiryTime() {
        return expiryTime;
    }
    
    public long getGracePeriodEnd() {
        return gracePeriodEnd;
    }
}
