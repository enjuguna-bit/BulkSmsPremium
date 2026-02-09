package com.bulksms.smsmanager.billing;

import java.util.Map;

/**
 * Data class for subscription information
 */
public class SubscriptionInfo {
    private final String id;
    private final String planId;
    private final String status;
    private final long startDate;
    private final long endDate;
    private final boolean autoRenew;
    private final double amount;
    private final String currency;
    private final Map<String, Boolean> features;
    private final long createdAt;

    public SubscriptionInfo(String id, String planId, String status, long startDate,
            long endDate, boolean autoRenew, double amount, String currency,
            Map<String, Boolean> features, long createdAt) {
        this.id = id;
        this.planId = planId;
        this.status = status;
        this.startDate = startDate;
        this.endDate = endDate;
        this.autoRenew = autoRenew;
        this.amount = amount;
        this.currency = currency;
        this.features = features;
        this.createdAt = createdAt;
    }

    public String getId() {
        return id;
    }

    public String getPlanId() {
        return planId;
    }

    public String getStatus() {
        return status;
    }

    public long getStartDate() {
        return startDate;
    }

    public long getEndDate() {
        return endDate;
    }

    public boolean isAutoRenew() {
        return autoRenew;
    }

    public double getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public Map<String, Boolean> getFeatures() {
        return features;
    }

    public long getCreatedAt() {
        return createdAt;
    }
}
