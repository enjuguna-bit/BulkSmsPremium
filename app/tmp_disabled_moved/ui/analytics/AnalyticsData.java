package com.bulksms.smsmanager.ui.analytics;

import java.util.Locale;


import com.bulksms.smsmanager.dashboard.SmsStats;

/**
 * Data model for analytics dashboard
 */
public class AnalyticsData {
    private final SmsStats smsStats;


    public AnalyticsData(SmsStats smsStats) {
        this.smsStats = smsStats;
    }

    public SmsStats getSmsStats() {
        return smsStats;
    }


    public int getDailyUsed() {
        return smsStats.getTotalSent(); // Simplified - in real app would track daily specifically
    }

    public int getDailyLimit() {
        return 50; // Default free plan daily limit
    }

    public int getMonthlyUsed() {
        return smsStats.getTotalSent(); // Simplified
    }

    public int getMonthlyLimit() {
        return 500; // Default free plan monthly limit
    }

    public String getPlanName() {
        return "FREE";
    }

    public String getPlanTier() {
        return "Tier: Free";
    }
}