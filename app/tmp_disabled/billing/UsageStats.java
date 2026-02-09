package com.bulksms.smsmanager.billing;

/**
 * Data class for usage statistics
 */
public class UsageStats {
    private final int dailySms;
    private final int monthlySms;

    public UsageStats() {
        this.dailySms = 0;
        this.monthlySms = 0;
    }

    public UsageStats(int dailySms, int monthlySms) {
        this.dailySms = dailySms;
        this.monthlySms = monthlySms;
    }

    public int getDailySms() {
        return dailySms;
    }

    public int getMonthlySms() {
        return monthlySms;
    }
}
