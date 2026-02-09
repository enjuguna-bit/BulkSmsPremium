package com.afriserve.smsmanager.auth;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;

import java.util.concurrent.Executor;

/**
 * Biometric Authentication Manager
 */
public class BiometricAuthManager {
    private static BiometricAuthManager instance;
    private final Context context;
    private final SharedPreferences prefs;
    private final BiometricManager biometricManager;
    
    private BiometricAuthManager(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = context.getSharedPreferences("biometric", Context.MODE_PRIVATE);
        this.biometricManager = BiometricManager.from(this.context);
    }
    
    public static BiometricAuthManager getInstance(Context context) {
        if (instance == null) {
            instance = new BiometricAuthManager(context);
        }
        return instance;
    }
    
    public BiometricStatus isBiometricAvailable() {
        int result = biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK);
        switch (result) {
            case BiometricManager.BIOMETRIC_SUCCESS:
                return BiometricStatus.AVAILABLE;
            case BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED:
                return BiometricStatus.NOT_ENROLLED;
            case BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE:
                return BiometricStatus.NO_HARDWARE;
            case BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE:
                return BiometricStatus.HW_UNAVAILABLE;
            default:
                return BiometricStatus.UNKNOWN;
        }
    }
    
    public boolean isBiometricEnabled() {
        return prefs.getBoolean("biometric_enabled", false);
    }
    
    public void enableBiometric(Context activityContext, BiometricAuthCallback callback) {
        BiometricStatus status = isBiometricAvailable();
        if (status != BiometricStatus.AVAILABLE) {
            callback.onError(getStatusMessage());
            return;
        }

        runOnMainThread(activityContext, () -> {
            FragmentActivity activity = requireFragmentActivity(activityContext, callback);
            if (activity == null) {
                return;
            }

            BiometricPrompt prompt = createPrompt(activity, callback, true);
            prompt.authenticate(buildPromptInfo("Confirm to enable biometric authentication"));
        });
    }
    
    public void disableBiometric() {
        prefs.edit().putBoolean("biometric_enabled", false).apply();
    }
    
    public void authenticate(Context activityContext, BiometricAuthCallback callback) {
        if (!isBiometricEnabled()) {
            callback.onError("Biometric not enabled");
            return;
        }

        BiometricStatus status = isBiometricAvailable();
        if (status != BiometricStatus.AVAILABLE) {
            callback.onError(getStatusMessage());
            return;
        }

        runOnMainThread(activityContext, () -> {
            FragmentActivity activity = requireFragmentActivity(activityContext, callback);
            if (activity == null) {
                return;
            }

            BiometricPrompt prompt = createPrompt(activity, callback, false);
            prompt.authenticate(buildPromptInfo("Authenticate to continue"));
        });
    }
    
    public String getStatusMessage() {
        BiometricStatus status = isBiometricAvailable();
        boolean isEnabled = isBiometricEnabled();
        
        switch (status) {
            case AVAILABLE:
                return isEnabled ? "Biometric authentication is enabled" : "Tap to enable biometric authentication";
            case NOT_ENROLLED:
                return "No biometric enrolled. Set up fingerprint/face unlock first.";
            case NO_HARDWARE:
                return "Biometric hardware not available on this device";
            case HW_UNAVAILABLE:
                return "Biometric hardware is currently unavailable";
            default:
                return "Biometric status unknown";
        }
    }

    private void runOnMainThread(Context activityContext, Runnable action) {
        if (activityContext instanceof Activity) {
            ((Activity) activityContext).runOnUiThread(action);
        } else {
            new Handler(Looper.getMainLooper()).post(action);
        }
    }

    private FragmentActivity requireFragmentActivity(Context activityContext, BiometricAuthCallback callback) {
        if (activityContext instanceof FragmentActivity) {
            return (FragmentActivity) activityContext;
        }
        callback.onError("Biometric authentication requires a FragmentActivity");
        return null;
    }

    private BiometricPrompt createPrompt(
        FragmentActivity activity,
        BiometricAuthCallback callback,
        boolean enabling
    ) {
        Executor executor = ContextCompat.getMainExecutor(activity);
        return new BiometricPrompt(activity, executor, new BiometricPrompt.AuthenticationCallback() {
            @Override
            public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                super.onAuthenticationSucceeded(result);
                if (enabling) {
                    prefs.edit().putBoolean("biometric_enabled", true).apply();
                }
                callback.onSuccess();
            }

            @Override
            public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                super.onAuthenticationError(errorCode, errString);
                if (errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON
                    || errorCode == BiometricPrompt.ERROR_USER_CANCELED
                    || errorCode == BiometricPrompt.ERROR_CANCELED) {
                    callback.onCancel();
                } else {
                    callback.onError(errString.toString());
                }
            }
        });
    }

    private BiometricPrompt.PromptInfo buildPromptInfo(String subtitle) {
        return new BiometricPrompt.PromptInfo.Builder()
            .setTitle("Biometric Authentication")
            .setSubtitle(subtitle)
            .setConfirmationRequired(false)
            .setNegativeButtonText("Cancel")
            .build();
    }
}
