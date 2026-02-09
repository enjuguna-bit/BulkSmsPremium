package com.google.firebase.analytics;

import android.content.Context;
import android.os.Bundle;

public class FirebaseAnalytics {
    private FirebaseAnalytics() {}
    public static FirebaseAnalytics getInstance(Context context) { return new FirebaseAnalytics(); }
    public void logEvent(String name, Bundle params) {}
    public void setUserProperty(String name, String value) {}
    public void setUserId(String id) {}
}
