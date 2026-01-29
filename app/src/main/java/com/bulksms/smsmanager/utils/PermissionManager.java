package com.bulksms.smsmanager.utils;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.content.ContextCompat;

/**
 * Utility class for managing app permissions
 */
public class PermissionManager {
    private static PermissionManager instance;
    private final Context context;
    
    private PermissionManager(Context context) {
        this.context = context.getApplicationContext();
    }
    
    public static PermissionManager getInstance(Context context) {
        if (instance == null) {
            instance = new PermissionManager(context);
        }
        return instance;
    }
    
    public boolean hasSmsPermissions() {
        return hasPermission(Manifest.permission.SEND_SMS) &&
               hasPermission(Manifest.permission.READ_SMS) &&
               hasPermission(Manifest.permission.RECEIVE_SMS);
    }
    
    public boolean hasCallPermission() {
        return hasPermission(Manifest.permission.CALL_PHONE);
    }
    
    public boolean hasStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return hasPermission(Manifest.permission.READ_MEDIA_IMAGES) &&
                   hasPermission(Manifest.permission.READ_MEDIA_VIDEO);
        } else {
            return hasPermission(Manifest.permission.READ_EXTERNAL_STORAGE) &&
                   hasPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        }
    }
    
    public boolean hasPhoneStatePermission() {
        return hasPermission(Manifest.permission.READ_PHONE_STATE);
    }
    
    public boolean hasContactsPermission() {
        return hasPermission(Manifest.permission.READ_CONTACTS);
    }
    
    public boolean hasAllRequiredPermissions() {
        return hasSmsPermissions() &&
               hasCallPermission() &&
               hasStoragePermission() &&
               hasPhoneStatePermission() &&
               hasContactsPermission();
    }
    
    private boolean hasPermission(String permission) {
        return ContextCompat.checkSelfPermission(context, permission) 
               == PackageManager.PERMISSION_GRANTED;
    }
    
    public String[] getAllRequiredPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return new String[]{
                Manifest.permission.SEND_SMS,
                Manifest.permission.READ_SMS,
                Manifest.permission.RECEIVE_SMS,
                Manifest.permission.CALL_PHONE,
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_PHONE_STATE,
                Manifest.permission.READ_CONTACTS
            };
        } else {
            return new String[]{
                Manifest.permission.SEND_SMS,
                Manifest.permission.READ_SMS,
                Manifest.permission.RECEIVE_SMS,
                Manifest.permission.CALL_PHONE,
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.READ_PHONE_STATE,
                Manifest.permission.READ_CONTACTS
            };
        }
    }
}
