package com.afriserve.smsmanager.data.analytics;

import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.afriserve.smsmanager.data.dao.DashboardDao;
import com.afriserve.smsmanager.data.entity.DashboardMetricsEntity;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import javax.inject.Inject;
import javax.inject.Singleton;

import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * Trend analysis engine for dashboard analytics
 * Provides comprehensive trend analysis and forecasting
 */
@Singleton
public class TrendAnalyzer {
    
    private static final String TAG = "TrendAnalyzer";
    
    private final DashboardDao dashboardDao;
    
    // Trend data
    private final MutableLiveData<TrendAnalysis> _trendAnalysis = new MutableLiveData<>();
    public final LiveData<TrendAnalysis> trendAnalysis = _trendAnalysis;
    
    @Inject
    public TrendAnalyzer(DashboardDao dashboardDao) {
        this.dashboardDao = dashboardDao;
        
        Log.d(TAG, "Trend analyzer initialized");
    }
    
    /**
     * Analyze trends for the specified period
     */
    public Single<TrendAnalysis> analyzeTrends(TrendPeriod period, int dataPoints) {
        return Single.fromCallable(() -> {
            try {
                long endDate = System.currentTimeMillis();
                long startDate = calculateStartDate(period, endDate, dataPoints);
                
                // Get metrics data
                List<DashboardMetricsEntity> metrics = dashboardDao
                    .getMetricsInDateRange(startDate, endDate).blockingGet();
                
                if (metrics.isEmpty()) {
                    return createEmptyTrendAnalysis(period);
                }
                
                // Sort by date
                Collections.sort(metrics, Comparator.comparing(m -> m.metricDate));
                
                // Perform trend analysis
                TrendAnalysis analysis = new TrendAnalysis(
                    period,
                    metrics,
                    calculateGrowthRate(metrics),
                    calculateSeasonalPattern(metrics),
                    detectAnomalies(metrics),
                    generateForecast(metrics),
                    calculateMovingAverages(metrics),
                    calculateVolatility(metrics)
                );
                
                _trendAnalysis.postValue(analysis);
                
                return analysis;
                
            } catch (Exception e) {
                Log.e(TAG, "Failed to analyze trends", e);
                throw new RuntimeException(e);
            }
        }).subscribeOn(Schedulers.io());
    }
    
    /**
     * Analyze delivery rate trends
     */
    public Single<DeliveryRateTrend> analyzeDeliveryRateTrends(TrendPeriod period) {
        return Single.fromCallable(() -> {
            try {
                long endDate = System.currentTimeMillis();
                long startDate = calculateStartDate(period, endDate, 30);
                
                List<DashboardMetricsEntity> metrics = dashboardDao
                    .getMetricsInDateRange(startDate, endDate).blockingGet();
                
                if (metrics.isEmpty()) {
                    return new DeliveryRateTrend(period, new ArrayList<>(), 0.0f, "STABLE");
                }
                
                Collections.sort(metrics, Comparator.comparing(m -> m.metricDate));
                
                List<DailyDeliveryRate> dailyRates = new ArrayList<>();
                for (DashboardMetricsEntity metric : metrics) {
                    dailyRates.add(new DailyDeliveryRate(
                        metric.metricDate,
                        metric.getDeliveryRate()
                    ));
                }
                
                float growthRate = calculateGrowthRate(metrics);
                String trendDirection = determineTrendDirection(growthRate);
                
                return new DeliveryRateTrend(period, dailyRates, growthRate, trendDirection);
                
            } catch (Exception e) {
                Log.e(TAG, "Failed to analyze delivery rate trends", e);
                throw new RuntimeException(e);
            }
        }).subscribeOn(Schedulers.io());
    }
    
    /**
     * Analyze campaign performance trends
     */
    public Single<CampaignTrend> analyzeCampaignTrends(TrendPeriod period) {
        return Single.fromCallable(() -> {
            try {
                long endDate = System.currentTimeMillis();
                long startDate = calculateStartDate(period, endDate, 30);
                
                List<DashboardMetricsEntity> metrics = dashboardDao
                    .getMetricsInDateRange(startDate, endDate).blockingGet();
                
                if (metrics.isEmpty()) {
                    return new CampaignTrend(period, new ArrayList<>(), 0.0f, "STABLE");
                }
                
                Collections.sort(metrics, Comparator.comparing(m -> m.metricDate));
                
                List<DailyCampaignMetrics> dailyCampaigns = new ArrayList<>();
                for (DashboardMetricsEntity metric : metrics) {
                    dailyCampaigns.add(new DailyCampaignMetrics(
                        metric.metricDate,
                        metric.campaignCount,
                        metric.activeCampaigns,
                        metric.getCampaignSuccessRate()
                    ));
                }
                
                float growthRate = calculateGrowthRate(metrics);
                String trendDirection = determineTrendDirection(growthRate);
                
                return new CampaignTrend(period, dailyCampaigns, growthRate, trendDirection);
                
            } catch (Exception e) {
                Log.e(TAG, "Failed to analyze campaign trends", e);
                throw new RuntimeException(e);
            }
        }).subscribeOn(Schedulers.io());
    }
    
