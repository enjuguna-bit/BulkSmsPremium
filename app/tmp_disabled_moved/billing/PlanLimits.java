package com.bulksms.smsmanager.billing;

/**
 * Data class for plan limits
 */
public class PlanLimits {
    private final int dailySms;
    private final int monthlySms;

    public PlanLimits(int dailySms, int monthlySms) {
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
