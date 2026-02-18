package com.afriserve.smsmanager.ui.dashboard;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class SmsStatsTest {

    @Test
    public void deliveryStatusAndTrend_areComputedFromRealValues() {
        SmsStats stats = new SmsStats(100, 96, 3, 1, 12.5f);

        assertEquals(96.0f, stats.getDeliveryRate(), 0.001f);
        assertEquals(SmsStats.DeliveryStatus.EXCELLENT, stats.getDeliveryStatus());
        assertEquals(SmsStats.TrendDirection.UP, stats.getTrendDirection());
        assertEquals(12.5f, stats.getTrendPercentage(), 0.001f);
    }

    @Test
    public void trendDirection_downAndStable_areDetected() {
        SmsStats down = new SmsStats(50, 35, 10, 5, -7.0f);
        SmsStats stable = new SmsStats(0, 0, 0, 0, 0.01f);

        assertEquals(SmsStats.TrendDirection.DOWN, down.getTrendDirection());
        assertEquals(SmsStats.TrendDirection.STABLE, stable.getTrendDirection());
    }
}

