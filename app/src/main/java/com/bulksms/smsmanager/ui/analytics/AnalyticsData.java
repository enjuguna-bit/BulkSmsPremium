package com.bulksms.smsmanager.ui.analytics;

import java.util.Locale;

import com.bulksms.smsmanager.billing.SubscriptionInfo;
import com.bulksms.smsmanager.dashboard.SmsStats;

/**
 * Data model for analytics dashboard
 */
public class AnalyticsData {
    private final SmsStats smsStats;
    private final SubscriptionInfo subscriptionInfo;

    public AnalyticsData(SmsStats smsStats, SubscriptionInfo subscriptionInfo) {
        this.smsStats = smsStats;
        this.subscriptionInfo = subscriptionInfo;
    }

    public SmsStats getSmsStats() {
        return smsStats;
    }

    public SubscriptionInfo getSubscriptionInfo() {
        return subscriptionInfo;
    }

    public int getDailyUsed() {
        return smsStats.getTotalSent(); // Simplified - in real app would track daily specifically
    }

    public int getDailyLimit() {
        if (subscriptionInfo != null) {
            return com.bulksms.smsmanager.billing.SubscriptionPlans.getLimitsForPlan(subscriptionInfo.getPlanId()).getDailySms();
        }
        return 100; // Free plan default
    }

    public int getMonthlyUsed() {
        return smsStats.getTotalSent(); // Simplified
    }

    public int getMonthlyLimit() {
        if (subscriptionInfo != null) {
            return com.bulksms.smsmanager.billing.SubscriptionPlans.getLimitsForPlan(subscriptionInfo.getPlanId()).getMonthlySms();
        }
        return 1000; // Free plan default
    }

    public String getPlanName() {
        if (subscriptionInfo != null) {
            return subscriptionInfo.getPlanId().toUpperCase(Locale.ROOT);
        }
        return "FREE";
    }

    public String getPlanTier() {
        if (subscriptionInfo != null) {
            return "Tier: " + subscriptionInfo.getPlanId().substring(0, 1).toUpperCase(Locale.ROOT) +
                   subscriptionInfo.getPlanId().substring(1);
        }
        return "Tier: Free";
    }
}