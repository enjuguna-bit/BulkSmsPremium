package com.bulksms.smsmanager.billing;

/**
 * Enum representing subscription status
 */
public enum SubscriptionStatus {
    NONE, // No subscription
    TRIAL, // Trial period
    ACTIVE, // Active subscription
    EXPIRING, // About to expire
    GRACE, // Grace period
    EXPIRED // Expired
}
