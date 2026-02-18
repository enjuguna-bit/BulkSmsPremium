package com.afriserve.smsmanager.ui.dashboard;

/**
 * Data model for SMS statistics
 */
public class SmsStats {
    public enum DeliveryStatus {
        EXCELLENT,
        GOOD,
        FAIR,
        POOR
    }

    public enum TrendDirection {
        UP,
        DOWN,
        STABLE
    }

    private final int totalSent;
    private final int totalDelivered;
    private final int totalFailed;
    private final int totalQueued;
    private final float deliveryRate;
    private final DeliveryStatus deliveryStatus;
    private final float trendPercentage;
    private final TrendDirection trendDirection;

    public SmsStats(
            int totalSent,
            int totalDelivered,
            int totalFailed,
            int totalQueued,
            float trendPercentage
    ) {
        this.totalSent = totalSent;
        this.totalDelivered = totalDelivered;
        this.totalFailed = totalFailed;
        this.totalQueued = totalQueued;
        this.trendPercentage = trendPercentage;

        // Calculate delivery rate
        if (totalSent > 0) {
            this.deliveryRate = (float) totalDelivered / totalSent * 100;
        } else {
            this.deliveryRate = 0;
        }

        // Determine delivery status
        this.deliveryStatus = calculateDeliveryStatus(this.deliveryRate);

        if (Math.abs(trendPercentage) < 0.1f) {
            this.trendDirection = TrendDirection.STABLE;
        } else if (trendPercentage > 0) {
            this.trendDirection = TrendDirection.UP;
        } else {
            this.trendDirection = TrendDirection.DOWN;
        }
    }

    private DeliveryStatus calculateDeliveryStatus(float rate) {
        if (rate >= 95) return DeliveryStatus.EXCELLENT;
        else if (rate >= 90) return DeliveryStatus.GOOD;
        else if (rate >= 80) return DeliveryStatus.FAIR;
        else return DeliveryStatus.POOR;
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

    public DeliveryStatus getDeliveryStatus() {
        return deliveryStatus;
    }

    public float getTrendPercentage() {
        return trendPercentage;
    }

    public TrendDirection getTrendDirection() {
        return trendDirection;
    }
}
