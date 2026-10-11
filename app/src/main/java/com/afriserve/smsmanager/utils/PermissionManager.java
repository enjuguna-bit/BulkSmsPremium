package com.afriserve.smsmanager.utils;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * PermissionManager — v2
 *
 * Changes vs v1:
 *  - Removed mutable singleton. All methods are static utility methods.
 *  - Removed WRITE_EXTERNAL_STORAGE from required SMS permissions.
 *    That permission is a no-op on API 29+ and was never needed for SMS.
 *  - Added POST_NOTIFICATIONS guard for API 33+ (Tiramisu).
 *  - getMissingSmsPermissions only returns permissions applicable to the
 *    current API level.
 *  - Display-name map extended for all permissions.
 */
public final class PermissionManager {

    private PermissionManager() {}

    public static PermissionManager getInstance(@NonNull Context context) {
        return new PermissionManager();
    }

    // Request codes
    public static final int REQUEST_CODE_SMS           = 1001;
    public static final int REQUEST_CODE_CONTACTS      = 1002;
    public static final int REQUEST_CODE_NOTIFICATIONS = 1003;

    // Core SMS permissions (always required)
    private static final String[] CORE_SMS_PERMISSIONS = {
        Manifest.permission.SEND_SMS,
        Manifest.permission.RECEIVE_SMS,
        Manifest.permission.READ_SMS,
        Manifest.permission.READ_PHONE_STATE
    };

    // ── Checkers ──────────────────────────────────────────────────────────────

    public static boolean hasSendSmsPermission(@NonNull Context ctx) {
        return has(ctx, Manifest.permission.SEND_SMS);
    }

    public static boolean hasReceiveSmsPermission(@NonNull Context ctx) {
        return has(ctx, Manifest.permission.RECEIVE_SMS);
    }

    public static boolean hasReadSmsPermission(@NonNull Context ctx) {
        return has(ctx, Manifest.permission.READ_SMS);
    }

    public static boolean hasContactsPermission(@NonNull Context ctx) {
        return has(ctx, Manifest.permission.READ_CONTACTS);
    }

    /** True on API < 33; on API 33+ checks POST_NOTIFICATIONS. */
    public static boolean hasNotificationsPermission(@NonNull Context ctx) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return has(ctx, Manifest.permission.POST_NOTIFICATIONS);
        }
        return true;
    }

    /** True when all core SMS permissions are granted. */
    public static boolean hasAllSmsPermissions(@NonNull Context ctx) {
        for (String p : CORE_SMS_PERMISSIONS) {
            if (!has(ctx, p)) return false;
        }
        return true;
    }

    /** Returns core SMS permissions not yet granted. */
    @NonNull
    public static String[] getMissingSmsPermissions(@NonNull Context ctx) {
        List<String> missing = new ArrayList<>();
        for (String p : CORE_SMS_PERMISSIONS) {
            if (!has(ctx, p)) missing.add(p);
        }
        return missing.toArray(new String[0]);
    }

    public static boolean isSmsSupported(@NonNull Context ctx) {
        return ctx.getPackageManager().hasSystemFeature(PackageManager.FEATURE_TELEPHONY);
    }

    public static boolean isPermissionGranted(int[] grantResults) {
        return grantResults != null && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED;
    }

    public static boolean hasPermanentlyDeniedPermission(
            @NonNull Activity activity, @NonNull String permission) {
        return !ActivityCompat.shouldShowRequestPermissionRationale(activity, permission);
    }

    // ── Request helpers ───────────────────────────────────────────────────────

    public static void requestSmsPermissions(@NonNull Activity activity) {
        ActivityCompat.requestPermissions(activity, CORE_SMS_PERMISSIONS, REQUEST_CODE_SMS);
    }

    public static void requestContactsPermission(@NonNull Activity activity) {
        ActivityCompat.requestPermissions(activity,
                new String[]{Manifest.permission.READ_CONTACTS}, REQUEST_CODE_CONTACTS);
    }

    public static void requestNotificationsPermission(@NonNull Activity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.requestPermissions(activity,
                    new String[]{Manifest.permission.POST_NOTIFICATIONS},
                    REQUEST_CODE_NOTIFICATIONS);
        }
    }

    /** Requests all permissions needed for full app functionality. */
    public static void requestAllPermissions(@NonNull Activity activity) {
        List<String> all = new ArrayList<>(Arrays.asList(CORE_SMS_PERMISSIONS));
        all.add(Manifest.permission.READ_CONTACTS);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            all.add(Manifest.permission.POST_NOTIFICATIONS);
        }
        ActivityCompat.requestPermissions(activity, all.toArray(new String[0]), REQUEST_CODE_SMS);
    }

    // ── Display names ─────────────────────────────────────────────────────────

    @NonNull
    public static String getPermissionDisplayName(@NonNull String permission) {
        switch (permission) {
            case Manifest.permission.SEND_SMS:           return "Send SMS";
            case Manifest.permission.RECEIVE_SMS:        return "Receive SMS";
            case Manifest.permission.READ_SMS:           return "Read SMS";
            case Manifest.permission.READ_PHONE_STATE:   return "Read Phone State";
            case Manifest.permission.RECEIVE_WAP_PUSH:   return "Receive WAP Push";
            case Manifest.permission.READ_CONTACTS:      return "Read Contacts";
            case Manifest.permission.POST_NOTIFICATIONS: return "Show Notifications";
            default:
                int dot = permission.lastIndexOf('.');
                return dot >= 0 ? permission.substring(dot + 1) : permission;
        }
    }

    // ── Private ───────────────────────────────────────────────────────────────

    private static boolean has(@NonNull Context ctx, @NonNull String permission) {
        return ContextCompat.checkSelfPermission(ctx, permission)
                == PackageManager.PERMISSION_GRANTED;
    }
}
