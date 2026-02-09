package com.bulksms.smsmanager.dashboard;

import com.bulksms.smsmanager.models.SmsModel;

import java.util.List;

/**
 * Data model for dashboard statistics (billing removed)
 */
public class DashboardStats {
    private final SmsStats smsStats;

    public DashboardStats(SmsStats smsStats) {
        this.smsStats = smsStats;
    }

    public SmsStats getSmsStats() {
        return smsStats;
    }

    public List<SmsModel> getRecentActivity() {
        return smsStats != null ? smsStats.recentActivity : null;
    }
}