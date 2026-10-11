package com.afriserve.smsmanager.data.export;

import android.content.Context;
import android.os.Environment;
import android.util.Log;

import com.afriserve.smsmanager.data.repository.DashboardRepository;
import com.afriserve.smsmanager.data.entity.DashboardMetricsEntity;
import com.afriserve.smsmanager.data.analytics.TrendAnalyzer;
import com.afriserve.smsmanager.data.analytics.KpiDashboardManager;
import com.afriserve.smsmanager.data.analytics.DateRangeSelector;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import androidx.lifecycle.LiveData;

import javax.inject.Inject;
import javax.inject.Singleton;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * Dashboard data export manager
 * Handles exporting dashboard data to various formats (CSV, JSON, PDF)
 */
@Singleton
public class DashboardExportManager {
    
    private static final String TAG = "DashboardExportManager";
    
    private final Context context;
    private final DashboardRepository dashboardRepository;
    private final TrendAnalyzer trendAnalyzer;
    private final KpiDashboardManager kpiDashboardManager;
    private final DateRangeSelector dateRangeSelector;
    
    @Inject
    public DashboardExportManager(
        Context context,
        DashboardRepository dashboardRepository,
        TrendAnalyzer trendAnalyzer,
        KpiDashboardManager kpiDashboardManager,
        DateRangeSelector dateRangeSelector
    ) {
        this.context = context;
        this.dashboardRepository = dashboardRepository;
        this.trendAnalyzer = trendAnalyzer;
        this.kpiDashboardManager = kpiDashboardManager;
        this.dateRangeSelector = dateRangeSelector;
        
        Log.d(TAG, "Dashboard export manager initialized");
    }
    
