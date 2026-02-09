package com.afriserve.smsmanager.ui.analytics;

import android.app.Application;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.afriserve.smsmanager.ui.analytics.AnalyticsData;

import java.util.HashMap;
import java.util.Map;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;

/**
 * ViewModel for Analytics Dashboard
 */
@HiltViewModel
public class AnalyticsViewModel extends AndroidViewModel {
    private static final String TAG = "AnalyticsViewModel";
    
    private final MutableLiveData<AnalyticsData> analyticsData = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>(false);
    private final MutableLiveData<String> status = new MutableLiveData<>("");
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>();
    private final MutableLiveData<Integer> eventCount = new MutableLiveData<>(0);
    
    @Inject
    public AnalyticsViewModel(@NonNull Application application) {
        super(application);
        
        // Initialize with sample data
        loadSampleData();
    }
    
    public LiveData<AnalyticsData> getAnalyticsData() {
        return analyticsData;
    }
    
    public LiveData<Boolean> getIsLoading() {
        return isLoading;
    }
    
    public LiveData<String> getStatus() {
        return status;
    }
    
    public LiveData<String> getErrorMessage() {
        return errorMessage;
    }
    
    public LiveData<Integer> getEventCount() {
        return eventCount;
    }
    
    public void loadAnalyticsData() {
        isLoading.setValue(true);
        status.setValue("Loading analytics data...");
        errorMessage.setValue(null);
        
        try {
            // Simulate API call
            AnalyticsData data = new AnalyticsData();
            
            // Plan information
            data.setPlanName("Free Plan");
            data.setPlanTier("Basic");
            
            // Usage limits
            data.setDailyUsage(45);
            data.setDailyLimit(100);
            data.setDailyPercentage(45);
            
            data.setMonthlyUsage(1250);
            data.setMonthlyLimit(3000);
            data.setMonthlyPercentage(42);
            
            // Campaign and template limits
            data.setCampaignsUsed(8);
            data.setCampaignsLimit(10);
            data.setTemplatesUsed(12);
            data.setTemplatesLimit(20);
            
            // User properties
            Map<String, Object> userProps = new HashMap<>();
            userProps.put("user_id", "user_12345");
            userProps.put("registration_date", "2024-01-15");
            userProps.put("app_version", "1.0.0");
            userProps.put("device_type", "Android");
            userProps.put("country", "KE");
            data.setUserProperties(userProps);
            
            // Upgrade suggestion
            if (data.getDailyPercentage() > 80 || data.getMonthlyPercentage() > 80) {
                data.setUpgradeSuggestion("You're approaching your limits. Consider upgrading to Pro Plan for unlimited messaging.");
            }
            
            analyticsData.setValue(data);
            status.setValue("Analytics data loaded successfully");
            eventCount.setValue(156);
            
        } catch (Exception e) {
            Log.e(TAG, "Error loading analytics data", e);
            errorMessage.setValue("Failed to load analytics data: " + e.getMessage());
            status.setValue("Error loading data");
        } finally {
            isLoading.setValue(false);
        }
    }
    
    public void exportAnalyticsData() {
        status.setValue("Exporting analytics data...");
        
        try {
            // Simulate export
            Thread.sleep(1000);
            status.setValue("Analytics data exported successfully");
            
        } catch (Exception e) {
            Log.e(TAG, "Error exporting data", e);
            errorMessage.setValue("Failed to export data: " + e.getMessage());
        }
    }
    
    public void testCustomEvent() {
        status.setValue("Sending test event...");
        
        try {
            // Simulate event tracking
            Thread.sleep(500);
            status.setValue("Test event sent successfully");
            
            // Update event count
            Integer currentCount = eventCount.getValue();
            if (currentCount != null) {
                eventCount.setValue(currentCount + 1);
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error sending test event", e);
            errorMessage.setValue("Failed to send test event: " + e.getMessage());
        }
    }
    
    public void testErrorEvent() {
        status.setValue("Sending error event...");
        
        try {
            // Simulate error tracking
            Thread.sleep(500);
            status.setValue("Error event sent successfully");
            
        } catch (Exception e) {
            Log.e(TAG, "Error sending error event", e);
            errorMessage.setValue("Failed to send error event: " + e.getMessage());
        }
    }
    
    public void testPerformanceEvent() {
        status.setValue("Sending performance event...");
        
        try {
            // Simulate performance tracking
            Thread.sleep(500);
            status.setValue("Performance event sent successfully");
            
        } catch (Exception e) {
            Log.e(TAG, "Error sending performance event", e);
            errorMessage.setValue("Failed to send performance event: " + e.getMessage());
        }
    }
    
    public void flushEvents() {
        status.setValue("Flushing events...");
        
        try {
            // Simulate flushing
            Thread.sleep(800);
            status.setValue("Events flushed successfully");
            
        } catch (Exception e) {
            Log.e(TAG, "Error flushing events", e);
            errorMessage.setValue("Failed to flush events: " + e.getMessage());
        }
    }
    
    public void resetUserProperties() {
        status.setValue("Resetting user properties...");
        
        try {
            AnalyticsData data = analyticsData.getValue();
            if (data != null) {
                data.setUserProperties(new HashMap<>());
                analyticsData.setValue(data);
            }
            
            status.setValue("User properties reset successfully");
            
        } catch (Exception e) {
            Log.e(TAG, "Error resetting user properties", e);
            errorMessage.setValue("Failed to reset user properties: " + e.getMessage());
        }
    }
    
    private void loadSampleData() {
        // Load initial sample data
        loadAnalyticsData();
    }
}
