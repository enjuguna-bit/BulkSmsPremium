package com.afriserve.smsmanager;

import android.Manifest;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.net.Uri;
import android.provider.Telephony;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import androidx.annotation.RequiresPermission;
import android.view.ViewGroup;
import android.text.TextUtils;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.Button;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.NavOptions;

import com.afriserve.smsmanager.auth.BiometricAuthManager;
import com.afriserve.smsmanager.auth.BiometricStatus;
import com.afriserve.smsmanager.auth.BiometricSettingsActivity;
import com.afriserve.smsmanager.billing.SubscriptionHelper;
import com.afriserve.smsmanager.ui.privacy.PrivacyPolicyActivity;
import com.afriserve.smsmanager.utils.ToastUtils;
import com.afriserve.smsmanager.utils.PermissionManager;
import com.afriserve.smsmanager.utils.HapticsUtils;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

/**
 * Modern Settings Fragment with all settings consolidated
 */
public class SettingsFragment extends Fragment {
    private static final int DEFAULT_SMS_REQUEST_CODE = 1001;
    private static final String SUBSCRIPTION_PREFS = "subscriptions";
    private static final String KEY_LAST_PHONE = "last_phone";
    
    private CardView themeCard, notificationsCard, defaultSmsCard, clearCacheCard, 
                     aboutCard, biometricCard, premiumCard, supportCard, privacyCard;
    private Switch switchNotifications;
    private TextView txtCurrentTheme, txtDefaultSmsStatus, txtBiometricStatus;
    private TextView txtSubscriptionStatus, txtSubscriptionPaidUntil;
    private TextView txtSubscriptionBadge, txtPremiumTitle, txtSubscriptionLastChecked;
    private TextView txtSupportEmail;
    private Button btnUpgradePremium, btnRefreshStatus;
    private TextInputLayout tilSubscriptionPhone;
    private TextInputEditText etSubscriptionPhone;

    private SharedPreferences prefs;
    private boolean isDefaultSms;
    private String appVersion = "1.0.0";
    private final ExecutorService subscriptionExecutor = Executors.newSingleThreadExecutor();
    