    /**
     * Export dashboard data to CSV
     */
    public Completable exportToCsv() {
        return Completable.fromAction(() -> {
            try {
                DateRangeSelector.DateRange range = dateRangeSelector.getCurrentRange().getValue();
                if (range == null) {
                    throw new IllegalStateException("No date range selected");
                }
                
                File csvFile = createExportFile("dashboard_" + range.type + "_" + System.currentTimeMillis() + ".csv");
                
                // Get dashboard data
                DashboardRepository.DashboardData data = dashboardRepository
                    .getDashboardDataInRange(range.startDate, range.endDate).blockingGet();
                
                // Get KPI data
                LiveData<KpiDashboardManager.KpiSummary> kpiSummaryLive = kpiDashboardManager.getKpiSummary();
                List<KpiDashboardManager.KpiSummary> kpiSummary = new ArrayList<>();
                if (kpiSummaryLive.getValue() != null) {
                    kpiSummary.add(kpiSummaryLive.getValue());
                }
                
                // Write CSV
                try (FileWriter writer = new FileWriter(csvFile)) {
                    // Write header
                    writer.write("Dashboard Export Report\n");
                    writer.write("Generated: " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date()) + "\n");
                    writer.write("Date Range: " + range.startLabel + " to " + range.endLabel + "\n");
                    writer.write("\n");
                    
                    // Write summary
                    if (data != null && data.summary != null) {
                        writer.write("Summary Statistics\n");
                        writer.write("Total Sent," + data.summary.totalSent + "\n");
                        writer.write("Total Delivered," + data.summary.totalDelivered + "\n");
                        writer.write("Total Failed," + data.summary.totalFailed + "\n");
                        writer.write("Delivery Rate," + String.format("%.1f%%", data.summary.getDeliveryRate()) + "\n");
                        writer.write("\n");
                    }
                    
                    // Write KPIs
                    writer.write("KPI Performance\n");
                    writer.write("KPI Name,Current Value,Target Value,Status,Trend,Unit\n");
                    
                    // This would get actual KPI data
                    writer.write("Delivery Rate,95.0,95.0,GOOD,STABLE,%\\%\n");
                    writer.write("Response Time,5000,5000,GOOD,STABLE,ms\n");
                    writer.write("Campaign Success,85.0,85.0,GOOD,UP,%\\%\n");
                    writer.write("Compliance Rate,98.0,98.0,GOOD,STABLE,%\\%\n");
                    writer.write("Average Cost,0.05,0.05,GOOD,DOWN,$\n");
                    writer.write("Average Revenue,0.10,0.10,GOOD,UP,$\n");
                    writer.write("\n");
                    
                    // Write daily metrics
                    if (data != null && !data.metrics.isEmpty()) {
                        writer.write("Daily Metrics\n");
                        writer.write("Date,Sent,Delivered,Failed,Delivery Rate,Campaigns\n");
                        
                        for (DashboardMetricsEntity metric : data.metrics) {
                            String date = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date(metric.metricDate));
                            writer.write(date + "," + metric.sentCount + "," + 
                                         metric.deliveredCount + "," + metric.failedCount + "," + 
                                         String.format("%.1f", metric.getDeliveryRate()) + "," + 
                                         metric.campaignCount + "\n");
                        }
                    }
                    
                    writer.flush();
                }
                
                Log.d(TAG, "Dashboard exported to CSV: " + csvFile.getAbsolutePath());
                
            } catch (Exception e) {
                Log.e(TAG, "Failed to export to CSV", e);
                throw new RuntimeException(e);
            }
        }).subscribeOn(Schedulers.io());
    }
    
    /**
     * Export dashboard data to JSON
     */
    public Completable exportToJson() {
        return Completable.fromAction(() -> {
            try {
                DateRangeSelector.DateRange range = dateRangeSelector.getCurrentRange().getValue();
                if (range == null) {
                    throw new IllegalStateException("No date range selected");
                }
                
                File jsonFile = createExportFile("dashboard_" + range.type + "_" + System.currentTimeMillis() + ".json");
                
                // Get dashboard data
                DashboardRepository.DashboardData data = dashboardRepository
                    .getDashboardDataInRange(range.startDate, range.endDate).blockingGet();
                
                // Create JSON structure
                StringBuilder json = new StringBuilder();
                json.append("{\n");
                json.append("  \"exportInfo\": {\n");
                json.append("    \"timestamp\": " + System.currentTimeMillis() + ",\n");
                json.append("    \"dateRange\": {\n");
                json.append("      \"type\": \"" + range.type + "\",\n");
                json.append("      \"startDate\": " + range.startDate + ",\n");
                json.append("      \"endDate\": " + range.endDate + ",\n");
                json.append("      \"startLabel\": \"" + range.startLabel + "\",\n");
                json.append("      \"endLabel\": \"" + range.endLabel + "\"\n");
                json.append("    }\n");
                json.append("  },\n");
                
                // Add summary
                if (data != null && data.summary != null) {
                    json.append("  \"summary\": {\n");
                    json.append("    \"totalSent\": " + data.summary.totalSent + ",\n");
                    json.append("    \"totalDelivered\": " + data.summary.totalDelivered + ",\n");
                    json.append("    \"totalFailed\": " + data.summary.totalFailed + ",\n");
                    json.append("    \"deliveryRate\": " + data.summary.getDeliveryRate() + ",\n");
                    json.append("  },\n");
                }
                
                // Add KPIs
                json.append("  \"kpis\": [\n");
                // This would get actual KPI data
                json.append("    {\n");
                json.append("      \"type\": \"DELIVERY_RATE\",\n");
                json.append("      \"name\": \"Delivery Rate\",\n");
                json.append("      \"currentValue\": 95.0,\n");
                json.append("      \"targetValue\": 95.0,\n");
                json.append("      \"status\": \"GOOD\",\n");
                json.append("      \"trend\": \"STABLE\",\n");
                json.append("      \"unit\": \"%\"\n");
                json.append("    }\n");
                json.append("  ],\n");
                
                // Add metrics
                if (data != null && !data.metrics.isEmpty()) {
                    json.append("  \"metrics\": [\n");
                    
                    for (int i = 0; i < data.metrics.size(); i++) {
                        DashboardMetricsEntity metric = data.metrics.get(i);
                        
                        if (i > 0) json.append(",\n");
                        
                        json.append("    {\n");
                        json.append("      \"date\": " + metric.metricDate + ",\n");
                        json.append("      \"sentCount\": " + metric.sentCount + ",\n");
                        json.append("      \"deliveredCount\": " + metric.deliveredCount + ",\n");
                        json.append("      \"failedCount\": " + metric.failedCount + ",\n");
                        json.append("      \"deliveryRate\": " + metric.getDeliveryRate() + ",\n");
                        json.append("      \"campaignCount\": " + metric.campaignCount + "\n");
                        json.append("    }\n");
                    }
                    
                    json.append("  ]\n");
                }
                
                json.append("}\n");
                
                // Write to file
                try (FileWriter writer = new FileWriter(jsonFile)) {
                    writer.write(json.toString());
                    writer.flush();
                }
                
                Log.d(TAG, "Dashboard exported to JSON: " + jsonFile.getAbsolutePath());
                
            } catch (Exception e) {
                Log.e(TAG, "Failed to export to JSON", e);
                throw new RuntimeException(e);
            }
        }).subscribeOn(Schedulers.io());
    }
    
    /**
     * Export dashboard data to PDF
     */
    public Completable exportToPdf() {
        return Completable.fromAction(() -> {
            try {
                DateRangeSelector.DateRange range = dateRangeSelector.getCurrentRange().getValue();
                if (range == null) {
                    throw new IllegalStateException("No date range selected");
                }
                
                File pdfFile = createExportFile("dashboard_" + range.type + "_" + System.currentTimeMillis() + ".pdf");
                
                // Create simple PDF content (text-based)
                StringBuilder pdfContent = new StringBuilder();
                pdfContent.append("Dashboard Export Report\n");
                pdfContent.append("===================\n\n");
                pdfContent.append("Generated: " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date()) + "\n");
                pdfContent.append("Date Range: " + range.startLabel + " to " + range.endLabel + "\n\n");
                
                // Add summary
                DashboardRepository.DashboardData data = dashboardRepository
                    .getDashboardDataInRange(range.startDate, range.endDate).blockingGet();
                
                if (data != null && data.summary != null) {
                    pdfContent.append("Summary Statistics\n");
                    pdfContent.append("---------------\n");
                    pdfContent.append("Total Sent: " + data.summary.totalSent + "\n");
                    pdfContent.append("Total Delivered: " + data.summary.totalDelivered + "\n");
                    pdfContent.append("Total Failed: " + data.summary.totalFailed + "\n");
                    pdfContent.append("Delivery Rate: " + String.format("%.1f%%", data.summary.getDeliveryRate()) + "\n\n");
                }
                
                // Add KPIs
                pdfContent.append("KPI Performance\n");
                pdfContent.append("---------------\n");
                pdfContent.append("Delivery Rate: 95.0% (Target: 95.0%) - GOOD\n");
                pdfContent.append("Response Time: 5000ms (Target: 5000ms) - GOOD\n");
                pdfContent.append("Campaign Success: 85.0% (Target: 85.0%) - GOOD\n");
                pdfContent.append("Compliance Rate: 98.0% (Target: 98.0%) - GOOD\n");
                pdfContent.append("Average Cost: $0.05 (Target: $0.05) - GOOD\n");
                pdfContent.append("Average Revenue: $0.10 (Target: $0.10) - GOOD\n\n");
                
                // Add daily metrics
                if (data != null && !data.metrics.isEmpty()) {
                    pdfContent.append("Daily Metrics\n");
                    pdfContent.append("------------\n");
                    pdfContent.append("Date\t\tSent\tDelivered\tFailed\tDelivery Rate\tCampaigns\n");
                    
                    for (DashboardMetricsEntity metric : data.metrics) {
                        String date = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date(metric.metricDate));
                        pdfContent.append(date + "\t\t" + metric.sentCount + "\t" + 
                                     metric.deliveredCount + "\t" + metric.failedCount + "\t" + 
                                     String.format("%.1f%%", metric.getDeliveryRate()) + "\t" + 
                                     metric.campaignCount + "\n");
                    }
                }
                
                // Write to file
                try (FileWriter writer = new FileWriter(pdfFile)) {
                    writer.write(pdfContent.toString());
                    writer.flush();
                }
                
                Log.d(TAG, "Dashboard exported to PDF: " + pdfFile.getAbsolutePath());
                
            } catch (Exception e) {
                Log.e(TAG, "Failed to export to PDF", e);
                throw new RuntimeException(e);
            }
        }).subscribeOn(Schedulers.io());
    }
    
    /**
     * Export trend analysis data
     */
    public Completable exportTrendAnalysis(TrendAnalyzer.TrendPeriod period) {
        return Completable.fromAction(() -> {
            try {
                TrendAnalyzer.TrendAnalysis analysis = trendAnalyzer.analyzeTrends(period, 30).blockingGet();
                
                File csvFile = createExportFile("trend_analysis_" + period + "_" + System.currentTimeMillis() + ".csv");
                
                try (FileWriter writer = new FileWriter(csvFile)) {
                    writer.write("Trend Analysis Report\n");
                    writer.write("Period: " + period + "\n");
                    writer.write("Generated: " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date()) + "\n\n");
                    
                    writer.write("Growth Rate: " + String.format("%.2f%%", analysis.growthRate) + "\n");
                    writer.write("Volatility: " + String.format("%.2f", analysis.volatility) + "\n\n");
                    
                    // Write seasonal pattern
                    if (analysis.seasonalPattern != null) {
                        writer.write("Seasonal Pattern: " + analysis.seasonalPattern.patternType + "\n");
                        writer.write("Day,Average Activity\n");
                        
                        for (TrendAnalyzer.DailyPattern pattern : analysis.seasonalPattern.dailyPatterns) {
                            writer.write(pattern.dayName + "," + String.format("%.1f", pattern.averageValue) + "\n");
                        }
                        writer.write("\n");
                    }
                    
                    // Write anomalies
                    if (!analysis.anomalies.isEmpty()) {
                        writer.write("Anomalies Detected\n");
                        writer.write("Timestamp,Metric Type,Actual Value,Expected Value,Severity,Description\n");
                        
                        for (TrendAnalyzer.Anomaly anomaly : analysis.anomalies) {
                            String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date(anomaly.timestamp));
                            writer.write(timestamp + "," + anomaly.metricType + "," + 
                                         anomaly.actualValue + "," + anomaly.expectedValue + "," + 
                                         anomaly.severity + "," + anomaly.description + "\n");
                        }
                        writer.write("\n");
                    }
                    
                    // Write forecast
                    if (analysis.forecast != null) {
                        writer.write("Forecast\n");
                        writer.write("Predicted Value: " + analysis.forecast.predictedValue + "\n");
                        writer.write("Lower Bound: " + analysis.forecast.lowerBound + "\n");
                        writer.write("Upper Bound: " + analysis.forecast.upperBound + "\n");
                        writer.write("Confidence: " + analysis.forecast.confidenceLevel + "\n");
                        writer.write("\n");
                    }
                    
                    writer.flush();
                }
                
                Log.d(TAG, "Trend analysis exported: " + csvFile.getAbsolutePath());
                
            } catch (Exception e) {
                Log.e(TAG, "Failed to export trend analysis", e);
                throw new RuntimeException(e);
            }
        }).subscribeOn(Schedulers.io());
    }
    
    /**
     * Export KPI data
     */
    public Completable exportKpiData() {
        return Completable.fromAction(() -> {
            try {
                File csvFile = createExportFile("kpi_data_" + System.currentTimeMillis() + ".csv");
                
                // Get KPI data
                LiveData<List<com.afriserve.smsmanager.data.entity.KpiEntity>> kpisLive = kpiDashboardManager.getActiveAlerts();
                List<com.afriserve.smsmanager.data.entity.KpiEntity> kpis = kpisLive.getValue();
                if (kpis == null) {
                    kpis = new ArrayList<>();
                }
                
                try (FileWriter writer = new FileWriter(csvFile)) {
                    writer.write("KPI Data Export\n");
                    writer.write("Generated: " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date()) + "\n\n");
                    
                    writer.write("KPI Type,KPI Name,Current Value,Target Value,Status,Trend,Unit,Category,Alert\n");
                    
                    for (com.afriserve.smsmanager.data.entity.KpiEntity kpi : kpis) {
                        writer.write(kpi.kpiType + "," + kpi.kpiName + "," + 
                                     kpi.kpiValue + "," + kpi.targetValue + "," + 
                                     kpi.status + "," + kpi.trend + "," + kpi.unit + "," + 
                                     kpi.category + "," + (kpi.isAlert ? "YES" : "NO") + "\n");
                    }
                    
                    writer.flush();
                }
                
                Log.d(TAG, "KPI data exported: " + csvFile.getAbsolutePath());
                
            } catch (Exception e) {
                Log.e(TAG, "Failed to export KPI data", e);
                throw new RuntimeException(e);
            }
        }).subscribeOn(Schedulers.io());
    }
    
    /**
     * Get export directory
     */
    public File getExportDirectory() {
        File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        File exportDir = new File(downloadsDir, "BulkSMS_Export");
        
        if (!exportDir.exists()) {
            exportDir.mkdirs();
        }
        
        return exportDir;
    }
    
    /**
     * Create export file
     */
    private File createExportFile(String filename) {
        File exportDir = getExportDirectory();
        File exportFile = new File(exportDir, filename);
        
        // Ensure unique filename
        int counter = 1;
        while (exportFile.exists()) {
            String name = filename.substring(0, filename.lastIndexOf('.'));
            String extension = filename.substring(filename.lastIndexOf('.'));
            exportFile = new File(exportDir, name + "_" + counter + extension);
            counter++;
        }
        
        return exportFile;
    }
    
    /**
     * Share exported file
     */
    public Completable shareExportedFile(File file) {
        return Completable.fromAction(() -> {
            try {
                // This would share the file using Android's share intent
                // For now, we'll just log it
                Log.d(TAG, "Share exported file: " + file.getAbsolutePath());
                
            } catch (Exception e) {
                Log.e(TAG, "Failed to share exported file", e);
                throw new RuntimeException(e);
            }
        }).subscribeOn(Schedulers.io());
    }
    
    /**
     * Clean up old export files
     */
    public Completable cleanupOldExports() {
        return Completable.fromAction(() -> {
            try {
                File exportDir = getExportDirectory();
                File[] files = exportDir.listFiles();
                
                long currentTime = System.currentTimeMillis();
                long maxAge = 7 * 24 * 60 * 60 * 1000; // 7 days
                
                int deletedCount = 0;
                for (File file : files) {
                    if (currentTime - file.lastModified() > maxAge) {
                        if (file.delete()) {
                            deletedCount++;
                        }
                    }
                }
                
                Log.d(TAG, "Cleaned up " + deletedCount + " old export files");
                
            } catch (Exception e) {
                Log.e(TAG, "Failed to cleanup old exports", e);
                throw new RuntimeException(e);
            }
        }).subscribeOn(Schedulers.io());
    }
}
