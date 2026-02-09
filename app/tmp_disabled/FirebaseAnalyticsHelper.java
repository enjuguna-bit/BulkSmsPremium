package com.bulksms.smsmanager.analytics;

import android.content.Context;
import android.os.Bundle;

import com.google.firebase.analytics.FirebaseAnalytics;

/**
 * Helper class for Firebase Analytics
 */
public class FirebaseAnalyticsHelper {
    private final FirebaseAnalytics firebaseAnalytics;
    
    public FirebaseAnalyticsHelper(Context context) {
        firebaseAnalytics = FirebaseAnalytics.getInstance(context);
    }
    
    public void logEvent(String eventName, Bundle parameters) {
        firebaseAnalytics.logEvent(eventName, parameters);
    }
    
    public void logEvent(String eventName) {
        firebaseAnalytics.logEvent(eventName, null);
    }
    
    public void logSubscriptionActivated(String planId, double amount) {
        Bundle params = new Bundle();
        params.putString("plan_id", planId);
        params.putDouble("amount", amount);
        params.putString("currency", "KES");
        firebaseAnalytics.logEvent("subscription_activated", params);
    }
    
    public void logSmsSent(int count, String planId) {
        Bundle params = new Bundle();
        params.putInt("count", count);
        params.putString("plan_id", planId);
        firebaseAnalytics.logEvent("sms_sent", params);
    }
    
    public void logUsageLimitExceeded(String limitType, String planId) {
        Bundle params = new Bundle();
        params.putString("limit_type", limitType);
        params.putString("plan_id", planId);
        firebaseAnalytics.logEvent("usage_limit_exceeded", params);
    }
    
    public void setUserProperty(String name, String value) {
        firebaseAnalytics.setUserProperty(name, value);
    }
    
    public void setUserId(String userId) {
        firebaseAnalytics.setUserId(userId);
    }
}
