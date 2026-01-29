package com.bulksms.smsmanager;

import android.Manifest;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.provider.Telephony;
import android.view.LayoutInflater;
import android.view.View;
import androidx.annotation.RequiresPermission;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.bulksms.smsmanager.auth.BiometricAuthManager;
import com.bulksms.smsmanager.auth.BiometricStatus;
import com.bulksms.smsmanager.billing.SubscriptionManager;
import com.bulksms.smsmanager.billing.SubscriptionState;
import com.bulksms.smsmanager.billing.SubscriptionStatus;
import com.bulksms.smsmanager.auth.BiometricSettingsActivity;
import com.bulksms.smsmanager.ui.billing.BillingActivity;
import com.bulksms.smsmanager.ui.billing.BillingStatusDialog;
import com.bulksms.smsmanager.utils.ToastUtils;
import com.bulksms.smsmanager.utils.PermissionManager;
import com.bulksms.smsmanager.utils.HapticsUtils;

/**
 * Modern Settings Fragment with all settings consolidated
 */
public class SettingsFragment extends Fragment {
    private CardView themeCard, notificationsCard, defaultSmsCard, clearCacheCard, 
                     aboutCard, billingCard, biometricCard;
    private Switch switchNotifications;
    private TextView txtCurrentTheme, txtDefaultSmsStatus, txtSubscriptionStatus, 
                     txtBillingIcon, txtBiometricStatus;
    
    private SharedPreferences prefs;
    private boolean isDefaultSms;
    private String appVersion = "1.0.0";
    
