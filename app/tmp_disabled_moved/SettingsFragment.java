package com.bulksms.smsmanager;

import android.Manifest;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.provider.Telephony;
import android.util.Log;
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
import com.bulksms.smsmanager.auth.BiometricSettingsActivity;
import com.bulksms.smsmanager.utils.ToastUtils;
import com.bulksms.smsmanager.utils.PermissionManager;
import com.bulksms.smsmanager.utils.HapticsUtils;

/**
 * Modern Settings Fragment with all settings consolidated
 */
public class SettingsFragment extends Fragment {
    private static final int DEFAULT_SMS_REQUEST_CODE = 1001;
    
    private CardView themeCard, notificationsCard, defaultSmsCard, clearCacheCard, 
                     aboutCard, biometricCard;
    private Switch switchNotifications;
    private TextView txtCurrentTheme, txtDefaultSmsStatus, txtBiometricStatus;

    private SharedPreferences prefs;
    private boolean isDefaultSms;
    private String appVersion = "1.0.0";
    
    // Managers (simplified for pure Java implementation)
    private ToastUtils toastUtils;
    private HapticsUtils hapticsUtils;
    private PermissionManager permissionManager;

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
        updateBiometricStatus();
    }
    
    @Override
    public void onResume() {
        super.onResume();
        // Refresh default SMS status when fragment becomes visible
        // This catches cases where user changed default app in system settings
        Log.d("SettingsFragment", "onResume - checking default SMS status");
        checkDefaultSmsStatus();
    }

    private void initializeManagers() {
        Context context = requireContext();
        toastUtils = ToastUtils.getInstance(context);
        hapticsUtils = HapticsUtils.getInstance(context);
        permissionManager = PermissionManager.getInstance(context);
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
        
        // Check default SMS status whenever settings are loaded
        checkDefaultSmsStatus();
    }
    
    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        
        if (requestCode == DEFAULT_SMS_REQUEST_CODE) {
            Log.d("SettingsFragment", "SMS role request result - requestCode: " + requestCode + ", resultCode: " + resultCode);
            
            // Always recheck status after returning from role request
            checkDefaultSmsStatus();
            
            if (resultCode == android.app.Activity.RESULT_OK) {
                toastUtils.showSuccess("Successfully set as default SMS app");
                Log.d("SettingsFragment", "Successfully set as default SMS app");
            } else {
                if (isDefaultSms) {
                    toastUtils.showInfo("Already set as default SMS app");
                } else {
                    toastUtils.showError("Default SMS app request cancelled or denied");
                }
                Log.d("SettingsFragment", "Default SMS app request cancelled or denied");
            }
        }
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
        boolean isDefaultSms = false;
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Use RoleManager for Android 10+ (most accurate)
            android.app.role.RoleManager roleManager = requireContext().getSystemService(android.app.role.RoleManager.class);
            if (roleManager != null) {
                isDefaultSms = roleManager.isRoleHeld(android.app.role.RoleManager.ROLE_SMS);
                Log.d("SettingsFragment", "Using RoleManager: isDefaultSms = " + isDefaultSms);
            } else {
                Log.w("SettingsFragment", "RoleManager not available, falling back to legacy method");
                isDefaultSms = checkLegacyDefaultSms(packageName);
            }
        } else {
            // Use legacy method for pre-Android 10
            isDefaultSms = checkLegacyDefaultSms(packageName);
        }
        
        this.isDefaultSms = isDefaultSms;
        
        // Update UI
        txtDefaultSmsStatus.setText(isDefaultSms ? 
            "Current default SMS app" : "Tap to set as default");
        
        int colorRes = isDefaultSms ? 
            android.R.color.holo_green_dark : android.R.color.holo_orange_dark;
        txtDefaultSmsStatus.setTextColor(ContextCompat.getColor(requireContext(), colorRes));
        
        // Additional status info
        String additionalInfo = "";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            additionalInfo = " (RoleManager)";
        } else {
            additionalInfo = " (Legacy check)";
        }
        
        Log.d("SettingsFragment", "Default SMS status: " + isDefaultSms + additionalInfo);
    }
    
    private boolean checkLegacyDefaultSms(String packageName) {
        try {
            String defaultSmsApp = Telephony.Sms.getDefaultSmsPackage(requireContext());
            boolean isDefault = packageName.equals(defaultSmsApp);
            Log.d("SettingsFragment", "Legacy check - defaultSmsApp: " + defaultSmsApp + 
                  ", our package: " + packageName + ", isDefault: " + isDefault);
            return isDefault;
        } catch (Exception e) {
            Log.e("SettingsFragment", "Error checking legacy default SMS", e);
            return false;
        }
    }

    private void makeDefaultSmsApp() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Use RoleManager for Android 10+ (preferred method)
                android.app.role.RoleManager roleManager = requireContext().getSystemService(android.app.role.RoleManager.class);
                if (roleManager != null) {
                    Intent intent = roleManager.createRequestRoleIntent(android.app.role.RoleManager.ROLE_SMS);
                    Log.d("SettingsFragment", "Requesting SMS role via RoleManager");
                    startActivityForResult(intent, DEFAULT_SMS_REQUEST_CODE);
                } else {
                    Log.w("SettingsFragment", "RoleManager not available, using legacy method");
                    requestDefaultSmsLegacy();
                }
            } else {
                // Use legacy method for pre-Android 10
                requestDefaultSmsLegacy();
            }
        } catch (Exception e) {
            Log.e("SettingsFragment", "Error requesting default SMS role", e);
            toastUtils.showError("Cannot request default SMS role: " + e.getMessage());
            openDefaultAppSettings();
        }
    }
    
    private void requestDefaultSmsLegacy() {
        try {
            Intent intent = new Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT);
            intent.putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, 
                          requireContext().getPackageName());
            Log.d("SettingsFragment", "Requesting SMS role via legacy method");
            startActivityForResult(intent, DEFAULT_SMS_REQUEST_CODE);
        } catch (Exception e) {
            Log.e("SettingsFragment", "Error with legacy SMS role request", e);
            openDefaultAppSettings();
        }
    }
    
    private void openDefaultAppSettings() {
        try {
            // Try to open default app settings - use intent action directly
            startActivity(new Intent("android.settings.MANAGE_DEFAULT_APPS_SETTINGS"));
        } catch (Exception ex) {
            Log.e("SettingsFragment", "Cannot open app settings", ex);
            toastUtils.showError("Cannot open settings. Please manually set as default SMS app.");
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
}
