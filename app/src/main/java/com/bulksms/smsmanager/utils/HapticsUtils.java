package com.bulksms.smsmanager.utils;

import android.Manifest;
import android.content.Context;
import android.os.VibrationEffect;
import android.os.Vibrator;
import androidx.annotation.RequiresPermission;

/**
 * Utility class for haptic feedback
 */
public class HapticsUtils {
    private static HapticsUtils instance;
    private final Vibrator vibrator;
    
    private HapticsUtils(Context context) {
        this.vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
    }
    
    public static HapticsUtils getInstance(Context context) {
        if (instance == null) {
            instance = new HapticsUtils(context);
        }
        return instance;
    }
    
    @RequiresPermission(Manifest.permission.VIBRATE)
    public void trigger(HapticType type) {
        if (vibrator == null || !vibrator.hasVibrator()) {
            return;
        }
        
        try {
            switch (type) {
                case SELECTION:
                    // Light tap for selection
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        vibrator.vibrate(VibrationEffect.createOneShot(10, VibrationEffect.DEFAULT_AMPLITUDE));
                    } else {
                        vibrator.vibrate(10);
                    }
                    break;
                    
                case SUCCESS:
                    // Double tap for success
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        long[] pattern = {0, 50, 50, 50};
                        vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1));
                    } else {
                        long[] pattern = {0, 50, 50, 50};
                        vibrator.vibrate(pattern, -1);
                    }
                    break;
                    
                case ERROR:
                    // Long vibration for error
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        vibrator.vibrate(VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE));
                    } else {
                        vibrator.vibrate(200);
                    }
                    break;
                    
                case WARNING:
                    // Medium vibration for warning
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        vibrator.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE));
                    } else {
                        vibrator.vibrate(100);
                    }
                    break;
                    
                case LIGHT:
                    // Very light vibration
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        vibrator.vibrate(VibrationEffect.createOneShot(5, VibrationEffect.DEFAULT_AMPLITUDE));
                    } else {
                        vibrator.vibrate(5);
                    }
                    break;
            }
        } catch (Exception e) {
            // Ignore vibration errors
        }
    }
    
    public enum HapticType {
        SELECTION,
        SUCCESS,
        ERROR,
        WARNING,
        LIGHT
    }
}
