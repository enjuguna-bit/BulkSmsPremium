package com.afriserve.smsmanager.utils;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import java.util.ArrayList;
import java.util.List;

/**
 * Permission manager for SMS and related functionality
 * Handles runtime permissions and provides utility methods for checking permissions
 */
public class PermissionManager {
    private static final String TAG = "PermissionManager";
    private static final String FEATURE_TELEPHONY_MESSAGING = "android.hardware.telephony.messaging";
    private static PermissionManager instance;
    
    private PermissionManager() {}
    
    public static synchronized PermissionManager getInstance(Context context) {
        if (instance == null) {
            instance = new PermissionManager();
        }
        return instance;
    }
    
    // Permission request codes
    public static final int REQUEST_CODE_SMS_PERMISSIONS = 1001;
    public static final int REQUEST_CODE_CONTACTS_PERMISSION = 1002;
    public static final int REQUEST_CODE_NOTIFICATIONS_PERMISSION = 1003;
    
    // Required permissions for SMS functionality
    private static final String[] REQUIRED_SMS_PERMISSIONS = {
        Manifest.permission.SEND_SMS,
        Manifest.permission.RECEIVE_SMS,
        Manifest.permission.READ_SMS,
        Manifest.permission.WRITE_EXTERNAL_STORAGE, // Use WRITE_EXTERNAL_STORAGE instead of WRITE_SMS
        Manifest.permission.RECEIVE_WAP_PUSH,
        Manifest.permission.READ_PHONE_STATE
    };
    
    private static final String[] OPTIONAL_PERMISSIONS = {
        Manifest.permission.READ_CONTACTS,
        Manifest.permission.POST_NOTIFICATIONS
    };

    /**
     * Check if all required SMS permissions are granted
     */
    public static boolean hasAllSmsPermissions(Context context) {
        for (String permission : REQUIRED_SMS_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(context, permission) 
                != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }
    
    /**
     * Check if SMS sending permission is granted
     */
    public static boolean hasSendSmsPermission(Context context) {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) 
               == PackageManager.PERMISSION_GRANTED;
    }
    
    /**
     * Check if SMS receiving permission is granted
     */
    public static boolean hasReceiveSmsPermission(Context context) {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) 
               == PackageManager.PERMISSION_GRANTED;
    }
    
    /**
     * Check if SMS reading permission is granted
     */
    public static boolean hasReadSmsPermission(Context context) {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) 
               == PackageManager.PERMISSION_GRANTED;
    }
    
    /**
     * Check if contacts permission is granted
     */
    public static boolean hasContactsPermission(Context context) {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) 
               == PackageManager.PERMISSION_GRANTED;
    }
    
    /**
     * Check if notifications permission is granted (Android 13+)
     */
    public static boolean hasNotificationsPermission(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) 
                   == PackageManager.PERMISSION_GRANTED;
        }
        return true; // Notifications permission not required on older Android versions
    }
    
    /**
     * Get list of required permissions that are not granted
     */
    public static String[] getMissingSmsPermissions(Context context) {
        List<String> missingPermissions = new ArrayList<>();
        
        for (String permission : REQUIRED_SMS_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(context, permission) 
                != PackageManager.PERMISSION_GRANTED) {
                missingPermissions.add(permission);
            }
        }
        
        return missingPermissions.toArray(new String[0]);
    }
    
    /**
     * Request all required SMS permissions
     */
    public static void requestSmsPermissions(Activity activity) {
        ActivityCompat.requestPermissions(
            activity,
            REQUIRED_SMS_PERMISSIONS,
            REQUEST_CODE_SMS_PERMISSIONS
        );
    }
    
    /**
     * Request contacts permission
     */
    public static void requestContactsPermission(Activity activity) {
        ActivityCompat.requestPermissions(
            activity,
            new String[]{Manifest.permission.READ_CONTACTS},
            REQUEST_CODE_CONTACTS_PERMISSION
        );
    }
    
    /**
     * Request notifications permission (Android 13+)
     */
    public static void requestNotificationsPermission(Activity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.requestPermissions(
                activity,
                new String[]{Manifest.permission.POST_NOTIFICATIONS},
                REQUEST_CODE_NOTIFICATIONS_PERMISSION
            );
        }
    }
    
    /**
     * Check if permission was granted in the request result
     */
    public static boolean isPermissionGranted(int[] grantResults) {
        return grantResults.length > 0 && 
               grantResults[0] == PackageManager.PERMISSION_GRANTED;
    }
    
    /**
     * Check if any permission was permanently denied
     */
    public static boolean hasPermanentlyDeniedPermission(Activity activity, String permission) {
        return !ActivityCompat.shouldShowRequestPermissionRationale(activity, permission);
    }
    
    /**
     * Get user-friendly permission name for display
     */
    public static String getPermissionDisplayName(String permission) {
        switch (permission) {
            case Manifest.permission.SEND_SMS:
                return "Send SMS";
            case Manifest.permission.RECEIVE_SMS:
                return "Receive SMS";
            case Manifest.permission.READ_SMS:
                return "Read SMS";
            case Manifest.permission.WRITE_EXTERNAL_STORAGE:
                return "Write External Storage";
            case Manifest.permission.RECEIVE_WAP_PUSH:
                return "Receive WAP Push";
            case Manifest.permission.READ_PHONE_STATE:
                return "Read Phone State";
            case Manifest.permission.READ_CONTACTS:
                return "Read Contacts";
            case Manifest.permission.POST_NOTIFICATIONS:
                return "Post Notifications";
            default:
                return permission;
        }
    }
    
    /**
     * Check if device supports SMS functionality
     */
    public static boolean isSmsSupported(Context context) {
        return context.getPackageManager().hasSystemFeature(
            PackageManager.FEATURE_TELEPHONY
        );
    }
    
    /**
     * Check if device has SMS capability (even if not required)
     */
    public static boolean hasSmsCapability(Context context) {
        return context.getPackageManager().hasSystemFeature(FEATURE_TELEPHONY_MESSAGING) ||
               context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_TELEPHONY);
    }
    
    /**
     * Get list of all permissions needed for full functionality
     */
    public static String[] getAllRequiredPermissions() {
        List<String> allPermissions = new ArrayList<>();
        
        // Add required SMS permissions
        for (String permission : REQUIRED_SMS_PERMISSIONS) {
            allPermissions.add(permission);
        }
        
        // Add optional permissions
        for (String permission : OPTIONAL_PERMISSIONS) {
            allPermissions.add(permission);
        }
        
        return allPermissions.toArray(new String[0]);
    }
    
    /**
     * Check if all permissions for full functionality are granted
     */
    public static boolean hasAllPermissions(Context context) {
        String[] allPermissions = getAllRequiredPermissions();
        
        for (String permission : allPermissions) {
            if (ContextCompat.checkSelfPermission(context, permission) 
                != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }
    
    /**
     * Request all permissions for full functionality
     */
    public static void requestAllPermissions(Activity activity) {
        ActivityCompat.requestPermissions(
            activity,
            getAllRequiredPermissions(),
            REQUEST_CODE_SMS_PERMISSIONS
        );
    }
}
