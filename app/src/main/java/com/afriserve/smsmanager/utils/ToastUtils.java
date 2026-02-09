package com.afriserve.smsmanager.utils;

import android.content.Context;
import android.widget.Toast;

/**
 * Utility class for showing Toast messages with consistent styling
 */
public class ToastUtils {
    private static ToastUtils instance;
    private final Context context;
    
    private ToastUtils(Context context) {
        this.context = context.getApplicationContext();
    }
    
    public static ToastUtils getInstance(Context context) {
        if (instance == null) {
            instance = new ToastUtils(context);
        }
        return instance;
    }
    
    public void showSuccess(String message) {
        Toast.makeText(context, "✓ " + message, Toast.LENGTH_SHORT).show();
    }
    
    public void showError(String message) {
        Toast.makeText(context, "✗ " + message, Toast.LENGTH_LONG).show();
    }
    
    public void showInfo(String message) {
        Toast.makeText(context, "ℹ " + message, Toast.LENGTH_SHORT).show();
    }
    
    public void showWarning(String message) {
        Toast.makeText(context, "⚠ " + message, Toast.LENGTH_LONG).show();
    }
    
    public void show(String message) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
    }
    
    public void showLong(String message) {
        Toast.makeText(context, message, Toast.LENGTH_LONG).show();
    }
}
