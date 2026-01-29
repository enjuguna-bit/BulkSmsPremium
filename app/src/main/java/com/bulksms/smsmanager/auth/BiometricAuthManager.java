package com.bulksms.smsmanager.auth;

import android.content.Context;
import android.content.SharedPreferences;
import android.hardware.biometrics.BiometricPrompt;
import android.os.Build;
import android.os.CancellationSignal;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.core.content.ContextCompat;

import com.bulksms.smsmanager.utils.ToastUtils;

import java.util.concurrent.Executor;

/**
 * Mock Biometric Authentication Manager
 */
public class BiometricAuthManager {
    private static BiometricAuthManager instance;
    private final Context context;
    private final SharedPreferences prefs;
    
    private BiometricAuthManager(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = context.getSharedPreferences("biometric", Context.MODE_PRIVATE);
    }
    
    public static BiometricAuthManager getInstance(Context context) {
        if (instance == null) {
            instance = new BiometricAuthManager(context);
        }
        return instance;
    }
    
    public BiometricStatus isBiometricAvailable() {
        // Mock implementation - in real app, check actual biometric hardware
        return BiometricStatus.AVAILABLE;
    }
    
    public boolean isBiometricEnabled() {
        return prefs.getBoolean("biometric_enabled", false);
    }
    
    public void enableBiometric(Context activityContext, BiometricAuthCallback callback) {
        // Mock implementation - in real app, show actual biometric prompt
        prefs.edit().putBoolean("biometric_enabled", true).apply();
        callback.onSuccess();
    }
    
    public void disableBiometric() {
        prefs.edit().putBoolean("biometric_enabled", false).apply();
    }
    
    public void authenticate(Context activityContext, BiometricAuthCallback callback) {
        // Mock implementation - in real app, show actual biometric prompt
        if (isBiometricEnabled()) {
            callback.onSuccess();
        } else {
            callback.onError("Biometric not enabled");
        }
    }
    
    public String getStatusMessage() {
        BiometricStatus status = isBiometricAvailable();
        boolean isEnabled = isBiometricEnabled();
        
        if (status != BiometricStatus.AVAILABLE) {
            return "Biometric not available on this device";
        } else if (isEnabled) {
            return "Biometric authentication is enabled";
        } else {
            return "Tap to enable biometric authentication";
        }
    }
}
