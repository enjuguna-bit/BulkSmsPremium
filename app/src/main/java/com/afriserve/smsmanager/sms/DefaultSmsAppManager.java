package com.afriserve.smsmanager.sms;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.provider.Telephony;
import android.util.Log;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import javax.inject.Inject;
import javax.inject.Singleton;

import dagger.hilt.android.qualifiers.ApplicationContext;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * Manager for Default SMS App functionality
 * Handles becoming the default SMS app and related permissions
 */
@Singleton
public class DefaultSmsAppManager {

    private static final String TAG = "DefaultSmsAppManager";

    private final Context context;

    @Inject
    public DefaultSmsAppManager(@ApplicationContext @androidx.annotation.NonNull Context context) {
        this.context = context;
    }

    /**
     * Check if the app is currently the default SMS app
     */
    public boolean isDefaultSmsApp() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            String defaultPackage = Telephony.Sms.getDefaultSmsPackage(context);
            return context.getPackageName().equals(defaultPackage);
        }
        return false; // Default SMS app concept introduced in KitKat
    }

    /**
     * Check if the app can become the default SMS app
     */
    public boolean canBecomeDefaultSmsApp() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT;
    }

    /**
     * Check if app has required permissions for default SMS app
     */
    public boolean hasRequiredPermissions() {
        String[] requiredPermissions = {
                android.Manifest.permission.SEND_SMS,
                android.Manifest.permission.RECEIVE_SMS,
                android.Manifest.permission.READ_SMS,
                android.Manifest.permission.RECEIVE_MMS,
                android.Manifest.permission.READ_CONTACTS
        };

        for (String permission : requiredPermissions) {
            if (ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }

        return true;
    }

    /**
     * Get missing permissions for default SMS app
     */
    public String[] getMissingPermissions() {
        java.util.List<String> missing = new java.util.ArrayList<>();

        String[] requiredPermissions = {
                android.Manifest.permission.SEND_SMS,
                android.Manifest.permission.RECEIVE_SMS,
                android.Manifest.permission.READ_SMS,
                android.Manifest.permission.RECEIVE_MMS,
                android.Manifest.permission.READ_CONTACTS
        };

        for (String permission : requiredPermissions) {
            if (ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
                missing.add(permission);
            }
        }

        return missing.toArray(new String[0]);
    }

    /**
     * Request to become the default SMS app
     * Returns Intent that should be launched from Activity
     */
    public Intent createDefaultSmsAppIntent() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            Intent intent = new Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT);
            intent.putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, context.getPackageName());
            return intent;
        }
        return null;
    }

    /**
     * Show dialog to request becoming default SMS app
     */
    public void requestDefaultSmsAppStatus(AppCompatActivity activity,
            DefaultSmsAppCallback callback) {
        if (isDefaultSmsApp()) {
            callback.onAlreadyDefaultSmsApp();
            return;
        }

        if (!canBecomeDefaultSmsApp()) {
            callback.onNotSupported();
            return;
        }

        // Show confirmation dialog
        new androidx.appcompat.app.AlertDialog.Builder(activity)
                .setTitle("Default SMS App")
                .setMessage(
                        "To provide the best SMS experience, this app needs to be set as your default SMS app. This will allow us to:\n\n"
                                +
                                "- Send and receive SMS messages\n" +
                                "- Sync messages with your device\n" +
                                "- Provide delivery reports\n" +
                                "- Manage your inbox efficiently\n\n" +
                                "You can change this back anytime in your device settings.")
                .setPositiveButton("Make Default", (dialog, which) -> {
                    Intent intent = createDefaultSmsAppIntent();
                    if (intent != null) {
                        callback.onDefaultSmsAppIntentReady(intent);
                    } else {
                        callback.onError("Failed to create default SMS app intent");
                    }
                })
                .setNegativeButton("Maybe Later", (dialog, which) -> {
                    callback.onUserDeclined();
                })
                .setNeutralButton("Learn More", (dialog, which) -> {
                    callback.onShowMoreInfo();
                })
                .show();
    }

    /**
     * Create ActivityResultLauncher for default SMS app request
     */
    public ActivityResultLauncher<Intent> createDefaultSmsLauncher(
            AppCompatActivity activity,
            DefaultSmsAppCallback callback) {
        return activity.registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK) {
                        if (isDefaultSmsApp()) {
                            callback.onDefaultSmsAppSuccess();
                        } else {
                            callback.onDefaultSmsAppFailed();
                        }
                    } else {
                        callback.onDefaultSmsAppCancelled();
                    }
                });
    }

    /**
     * Get status message for current default SMS app state
     */
    public String getStatusMessage() {
        if (isDefaultSmsApp()) {
            return "This app is your default SMS app";
        }
        if (!canBecomeDefaultSmsApp()) {
            return "Default SMS app not supported on this device";
        }
        return "Set as default SMS app for full functionality";
    }

    /**
     * Enable automatic sync when becoming default SMS app
     */
    public Completable enableDefaultSmsFeatures() {
        return Completable.fromAction(() -> {
            if (isDefaultSmsApp()) {
                Log.d(TAG, "Enabling default SMS app features");

                // Enable real-time sync
                // This would be called after successfully becoming default SMS app

                Log.d(TAG, "Default SMS app features enabled");
            } else {
                throw new IllegalStateException("App is not the default SMS app");
            }
        })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread());
    }

    /**
     * Check if we should prompt user to become default SMS app
     */
    public boolean shouldPromptForDefaultSmsApp() {
        return !isDefaultSmsApp() && canBecomeDefaultSmsApp();
    }

    /**
     * Callback interface for default SMS app operations
     */
    public interface DefaultSmsAppCallback {
        void onAlreadyDefaultSmsApp();

        void onDefaultSmsAppIntentReady(Intent intent);

        void onDefaultSmsAppSuccess();

        void onDefaultSmsAppFailed();

        void onDefaultSmsAppCancelled();

        void onPermissionsRequired(String[] missingPermissions);

        void onNotSupported();

        void onUserDeclined();

        void onShowMoreInfo();

        void onError(String message);
    }

    /**
     * Helper class to manage default SMS app state in UI
     */
    public static class DefaultSmsAppUiHelper {

        public static void updateDefaultSmsAppStatus(android.widget.TextView statusView,
                DefaultSmsAppManager manager) {
            if (statusView != null && manager != null) {
                statusView.setText(manager.getStatusMessage());

                // Update appearance based on status
                if (manager.isDefaultSmsApp()) {
                    statusView.setTextColor(android.graphics.Color.GREEN);
                } else if (manager.canBecomeDefaultSmsApp()) {
                    statusView.setTextColor(android.graphics.Color.parseColor("#FF9800")); // Orange
                } else {
                    statusView.setTextColor(android.graphics.Color.RED);
                }
            }
        }

        public static void setupDefaultSmsButton(android.widget.Button button,
                DefaultSmsAppManager manager,
                DefaultSmsAppManager.DefaultSmsAppCallback callback) {
            if (button != null && manager != null) {
                if (manager.isDefaultSmsApp()) {
                    button.setText("Default SMS App");
                    button.setEnabled(false);
                } else if (manager.canBecomeDefaultSmsApp()) {
                    button.setText("Set as Default SMS App");
                    button.setEnabled(true);
                    button.setOnClickListener(v -> {
                        Context context = button.getContext();
                        if (context instanceof AppCompatActivity) {
                            manager.requestDefaultSmsAppStatus((AppCompatActivity) context, callback);
                        }
                    });
                } else {
                    button.setText("Default SMS App Not Available");
                    button.setEnabled(false);
                }
            }
        }
    }
}
