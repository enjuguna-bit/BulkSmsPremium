package com.bulksms.smsmanager.dashboard;

import com.bulksms.smsmanager.models.SmsModel;

import java.util.List;
import java.util.Locale;

/**
 * SMS statistics data model
 */
public class SmsStats {
    public int sentCount = 0;
    public int failedCount = 0;
    public float deliveryRate = 0.0f;
    public List<SmsModel> recentActivity;

    public int getTotalSent() {
        return sentCount;
    }

    public int getTotalFailed() {
        return failedCount;
    }

    public int getTotalQueued() {
        return 0; // Not implemented yet
    }

    public float getDeliveryRate() {
        return deliveryRate;
    }

    public String getDeliveryStatus() {
        if (deliveryRate >= 98) return "Excellent";
        if (deliveryRate >= 95) return "Good";
        if (deliveryRate >= 90) return "Fair";
        return "Poor";
    }

    public String getTrend() {
        // In a real implementation, this would compare with previous period
        return sentCount > 0 ? "+5% from yesterday" : "No activity";
    }

    public String getDeliveryRateText() {
        return String.format(Locale.getDefault(), "%.1f%%", deliveryRate);
    }

    public String getTrendText() {
        return sentCount > 0 ? "+5% from yesterday" : "No activity";
    }
}