    /**
     * Detect anomalies in the data
     */
    public Single<List<Anomaly>> detectAnomalies(TrendPeriod period) {
        return Single.fromCallable(() -> {
            try {
                long endDate = System.currentTimeMillis();
                long startDate = calculateStartDate(period, endDate, 30);
                
                List<DashboardMetricsEntity> metrics = dashboardDao
                    .getMetricsInDateRange(startDate, endDate).blockingGet();
                
                return detectAnomalies(metrics);
                
            } catch (Exception e) {
                Log.e(TAG, "Failed to detect anomalies", e);
                throw new RuntimeException(e);
            }
        }).subscribeOn(Schedulers.io());
    }
    
    /**
     * Generate forecast for next period
     */
    public Single<Forecast> generateForecast(TrendPeriod period) {
        return Single.fromCallable(() -> {
            try {
                long endDate = System.currentTimeMillis();
                long startDate = calculateStartDate(period, endDate, 30);
                
                List<DashboardMetricsEntity> metrics = dashboardDao
                    .getMetricsInDateRange(startDate, endDate).blockingGet();
                
                if (metrics.isEmpty()) {
                    return new Forecast(period, 0, 0, 0, 0.0f, "LOW");
                }
                
                return generateForecast(metrics);
                
            } catch (Exception e) {
                Log.e(TAG, "Failed to generate forecast", e);
                throw new RuntimeException(e);
            }
        }).subscribeOn(Schedulers.io());
    }
    
    // Private analysis methods
    
    private float calculateGrowthRate(List<DashboardMetricsEntity> metrics) {
        if (metrics.size() < 2) return 0.0f;
        
        DashboardMetricsEntity first = metrics.get(0);
        DashboardMetricsEntity last = metrics.get(metrics.size() - 1);
        
        if (first.sentCount == 0) return 0.0f;
        
        return ((float) (last.sentCount - first.sentCount) / first.sentCount) * 100.0f;
    }
    
    private SeasonalPattern calculateSeasonalPattern(List<DashboardMetricsEntity> metrics) {
        if (metrics.isEmpty()) {
            return new SeasonalPattern("NO_DATA", new ArrayList<>());
        }
        
        // Calculate average by day of week
        float[] dayOfWeekAverages = new float[7];
        int[] dayOfWeekCounts = new int[7];
        
        for (DashboardMetricsEntity metric : metrics) {
            Calendar cal = Calendar.getInstance();
            cal.setTimeInMillis(metric.metricDate);
            int dayOfWeek = cal.get(Calendar.DAY_OF_WEEK);
            
            dayOfWeekAverages[dayOfWeek - 1] += metric.sentCount;
            dayOfWeekCounts[dayOfWeek - 1]++;
        }
        
        List<DailyPattern> patterns = new ArrayList<>();
        String[] dayNames = {"Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"};
        
        for (int i = 0; i < 7; i++) {
            if (dayOfWeekCounts[i] > 0) {
                float average = dayOfWeekAverages[i] / dayOfWeekCounts[i];
                patterns.add(new DailyPattern(dayNames[i], average));
            }
        }
        
        return new SeasonalPattern("WEEKLY", patterns);
    }
    
    private List<Anomaly> detectAnomalies(List<DashboardMetricsEntity> metrics) {
        List<Anomaly> anomalies = new ArrayList<>();
        
        if (metrics.size() < 7) return anomalies; // Need at least a week of data
        
        // Calculate moving average and standard deviation
        List<Double> values = new ArrayList<>();
        for (DashboardMetricsEntity metric : metrics) {
            values.add((double) metric.sentCount);
        }
        
        double mean = calculateMean(values);
        double stdDev = calculateStandardDeviation(values, mean);
        
        // Detect anomalies (values beyond 2 standard deviations)
        for (int i = 0; i < metrics.size(); i++) {
            DashboardMetricsEntity metric = metrics.get(i);
            double value = metric.sentCount;
            
            if (Math.abs(value - mean) > 2 * stdDev) {
                String severity = Math.abs(value - mean) > 3 * stdDev ? "HIGH" : "MEDIUM";
                anomalies.add(new Anomaly(
                    metric.metricDate,
                    "SENT_COUNT",
                    value,
                    mean,
                    severity,
                    "Unusual activity detected"
                ));
            }
        }
        
        return anomalies;
    }
    