    // Managers (simplified for pure Java implementation)
    private ToastUtils toastUtils;
    private HapticsUtils hapticsUtils;
    private PermissionManager permissionManager;
    private SubscriptionManager subscriptionManager;
    private BiometricAuthManager biometricManager;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, 
                           @Nullable ViewGroup container, 
                           @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_settings, container, false);
    }

    @RequiresPermission(Manifest.permission.VIBRATE)
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        // Initialize managers
        initializeManagers();
        
        // Initialize preferences
        prefs = requireContext().getSharedPreferences("settings", Context.MODE_PRIVATE);
        
        // Initialize views
        initializeViews(view);
        
        // Setup listeners
        setupListeners();
        
        // Load settings
        loadSettings();
        
        // Update statuses
        checkDefaultSmsStatus();
        updateBillingStatus();
        updateBiometricStatus();
    }

    private void initializeManagers() {
        Context context = requireContext();
        toastUtils = ToastUtils.getInstance(context);
        hapticsUtils = HapticsUtils.getInstance(context);
        permissionManager = PermissionManager.getInstance(context);
        subscriptionManager = SubscriptionManager.getInstance(context);
        biometricManager = BiometricAuthManager.getInstance(context);
    }

    private void initializeViews(View view) {
        themeCard = view.findViewById(R.id.themeCard);
        notificationsCard = view.findViewById(R.id.notificationsCard);
        switchNotifications = view.findViewById(R.id.switchNotifications);
        defaultSmsCard = view.findViewById(R.id.defaultSmsCard);
        txtDefaultSmsStatus = view.findViewById(R.id.txtDefaultSmsStatus);
        clearCacheCard = view.findViewById(R.id.clearCacheCard);
        aboutCard = view.findViewById(R.id.aboutCard);
        txtCurrentTheme = view.findViewById(R.id.txtCurrentTheme);
        billingCard = view.findViewById(R.id.billingCard);
        txtSubscriptionStatus = view.findViewById(R.id.txtSubscriptionStatus);
        txtBillingIcon = view.findViewById(R.id.txtBillingIcon);
        biometricCard = view.findViewById(R.id.biometricCard);
        txtBiometricStatus = view.findViewById(R.id.txtBiometricStatus);
    }

    @RequiresPermission(Manifest.permission.VIBRATE)
    private void setupListeners() {
        themeCard.setOnClickListener(v -> showThemeDialog());
        defaultSmsCard.setOnClickListener(v -> {
            if (!isDefaultSms) {
                makeDefaultSmsApp();
            } else {
                toastUtils.showInfo("App is already the default SMS handler");
            }
        });
        clearCacheCard.setOnClickListener(v -> showResetConfirmation());
        aboutCard.setOnClickListener(v -> showAboutDialog());
        billingCard.setOnClickListener(v -> showBillingStatusDialog());
        billingCard.setOnLongClickListener(v -> {
            startActivity(new Intent(requireContext(), BillingActivity.class));
            toastUtils.showInfo("Opening billing settings...");
            return true;
        });
        biometricCard.setOnClickListener(v -> 
            startActivity(new Intent(requireContext(), BiometricSettingsActivity.class)));
        
        switchNotifications.setOnCheckedChangeListener((buttonView, isChecked) -> {
            saveBooleanSetting("notifications_enabled", isChecked);
            if (isChecked) {
                toastUtils.showSuccess("Notifications enabled");
            }
            hapticsUtils.trigger(HapticsUtils.HapticType.SELECTION);
        });
    }

    private void loadSettings() {
        String themeMode = prefs.getString("theme_mode", AppConfig.Defaults.THEME_MODE);
        boolean notificationsEnabled = prefs.getBoolean("notifications_enabled", true);
        
        String themeText;
        if ("light".equals(themeMode)) {
            themeText = "Light";
        } else if ("dark".equals(themeMode)) {
            themeText = "Dark";
        } else {
            themeText = "System";
        }
        
        txtCurrentTheme.setText(themeText);
        switchNotifications.setChecked(notificationsEnabled);
    }

    @RequiresPermission(Manifest.permission.VIBRATE)
    private void showThemeDialog() {
        String[] themes = {"System", "Light", "Dark"};
        String currentTheme = prefs.getString("theme_mode", AppConfig.Defaults.THEME_MODE);
        
        int selectedIndex;
        if ("light".equals(currentTheme)) {
            selectedIndex = 1;
        } else if ("dark".equals(currentTheme)) {
            selectedIndex = 2;
        } else {
            selectedIndex = 0;
        }
        
        new AlertDialog.Builder(requireContext())
                .setTitle("Select Theme")
                .setSingleChoiceItems(themes, selectedIndex, (dialog, which) -> {
                    String selectedTheme = which == 1 ? "light" : 
                                          which == 2 ? "dark" : AppConfig.Defaults.THEME_MODE;
                    setThemeMode(selectedTheme);
                    dialog.dismiss();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    @RequiresPermission(Manifest.permission.VIBRATE)
    private void setThemeMode(String themeMode) {
        saveStringSetting("theme_mode", themeMode);
        
        String themeText;
        if ("light".equals(themeMode)) {
            themeText = "Light";
        } else if ("dark".equals(themeMode)) {
            themeText = "Dark";
        } else {
            themeText = "System";
        }
        
        txtCurrentTheme.setText(themeText);
        toastUtils.showSuccess("Theme changed to " + themeText);
        hapticsUtils.trigger(HapticsUtils.HapticType.SUCCESS);
    }

    private void saveStringSetting(String key, String value) {
        prefs.edit().putString(key, value).apply();
    }

    private void saveBooleanSetting(String key, boolean value) {
        prefs.edit().putBoolean(key, value).apply();
    }

    private void checkDefaultSmsStatus() {
        String packageName = requireContext().getPackageName();
        String defaultSmsApp = Telephony.Sms.getDefaultSmsPackage(requireContext());
        isDefaultSms = packageName.equals(defaultSmsApp);
        
        txtDefaultSmsStatus.setText(isDefaultSms ? 
            "Current default app" : "Tap to set as default");
        
        int colorRes = isDefaultSms ? 
            android.R.color.holo_green_dark : android.R.color.holo_orange_dark;
        txtDefaultSmsStatus.setTextColor(ContextCompat.getColor(requireContext(), colorRes));
    }

    private void makeDefaultSmsApp() {
        try {
            Intent intent = new Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT);
            intent.putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, 
                          requireContext().getPackageName());
            startActivity(intent);
        } catch (Exception e) {
            toastUtils.showError("Cannot request default SMS role: " + e.getMessage());
            try {
                startActivity(new Intent("android.settings.MANAGE_DEFAULT_APPS_SETTINGS"));
            } catch (Exception ex) {
                toastUtils.showError("Cannot open settings");
            }
        }
    }

    @RequiresPermission(Manifest.permission.VIBRATE)
    private void showResetConfirmation() {
        new AlertDialog.Builder(requireContext())
                .setTitle("Clear Cache")
                .setMessage("Clear app cache and restore settings to default?")
                .setPositiveButton("Clear", (dialog, which) -> resetSettings())
                .setNegativeButton("Cancel", null)
                .show();
    }

    @RequiresPermission(Manifest.permission.VIBRATE)
    private void resetSettings() {
        prefs.edit().clear().apply();
        loadSettings();
        toastUtils.showSuccess("Settings reset");
        hapticsUtils.trigger(HapticsUtils.HapticType.SUCCESS);
    }

    private void showAboutDialog() {
        new AlertDialog.Builder(requireContext())
                .setTitle("About SMS Manager")
                .setMessage("Version " + appVersion + "\n\nA powerful bulk SMS tool for Kenya.")
                .setPositiveButton("OK", null)
                .show();
    }

    private void showBillingStatusDialog() {
        new BillingStatusDialog(requireContext()).show();
    }

    private void updateBillingStatus() {
        try {
            SubscriptionState subscriptionState = subscriptionManager.getSubscriptionState();
            SubscriptionStatus status = subscriptionState.getStatus();
            
            String statusText;
            int colorRes;
            String icon;
            
            switch (status) {
                case ACTIVE:
                    String planName = subscriptionManager.getPlanName(
                        subscriptionState.getSubscription().getPlanId());
                    statusText = "✓ " + (planName != null ? planName : "Premium") + " Active";
                    colorRes = android.R.color.holo_green_dark;
                    icon = "💎";
                    break;
                case EXPIRING:
                    statusText = "⚠ Expiring in " + subscriptionState.getDaysRemaining() + " days";
                    colorRes = android.R.color.holo_orange_light;
                    icon = "⏰";
                    break;
                case GRACE:
                    statusText = "⚠ Grace period: " + 
                               subscriptionState.getGraceDaysRemaining() + " days left";
                    colorRes = android.R.color.holo_orange_dark;
                    icon = "🔒";
                    break;
                case EXPIRED:
                    statusText = "✗ Subscription Expired";
                    colorRes = android.R.color.holo_red_dark;
                    icon = "❌";
                    break;
                case NONE:
                default:
                    if (subscriptionManager.isInTrial()) {
                        statusText = "🎯 Trial: " + subscriptionManager.getTrialDaysRemaining() + 
                                   " days left";
                        colorRes = android.R.color.holo_blue_bright;
                        icon = "🎯";
                    } else {
                        statusText = "🆓 Free Plan";
                        colorRes = android.R.color.darker_gray;
                        icon = "🆓";
                    }
                    break;
            }
            
            txtSubscriptionStatus.setText(statusText);
            txtSubscriptionStatus.setTextColor(ContextCompat.getColor(requireContext(), colorRes));
            txtBillingIcon.setText(icon);
            
        } catch (Exception e) {
            txtSubscriptionStatus.setText("⚠ Status unavailable");
            txtSubscriptionStatus.setTextColor(ContextCompat.getColor(requireContext(), 
                                                                     android.R.color.darker_gray));
            txtBillingIcon.setText("⚠️");
        }
    }

    private void updateBiometricStatus() {
        try {
            BiometricStatus status = biometricManager.isBiometricAvailable();
            boolean isEnabled = biometricManager.isBiometricEnabled();
            
            String statusText;
            int colorRes;
            
            if (status != BiometricStatus.AVAILABLE) {
                statusText = "Not available";
                colorRes = android.R.color.darker_gray;
            } else if (isEnabled) {
                statusText = "✓ Enabled";
                colorRes = android.R.color.holo_green_dark;
            } else {
                statusText = "Setup required";
                colorRes = android.R.color.holo_blue_bright;
            }
            
            txtBiometricStatus.setText(statusText);
            txtBiometricStatus.setTextColor(ContextCompat.getColor(requireContext(), colorRes));
            
        } catch (Exception e) {
            txtBiometricStatus.setText("⚠ Status unavailable");
            txtBiometricStatus.setTextColor(ContextCompat.getColor(requireContext(), 
                                                                 android.R.color.darker_gray));
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        checkDefaultSmsStatus();
        updateBillingStatus();
        updateBiometricStatus();
    }
}
