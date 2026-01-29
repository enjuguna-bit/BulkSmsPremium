package com.bulksms.smsmanager.billing;

import java.util.HashMap;
import java.util.Map;

/**
 * Data class for subscription plan details
 */
public class SubscriptionPlan {
    private final String id;
    private final String name;
    private final double price;
    private final String currency;
    private final long durationMs;
    private final Map<String, Boolean> features;

    public SubscriptionPlan(String id, String name, double price, String currency, 
                          long durationMs, Map<String, Boolean> features) {
        this.id = id;
        this.name = name;
        this.price = price;
        this.currency = currency;
        this.durationMs = durationMs;
        this.features = features;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public double getPrice() { return price; }
    public String getCurrency() { return currency; }
    public long getDurationMs() { return durationMs; }
    public Map<String, Boolean> getFeatures() { return features; }
}
