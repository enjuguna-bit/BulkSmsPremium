package com.bulksms.smsmanager.ui.dashboard;

import com.bulksms.smsmanager.billing.SubscriptionInfo;
import com.bulksms.smsmanager.models.SmsModel;

import java.util.List;

/**
 * Data model for dashboard statistics
 */
public class DashboardStats {
    private final SmsStats smsStats;
    private final SubscriptionInfo subscriptionInfo;
    private List<SmsModel> recentActivity;

    public DashboardStats(SmsStats smsStats, SubscriptionInfo subscriptionInfo) {
        this.smsStats = smsStats;
        this.subscriptionInfo = subscriptionInfo;
    }

    public SmsStats getSmsStats() {
        return smsStats;
    }

    public SubscriptionInfo getSubscriptionInfo() {
        return subscriptionInfo;
    }

    public List<SmsModel> getRecentActivity() {
        return recentActivity;
    }

    public void setRecentActivity(List<SmsModel> recentActivity) {
        this.recentActivity = recentActivity;
    }
}