    // Managers (simplified for pure Java implementation)
    private ToastUtils toastUtils;
    private HapticsUtils hapticsUtils;
    private PermissionManager permissionManager;
    private com.afriserve.smsmanager.auth.BiometricAuthManager biometricManager;

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
        updateSubscriptionStatus(false);
        setupBackNavigation();
    }
    
    @Override
    public void onResume() {
        super.onResume();
        // Refresh default SMS status when fragment becomes visible
        // This catches cases where user changed default app in system settings
        Log.d("SettingsFragment", "onResume - checking default SMS status");
        checkDefaultSmsStatus();
        updateSubscriptionStatus(false);
    }

    private void openSubscription() {
        savePhoneFromInputIfValid();
        if (SubscriptionHelper.INSTANCE.isPaymentPending(requireContext())) {
            updateSubscriptionStatus(true);
        } else {
            SubscriptionHelper.INSTANCE.launch(requireContext());
        }
    }

    private void updateSubscriptionStatus(boolean forceRefresh) {
        if (txtSubscriptionStatus == null) {
            return;
        }
        applySubscriptionStatus(SubscriptionHelper.INSTANCE.getCachedStatus(requireContext()));
        if (forceRefresh) {
            setRefreshInProgress(true);
        }

        subscriptionExecutor.execute(() -> {
            try {
                SubscriptionHelper.SubscriptionStatus refreshed =
                        SubscriptionHelper.INSTANCE.refreshSubscriptionStatusBlocking(requireContext(), forceRefresh);
                if (isAdded()) {
                    requireActivity().runOnUiThread(() -> {
                        applySubscriptionStatus(refreshed);
                        setRefreshInProgress(false);
                    });
                }
            } catch (Exception e) {
                Log.w("SettingsFragment", "Failed to refresh subscription status", e);
                if (isAdded()) {
                    requireActivity().runOnUiThread(() -> {
                        setRefreshInProgress(false);
                        applySubscriptionStatus(SubscriptionHelper.INSTANCE.getCachedStatus(requireContext()));
                        toastUtils.showError("Unable to reach server. Showing last known status.");
                    });
                }
            }
        });
    }

    private void applySubscriptionStatus(SubscriptionHelper.SubscriptionStatus status) {
        if (status == null || txtSubscriptionStatus == null || btnUpgradePremium == null) {
            return;
        }
        long now = System.currentTimeMillis();
        boolean isActive = status.getPremium()
                && (status.getPaidUntilMillis() == null || status.getPaidUntilMillis() > now);

        if (isActive) {
            String planLabel = formatPlanLabel(status.getPlan());
            if (txtSubscriptionBadge != null) {
                txtSubscriptionBadge.setText("PREMIUM");
            }
            if (txtPremiumTitle != null) {
                txtPremiumTitle.setText("Premium active");
            }
            if (planLabel != null) {
                txtSubscriptionStatus.setText("Plan: " + planLabel);
            } else {
                txtSubscriptionStatus.setText("Premium active");
            }
            btnUpgradePremium.setText("Manage");
            if (shouldShowPaidUntil(status.getPlan()) && txtSubscriptionPaidUntil != null) {
                txtSubscriptionPaidUntil.setVisibility(View.VISIBLE);
                txtSubscriptionPaidUntil.setText(formatPaidUntil(status.getPaidUntilMillis()));
            } else if (txtSubscriptionPaidUntil != null) {
                txtSubscriptionPaidUntil.setVisibility(View.GONE);
            }
            updateLastChecked(status.getLastCheckedMillis());
            return;
        }

        if (SubscriptionHelper.INSTANCE.isPaymentPending(requireContext())) {
            if (txtSubscriptionBadge != null) {
                txtSubscriptionBadge.setText("PENDING");
            }
            if (txtPremiumTitle != null) {
                txtPremiumTitle.setText("Payment processing");
            }
            txtSubscriptionStatus.setText("Payment processing...");
            btnUpgradePremium.setText("Check Status");
            if (txtSubscriptionPaidUntil != null) {
                txtSubscriptionPaidUntil.setVisibility(View.GONE);
            }
            updateLastChecked(status.getLastCheckedMillis());
            return;
        }

        if (txtSubscriptionBadge != null) {
            txtSubscriptionBadge.setText("FREE");
        }
        if (txtPremiumTitle != null) {
            txtPremiumTitle.setText("Free plan");
        }
        txtSubscriptionStatus.setText("Free plan - limited features");
        btnUpgradePremium.setText("Upgrade Now");
        if (txtSubscriptionPaidUntil != null) {
            txtSubscriptionPaidUntil.setVisibility(View.GONE);
        }
        updateLastChecked(status.getLastCheckedMillis());
    }

    private boolean shouldShowPaidUntil(String plan) {
        if (plan == null) return false;
        String normalized = plan.trim().toLowerCase(Locale.getDefault());
        return "daily".equals(normalized) || "weekly".equals(normalized);
    }

    private String formatPlanLabel(String plan) {
        if (plan == null) return null;
        String normalized = plan.trim().toLowerCase(Locale.getDefault());
        switch (normalized) {
            case "daily":
                return "Daily";
            case "weekly":
                return "Weekly";
            case "monthly":
                return "Monthly";
            case "yearly":
                return "Yearly";
            default:
                return null;
        }
    }

    private String formatPaidUntil(Long paidUntilMillis) {
        if (paidUntilMillis == null || paidUntilMillis <= 0L) {
            return "Paid until: --";
        }
        SimpleDateFormat formatter = new SimpleDateFormat("MMM d, yyyy", Locale.getDefault());
        return "Paid until: " + formatter.format(new Date(paidUntilMillis));
    }

    private void updateLastChecked(long lastCheckedMillis) {
        if (txtSubscriptionLastChecked == null) {
            return;
        }
        if (lastCheckedMillis <= 0L) {
            txtSubscriptionLastChecked.setText("Last checked: --");
            return;
        }
        SimpleDateFormat formatter = new SimpleDateFormat("MMM d, yyyy HH:mm", Locale.getDefault());
        txtSubscriptionLastChecked.setText("Last checked: " + formatter.format(new Date(lastCheckedMillis)));
    }

    private void setRefreshInProgress(boolean refreshing) {
        if (btnRefreshStatus != null) {
            btnRefreshStatus.setEnabled(!refreshing);
            btnRefreshStatus.setText(refreshing ? "Refreshing..." : "Refresh Status");
        }
        if (refreshing && txtSubscriptionLastChecked != null) {
            txtSubscriptionLastChecked.setText("Checking server...");
        }
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

        supportCard = view.findViewById(R.id.supportCard);
        txtSupportEmail = view.findViewById(R.id.txtSupportEmail);

        premiumCard = view.findViewById(R.id.premiumCard);
        txtSubscriptionBadge = view.findViewById(R.id.txtSubscriptionBadge);
        txtPremiumTitle = view.findViewById(R.id.txtPremiumTitle);
        txtSubscriptionStatus = view.findViewById(R.id.txtSubscriptionStatus);
        txtSubscriptionPaidUntil = view.findViewById(R.id.txtSubscriptionPaidUntil);
        txtSubscriptionLastChecked = view.findViewById(R.id.txtSubscriptionLastChecked);
        btnUpgradePremium = view.findViewById(R.id.btnUpgradePremium);
        btnRefreshStatus = view.findViewById(R.id.btnRefreshStatus);
        tilSubscriptionPhone = view.findViewById(R.id.tilSubscriptionPhone);
        etSubscriptionPhone = view.findViewById(R.id.etSubscriptionPhone);
        privacyCard = view.findViewById(R.id.privacyCard);
        prefillSubscriptionPhone();
    }

    @RequiresPermission(Manifest.permission.VIBRATE)
    private void setupListeners() {
        if (premiumCard != null) {
            premiumCard.setOnClickListener(v -> openSubscription());
        }
        if (btnUpgradePremium != null) {
            btnUpgradePremium.setOnClickListener(v -> openSubscription());
        }
        if (btnRefreshStatus != null) {
            btnRefreshStatus.setOnClickListener(v -> {
                if (prepareSubscriptionPhoneForRefresh()) {
                    updateSubscriptionStatus(true);
                }
            });
        }

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

        if (supportCard != null) {
            supportCard.setOnClickListener(v -> openSupportEmail());
        }

        if (privacyCard != null) {
            privacyCard.setOnClickListener(v -> openPrivacyPolicy());
        }
        
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

    private void setupBackNavigation() {
        requireActivity().getOnBackPressedDispatcher().addCallback(
            getViewLifecycleOwner(),
            new OnBackPressedCallback(true) {
                @Override
                public void handleOnBackPressed() {
                    try {
                        NavController navController = Navigation.findNavController(
                            requireActivity(),
                            R.id.nav_host_fragment_container
                        );
                        boolean popped = navController.popBackStack(R.id.nav_dashboard, false);
                        if (!popped) {
                            NavOptions options = new NavOptions.Builder()
                                    .setLaunchSingleTop(true)
                                    .setPopUpTo(R.id.nav_dashboard, false)
                                    .build();
                            navController.navigate(R.id.nav_dashboard, null, options);
                        }
                    } catch (Exception e) {
                        requireActivity().finish();
                    }
                }
            }
        );
    }

    private void prefillSubscriptionPhone() {
        if (etSubscriptionPhone == null) {
            return;
        }
        String lastPhone = getSavedSubscriptionPhone();
        if (!TextUtils.isEmpty(lastPhone)) {
            etSubscriptionPhone.setText(lastPhone);
        }
    }

    private String getSavedSubscriptionPhone() {
        Context context = requireContext();
        SharedPreferences prefs = context.getSharedPreferences(SUBSCRIPTION_PREFS, Context.MODE_PRIVATE);
        return prefs.getString(KEY_LAST_PHONE, null);
    }

    private void saveSubscriptionPhone(String phone) {
        Context context = requireContext();
        SharedPreferences prefs = context.getSharedPreferences(SUBSCRIPTION_PREFS, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_LAST_PHONE, phone).apply();
    }

    private boolean prepareSubscriptionPhoneForRefresh() {
        if (etSubscriptionPhone == null) {
            return true;
        }
        String input = etSubscriptionPhone.getText() != null
                ? etSubscriptionPhone.getText().toString().trim()
                : "";

        if (input.isEmpty()) {
            String cached = getSavedSubscriptionPhone();
            if (TextUtils.isEmpty(cached)) {
                if (tilSubscriptionPhone != null) {
                    tilSubscriptionPhone.setError("Phone number is required to check status");
                }
                toastUtils.showInfo("Enter the M-Pesa phone number used to pay.");
                return false;
            }
            return true;
        }

        if (!validatePhoneNumber(input)) {
            return false;
        }

        String formatted = formatPhoneNumber(input);
        saveSubscriptionPhone(formatted);
        if (tilSubscriptionPhone != null) {
            tilSubscriptionPhone.setError(null);
        }
        etSubscriptionPhone.setText(formatted);
        return true;
    }

    private void savePhoneFromInputIfValid() {
        if (etSubscriptionPhone == null) {
            return;
        }
        String input = etSubscriptionPhone.getText() != null
                ? etSubscriptionPhone.getText().toString().trim()
                : "";
        if (input.isEmpty()) {
            return;
        }
        if (!validatePhoneNumber(input)) {
            return;
        }
        String formatted = formatPhoneNumber(input);
        saveSubscriptionPhone(formatted);
    }

    private boolean validatePhoneNumber(String phone) {
        if (tilSubscriptionPhone != null) {
            tilSubscriptionPhone.setError(null);
        }
        if (phone == null || phone.trim().isEmpty()) {
            if (tilSubscriptionPhone != null) {
                tilSubscriptionPhone.setError("Phone number is required");
            }
            return false;
        }

        String cleaned = phone.replaceAll("[^0-9+]", "");
        String digits = cleaned.replace("+", "");

        boolean valid;
        if (digits.startsWith("0")) {
            valid = digits.length() == 10 && (digits.charAt(1) == '7' || digits.charAt(1) == '1');
        } else if (digits.startsWith("254")) {
            valid = digits.length() == 12 && (digits.charAt(3) == '7' || digits.charAt(3) == '1');
        } else if (digits.startsWith("7") || digits.startsWith("1")) {
            valid = digits.length() == 9;
        } else {
            valid = false;
        }

        if (!valid && tilSubscriptionPhone != null) {
            tilSubscriptionPhone.setError("Enter a valid Kenyan phone number");
        }
        return valid;
    }

    private String formatPhoneNumber(String phone) {
        if (phone == null) {
            return "";
        }
        String formatted = phone.replaceAll("[^0-9+]", "");
        formatted = formatted.replace("+", "");

        if (formatted.startsWith("0") && formatted.length() == 10) {
            formatted = "254" + formatted.substring(1);
        } else if (formatted.startsWith("254") && formatted.length() == 12) {
            // Already in correct format
        } else if ((formatted.startsWith("7") || formatted.startsWith("1")) && formatted.length() == 9) {
            formatted = "254" + formatted;
        }
        return formatted;
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

    private void openSupportEmail() {
        try {
            Intent intent = new Intent(Intent.ACTION_SENDTO);
            intent.setData(Uri.parse("mailto:enjuguna794@gmail.com"));
            intent.putExtra(Intent.EXTRA_SUBJECT, "Bulk SMS Manager Support");
            intent.putExtra(Intent.EXTRA_TEXT, "Hi Support,\n\nI need help with:\n\n");
            if (intent.resolveActivity(requireContext().getPackageManager()) != null) {
                startActivity(intent);
            } else {
                toastUtils.showInfo("No email app found. Please email enjuguna794@gmail.com");
            }
        } catch (Exception e) {
            Log.e("SettingsFragment", "Failed to open support email", e);
            toastUtils.showError("Unable to open email app");
        }
    }

    private void openPrivacyPolicy() {
        try {
            String url = getString(R.string.settings_privacy_url);
            if (url == null || url.trim().isEmpty()) {
                toastUtils.showError("Privacy policy URL not set");
                return;
            }
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            if (intent.resolveActivity(requireContext().getPackageManager()) != null) {
                startActivity(intent);
            } else {
                startActivity(new Intent(requireContext(), PrivacyPolicyActivity.class));
            }
        } catch (Exception e) {
            Log.e("SettingsFragment", "Failed to open privacy policy", e);
            try {
                startActivity(new Intent(requireContext(), PrivacyPolicyActivity.class));
            } catch (Exception ignored) {
                toastUtils.showError("Unable to open privacy policy");
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
                statusText = "Enabled";
                colorRes = android.R.color.holo_green_dark;
            } else {
                statusText = "Setup required";
                colorRes = android.R.color.holo_blue_bright;
            }
            
            txtBiometricStatus.setText(statusText);
            txtBiometricStatus.setTextColor(ContextCompat.getColor(requireContext(), colorRes));
            
        } catch (Exception e) {
            txtBiometricStatus.setText("Status unavailable");
            txtBiometricStatus.setTextColor(ContextCompat.getColor(requireContext(), 
                                                                 android.R.color.darker_gray));
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        subscriptionExecutor.shutdown();
    }
}
