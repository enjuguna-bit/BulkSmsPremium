package com.afriserve.smsmanager.ui.dashboard;

/**
 * Data model for SMS statistics
 */
public class SmsStats {
    private final int totalSent;
    private final int totalDelivered;
    private final int totalFailed;
    private final int totalQueued;
    private final float deliveryRate;
    private final String deliveryStatus;
    private final String trend;

    public SmsStats(int totalSent, int totalDelivered, int totalFailed, int totalQueued) {
        this.totalSent = totalSent;
        this.totalDelivered = totalDelivered;
        this.totalFailed = totalFailed;
        this.totalQueued = totalQueued;

        // Calculate delivery rate
        if (totalSent > 0) {
            this.deliveryRate = (float) totalDelivered / totalSent * 100;
        } else {
            this.deliveryRate = 0;
        }

        // Determine delivery status
        this.deliveryStatus = calculateDeliveryStatus(deliveryRate);

        // Calculate trend (simplified - in real app would compare with previous period)
        this.trend = "+5%"; // Mock trend
    }

    private String calculateDeliveryStatus(float rate) {
        if (rate >= 95) return "Excellent";
        else if (rate >= 90) return "Good";
        else if (rate >= 80) return "Fair";
        else return "Poor";
    }

    public int getTotalSent() {
        return totalSent;
    }

    public int getTotalDelivered() {
        return totalDelivered;
    }

    public int getTotalFailed() {
        return totalFailed;
    }

    public int getTotalQueued() {
        return totalQueued;
    }

    public float getDeliveryRate() {
        return deliveryRate;
    }

    public String getDeliveryStatus() {
        return deliveryStatus;
    }

    public String getTrend() {
        return trend;
    }
}