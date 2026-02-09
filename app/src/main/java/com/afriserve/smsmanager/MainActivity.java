package com.afriserve.smsmanager;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Telephony;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.activity.OnBackPressedCallback;
import androidx.navigation.NavController;
import androidx.navigation.NavOptions;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.NavigationUI;

import com.google.android.material.bottomnavigation.BottomNavigationView;

import dagger.hilt.android.AndroidEntryPoint;

/**
 * Modern MainActivity with permission handling and navigation
 */
@AndroidEntryPoint
public class MainActivity extends AppCompatActivity {
    private static final int PERMISSION_REQUEST_CODE = 1001;
    private static final int DEFAULT_SMS_REQUEST_CODE = 101;
    private static final int CALL_PERMISSION_REQUEST_CODE = 1002;
    
    private NavController navController;
    private String pendingCallNumber;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d("BulkSmsStartup", "MainActivity onCreate start: " + System.currentTimeMillis());
        applyThemePreference();
        Log.d("BulkSmsStartup", "Theme applied: " + System.currentTimeMillis());
        setContentView(R.layout.activity_main);
        
        setupNavigation();
        Log.d("BulkSmsStartup", "Navigation setup complete: " + System.currentTimeMillis());
        setupBackNavigation();
        checkAndRequestPermissions();
        Log.d("BulkSmsStartup", "Permissions check initiated: " + System.currentTimeMillis());
        startLocalServer();
        Log.d("BulkSmsStartup", "MainActivity onCreate end: " + System.currentTimeMillis());
    }

    private void setupNavigation() {
        // Programmatically create NavHostFragment to ensure it's created after Hilt sets the FragmentFactory
        NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager().findFragmentByTag("NavHost");
        if (navHostFragment == null) {
            navHostFragment = NavHostFragment.create(R.xml.navigation);
            getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.nav_host_fragment_container, navHostFragment, "NavHost")
                .setPrimaryNavigationFragment(navHostFragment)
                .commitNow();
        }
        // Ensure child fragments use the Activity's FragmentFactory (Hilt)
        navHostFragment.getChildFragmentManager().setFragmentFactory(getSupportFragmentManager().getFragmentFactory());
        navController = navHostFragment.getNavController();
        BottomNavigationView bottomNav = findViewById(R.id.bottom_navigation);
        NavigationUI.setupWithNavController(bottomNav, navController);
    }

    private void setupBackNavigation() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (navController == null || navController.getCurrentDestination() == null) {
                    finish();
                    return;
                }

                int currentId = navController.getCurrentDestination().getId();
                if (currentId == R.id.nav_dashboard) {
                    finish();
                    return;
                }

                if (isTopLevelDestination(currentId)) {
                    boolean popped = navController.popBackStack(R.id.nav_dashboard, false);
                    if (!popped) {
                        NavOptions options = new NavOptions.Builder()
                                .setLaunchSingleTop(true)
                                .setPopUpTo(R.id.nav_dashboard, false)
                                .build();
                        navController.navigate(R.id.nav_dashboard, null, options);
                    }
                    return;
                }

                if (!navController.popBackStack()) {
                    finish();
                }
            }
        });
    }

    private boolean isTopLevelDestination(int destinationId) {
        return destinationId == R.id.nav_dashboard
                || destinationId == R.id.nav_inbox
                || destinationId == R.id.nav_send
                || destinationId == R.id.nav_bulk
                || destinationId == R.id.nav_settings;
    }

    private void checkAndRequestPermissions() {
        if (!isDefaultSmsApp()) {
            requestDefaultSmsApp();
            return;
        }

        String[] requiredPermissions = {
                Manifest.permission.SEND_SMS,
                Manifest.permission.READ_SMS,
                Manifest.permission.RECEIVE_SMS,
                Manifest.permission.READ_PHONE_STATE
        };

        // Add notification permission for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            String[] updatedPermissions = new String[requiredPermissions.length + 1];
            System.arraycopy(requiredPermissions, 0, updatedPermissions, 0, requiredPermissions.length);
            updatedPermissions[requiredPermissions.length] = Manifest.permission.POST_NOTIFICATIONS;
            requiredPermissions = updatedPermissions;
        }

        boolean allGranted = true;
        for (String permission : requiredPermissions) {
            if (ContextCompat.checkSelfPermission(this, permission) 
                != PackageManager.PERMISSION_GRANTED) {
                allGranted = false;
                break;
            }
        }

        if (!allGranted) {
            ActivityCompat.requestPermissions(this, requiredPermissions, PERMISSION_REQUEST_CODE);
            Log.d("BulkSmsStartup", "Requested runtime permissions: " + System.currentTimeMillis());
        } else {
            Log.d("BulkSmsStartup", "All permissions already granted: " + System.currentTimeMillis());
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, 
                                         @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        
        if (requestCode == PERMISSION_REQUEST_CODE) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            
            if (allGranted) {
                Toast.makeText(this, "All permissions granted", Toast.LENGTH_SHORT).show();
                Log.d("BulkSmsStartup", "Permissions granted callback: " + System.currentTimeMillis());
            } else {
                Log.d("BulkSmsStartup", "Permissions denied callback: " + System.currentTimeMillis());
            }
        } else if (requestCode == CALL_PERMISSION_REQUEST_CODE) {
            String number = pendingCallNumber;
            pendingCallNumber = null;
            if (number == null) return;
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startDirectCall(number);
            } else {
                Toast.makeText(this, "Call permission not granted", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private boolean isDefaultSmsApp() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            android.app.role.RoleManager roleManager = getSystemService(android.app.role.RoleManager.class);
            return roleManager != null && roleManager.isRoleHeld(android.app.role.RoleManager.ROLE_SMS);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            String defaultSmsApp = Telephony.Sms.getDefaultSmsPackage(this);
            String myPackageName = getPackageName();
            return myPackageName.equals(defaultSmsApp);
        }
        return false;
    }

    private void requestDefaultSmsApp() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Use RoleManager for Android 10+
            android.app.role.RoleManager roleManager = getSystemService(android.app.role.RoleManager.class);
            if (roleManager != null) {
                if (!roleManager.isRoleHeld(android.app.role.RoleManager.ROLE_SMS)) {
                    Intent intent = roleManager.createRequestRoleIntent(android.app.role.RoleManager.ROLE_SMS);
                    Log.d("BulkSmsStartup", "Requesting ROLE_SMS via RoleManager: " + System.currentTimeMillis());
                    startActivityForResult(intent, DEFAULT_SMS_REQUEST_CODE);
                } else {
                    Log.d("BulkSmsStartup", "Already default SMS app: " + System.currentTimeMillis());
                }
            } else {
                Log.d("BulkSmsStartup", "RoleManager not available: " + System.currentTimeMillis());
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            // Pre-Android 10: Use legacy method (Android 4.4+)
            String defaultSmsApp = Telephony.Sms.getDefaultSmsPackage(this);
            String myPackageName = getPackageName();
            
            if (!myPackageName.equals(defaultSmsApp)) {
                Log.d("BulkSmsStartup", "Requesting change default SMS app intent: " + System.currentTimeMillis());
                Intent intent = new Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT);
                intent.putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, myPackageName);
                startActivityForResult(intent, DEFAULT_SMS_REQUEST_CODE);
            } else {
                Log.d("BulkSmsStartup", "Already default SMS app: " + System.currentTimeMillis());
            }
        } else {
            Log.d("BulkSmsStartup", "Default SMS app not supported on this Android version: " + System.currentTimeMillis());
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == DEFAULT_SMS_REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                Toast.makeText(this, "Set as default SMS app", Toast.LENGTH_SHORT).show();
                Log.d("BulkSmsStartup", "Successfully set as default SMS app: " + System.currentTimeMillis());
                checkAndRequestPermissions();
            } else {
                Toast.makeText(this, "Default SMS app request cancelled", Toast.LENGTH_SHORT).show();
                Log.d("BulkSmsStartup", "Default SMS app request cancelled: " + System.currentTimeMillis());
            }
        }
    }

    public void sendSms(String phoneNumber, String message) {
        if (hasSmsPermissions()) {
            try {
                // Implementation for sending SMS
                // This would typically be in a separate service
            } catch (Exception e) {
                Log.e("MainActivity", "Failed to send SMS", e);
                Toast.makeText(this, "Failed to send SMS: " + e.getMessage(), 
                             Toast.LENGTH_LONG).show();
            }
        } else {
            Toast.makeText(this, "SMS permissions not granted", Toast.LENGTH_SHORT).show();
            checkAndRequestPermissions();
        }
    }

    public void makeCall(String phoneNumber) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) 
            == PackageManager.PERMISSION_GRANTED) {
            startDirectCall(phoneNumber);
        } else {
            pendingCallNumber = phoneNumber;
            ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.CALL_PHONE},
                CALL_PERMISSION_REQUEST_CODE);
        }
    }

    private void startDirectCall(String phoneNumber) {
        try {
            startActivity(new Intent(Intent.ACTION_CALL, Uri.parse("tel:" + phoneNumber)));
        } catch (Exception e) {
            Log.e("MainActivity", "Failed to initiate call", e);
            Toast.makeText(this, "Failed to make call: " + e.getMessage(),
                         Toast.LENGTH_LONG).show();
        }
    }

    private boolean hasSmsPermissions() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) 
               == PackageManager.PERMISSION_GRANTED &&
               ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS) 
               == PackageManager.PERMISSION_GRANTED;
    }

    private void startLocalServer() {
        // Implementation for local server if needed
        // This would typically be in a separate service
    }

    private void applyThemePreference() {
        SharedPreferences prefs = getSharedPreferences("settings", Context.MODE_PRIVATE);
        String themeMode = prefs.getString("theme_mode", AppConfig.Defaults.THEME_MODE);
        
        int nightMode;
        if ("light".equals(themeMode)) {
            nightMode = AppCompatDelegate.MODE_NIGHT_NO;
        } else if ("dark".equals(themeMode)) {
            nightMode = AppCompatDelegate.MODE_NIGHT_YES;
        } else {
            nightMode = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
        }
        
        AppCompatDelegate.setDefaultNightMode(nightMode);
    }
}
