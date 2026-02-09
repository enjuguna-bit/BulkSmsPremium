package com.afriserve.smsmanager.ui.dashboard;

import com.afriserve.smsmanager.models.SmsModel;

import java.util.List;

/**
 * Data model for dashboard statistics (billing removed)
 */
public class DashboardStats {
    private final SmsStats smsStats;
    private List<SmsModel> recentActivity;

    public DashboardStats(SmsStats smsStats) {
        this.smsStats = smsStats;
    }

    public SmsStats getSmsStats() {
        return smsStats;
    }

    public List<SmsModel> getRecentActivity() {
        return recentActivity;
    }

    public void setRecentActivity(List<SmsModel> recentActivity) {
        this.recentActivity = recentActivity;
    }
}