    private Forecast generateForecast(List<DashboardMetricsEntity> metrics) {
        if (metrics.size() < 3) {
            return new Forecast(TrendPeriod.WEEKLY, 0, 0, 0, 0.0f, "LOW");
        }
        
        // Simple linear regression for forecasting
        int n = metrics.size();
        double sumX = 0, sumY = 0, sumXY = 0, sumX2 = 0;
        
        for (int i = 0; i < n; i++) {
            double x = i;
            double y = metrics.get(i).sentCount;
            sumX += x;
            sumY += y;
            sumXY += x * y;
            sumX2 += x * x;
        }
        
        double slope = (n * sumXY - sumX * sumY) / (n * sumX2 - sumX * sumX);
        double intercept = (sumY - slope * sumX) / n;
        
        // Forecast next period
        int nextPeriod = n;
        double forecastValue = slope * nextPeriod + intercept;
        
        // Calculate confidence based on historical variance
        List<Double> values = new ArrayList<>();
        for (DashboardMetricsEntity metric : metrics) {
            values.add((double) metric.sentCount);
        }
        double variance = calculateVariance(values);
        String confidence = variance < 100 ? "HIGH" : variance < 500 ? "MEDIUM" : "LOW";
        
        return new Forecast(
            TrendPeriod.WEEKLY,
            (int) Math.max(0, forecastValue),
            (int) Math.max(0, forecastValue * 0.9), // Lower bound
            (int) Math.max(0, forecastValue * 1.1), // Upper bound
            (float) variance,
            confidence
        );
    }
    
    private List<MovingAverage> calculateMovingAverages(List<DashboardMetricsEntity> metrics) {
        List<MovingAverage> movingAverages = new ArrayList<>();
        
        // 7-day moving average
        for (int i = 6; i < metrics.size(); i++) {
            double sum = 0;
            for (int j = i - 6; j <= i; j++) {
                sum += metrics.get(j).sentCount;
            }
            double avg = sum / 7;
            
            movingAverages.add(new MovingAverage(
                metrics.get(i).metricDate,
                7,
                avg
            ));
        }
        
        return movingAverages;
    }
    
    private double calculateVolatility(List<DashboardMetricsEntity> metrics) {
        if (metrics.size() < 2) return 0.0;
        
        List<Double> values = new ArrayList<>();
        for (DashboardMetricsEntity metric : metrics) {
            values.add((double) metric.sentCount);
        }
        
        return calculateStandardDeviation(values, calculateMean(values));
    }
    
    // Helper methods
    private long calculateStartDate(TrendPeriod period, long endDate, int dataPoints) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(endDate);
        
        switch (period) {
            case DAILY:
                cal.add(Calendar.DAY_OF_MONTH, -dataPoints);
                break;
            case WEEKLY:
                cal.add(Calendar.WEEK_OF_YEAR, -dataPoints);
                break;
            case MONTHLY:
                cal.add(Calendar.MONTH, -dataPoints);
                break;
            case QUARTERLY:
                cal.add(Calendar.MONTH, -dataPoints * 3);
                break;
            case YEARLY:
                cal.add(Calendar.YEAR, -dataPoints);
                break;
        }
        
        return cal.getTimeInMillis();
    }
    
    private TrendAnalysis createEmptyTrendAnalysis(TrendPeriod period) {
        return new TrendAnalysis(
            period,
            new ArrayList<>(),
            0.0f,
            new SeasonalPattern("NO_DATA", new ArrayList<>()),
            new ArrayList<>(),
            new Forecast(period, 0, 0, 0, 0.0f, "LOW"),
            new ArrayList<>(),
            0.0
        );
    }
    
    private String determineTrendDirection(float growthRate) {
        if (growthRate > 10.0f) return "STRONG_UP";
        if (growthRate > 5.0f) return "UP";
        if (growthRate > -5.0f) return "STABLE";
        if (growthRate > -10.0f) return "DOWN";
        return "STRONG_DOWN";
    }
    
    private double calculateMean(List<Double> values) {
        return values.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
    }
    
    private double calculateStandardDeviation(List<Double> values, double mean) {
        double variance = calculateVariance(values, mean);
        return Math.sqrt(variance);
    }
    
    private double calculateVariance(List<Double> values) {
        if (values.isEmpty()) return 0.0;
        double mean = calculateMean(values);
        return calculateVariance(values, mean);
    }
    
    private double calculateVariance(List<Double> values, double mean) {
        return values.stream()
            .mapToDouble(v -> Math.pow(v - mean, 2))
            .average()
            .orElse(0.0);
    }
    
    // Data classes
    
    public enum TrendPeriod {
        DAILY, WEEKLY, MONTHLY, QUARTERLY, YEARLY
    }
    
    public static class TrendAnalysis {
        public final TrendPeriod period;
        public final List<DashboardMetricsEntity> data;
        public final float growthRate;
        public final SeasonalPattern seasonalPattern;
        public final List<Anomaly> anomalies;
        public final Forecast forecast;
        public final List<MovingAverage> movingAverages;
        public final double volatility;
        
        public TrendAnalysis(TrendPeriod period, List<DashboardMetricsEntity> data, 
                           float growthRate, SeasonalPattern seasonalPattern,
                           List<Anomaly> anomalies, Forecast forecast,
                           List<MovingAverage> movingAverages, double volatility) {
            this.period = period;
            this.data = data;
            this.growthRate = growthRate;
            this.seasonalPattern = seasonalPattern;
            this.anomalies = anomalies;
            this.forecast = forecast;
            this.movingAverages = movingAverages;
            this.volatility = volatility;
        }
    }
    
    public static class SeasonalPattern {
        public final String patternType;
        public final List<DailyPattern> dailyPatterns;
        
        public SeasonalPattern(String patternType, List<DailyPattern> dailyPatterns) {
            this.patternType = patternType;
            this.dailyPatterns = dailyPatterns;
        }
    }
    
    public static class DailyPattern {
        public final String dayName;
        public final double averageValue;
        
        public DailyPattern(String dayName, double averageValue) {
            this.dayName = dayName;
            this.averageValue = averageValue;
        }
    }
    
    public static class Anomaly {
        public final long timestamp;
        public final String metricType;
        public final double actualValue;
        public final double expectedValue;
        public final String severity;
        public final String description;
        
        public Anomaly(long timestamp, String metricType, double actualValue, 
                       double expectedValue, String severity, String description) {
            this.timestamp = timestamp;
            this.metricType = metricType;
            this.actualValue = actualValue;
            this.expectedValue = expectedValue;
            this.severity = severity;
            this.description = description;
        }
    }
    
    public static class Forecast {
        public final TrendPeriod period;
        public final int predictedValue;
        public final int lowerBound;
        public final int upperBound;
        public final float confidence;
        public final String confidenceLevel;
        
        public Forecast(TrendPeriod period, int predictedValue, int lowerBound, 
                        int upperBound, float confidence, String confidenceLevel) {
            this.period = period;
            this.predictedValue = predictedValue;
            this.lowerBound = lowerBound;
            this.upperBound = upperBound;
            this.confidence = confidence;
            this.confidenceLevel = confidenceLevel;
        }
    }
    
    public static class MovingAverage {
        public final long timestamp;
        public final int period;
        public final double average;
        
        public MovingAverage(long timestamp, int period, double average) {
            this.timestamp = timestamp;
            this.period = period;
            this.average = average;
        }
    }
    
    public static class DeliveryRateTrend {
        public final TrendPeriod period;
        public final List<DailyDeliveryRate> dailyRates;
        public final float growthRate;
        public final String trendDirection;
        
        public DeliveryRateTrend(TrendPeriod period, List<DailyDeliveryRate> dailyRates, 
                               float growthRate, String trendDirection) {
            this.period = period;
            this.dailyRates = dailyRates;
            this.growthRate = growthRate;
            this.trendDirection = trendDirection;
        }
    }
    
    public static class DailyDeliveryRate {
        public final long date;
        public final float deliveryRate;
        
        public DailyDeliveryRate(long date, float deliveryRate) {
            this.date = date;
            this.deliveryRate = deliveryRate;
        }
    }
    
    public static class CampaignTrend {
        public final TrendPeriod period;
        public final List<DailyCampaignMetrics> dailyCampaigns;
        public final float growthRate;
        public final String trendDirection;
        
        public CampaignTrend(TrendPeriod period, List<DailyCampaignMetrics> dailyCampaigns, 
                            float growthRate, String trendDirection) {
            this.period = period;
            this.dailyCampaigns = dailyCampaigns;
            this.growthRate = growthRate;
            this.trendDirection = trendDirection;
        }
    }
    
    public static class DailyCampaignMetrics {
        public final long date;
        public final int totalCampaigns;
        public final int activeCampaigns;
        public final float successRate;
        
        public DailyCampaignMetrics(long date, int totalCampaigns, int activeCampaigns, float successRate) {
            this.date = date;
            this.totalCampaigns = totalCampaigns;
            this.activeCampaigns = activeCampaigns;
            this.successRate = successRate;
        }
    }
}
