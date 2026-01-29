package com.bulksms.smsmanager;

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
import androidx.navigation.NavController;
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
    
    private NavController navController;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d("BulkSmsStartup", "MainActivity onCreate start: " + System.currentTimeMillis());
        applyThemePreference();
        Log.d("BulkSmsStartup", "Theme applied: " + System.currentTimeMillis());
        setContentView(R.layout.activity_main);
        
        setupNavigation();
        Log.d("BulkSmsStartup", "Navigation setup complete: " + System.currentTimeMillis());
        checkAndRequestPermissions();
        Log.d("BulkSmsStartup", "Permissions check initiated: " + System.currentTimeMillis());
        startLocalServer();
        Log.d("BulkSmsStartup", "MainActivity onCreate end: " + System.currentTimeMillis());
    }

    private void setupNavigation() {
        NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager()
                .findFragmentById(R.id.nav_host_fragment);
        if (navHostFragment != null) {
            navController = navHostFragment.getNavController();
            BottomNavigationView bottomNav = findViewById(R.id.bottom_navigation);
            NavigationUI.setupWithNavController(bottomNav, navController);
        }
    }

    private void checkAndRequestPermissions() {
        String[] requiredPermissions = {
                Manifest.permission.SEND_SMS,
                Manifest.permission.READ_SMS,
                Manifest.permission.RECEIVE_SMS,
                Manifest.permission.READ_PHONE_STATE,
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.CALL_PHONE
        };

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
            checkDefaultSmsApp();
            Log.d("BulkSmsStartup", "All permissions already granted; checking default SMS app: " + System.currentTimeMillis());
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
            
            // Always check default SMS app after permission request, regardless of grant status
            // Some permissions might be granted but we still want to be default SMS app
            checkDefaultSmsApp();
            
            if (allGranted) {
                Toast.makeText(this, "All permissions granted", Toast.LENGTH_SHORT).show();
                Log.d("BulkSmsStartup", "Permissions granted callback: " + System.currentTimeMillis());
            } else {
                Toast.makeText(this, "Some permissions denied. Functionality may be limited.", 
                             Toast.LENGTH_LONG).show();
                Log.d("BulkSmsStartup", "Permissions denied callback: " + System.currentTimeMillis());
            }
        }
    }

    private void checkDefaultSmsApp() {
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
            try {
                startActivity(new Intent(Intent.ACTION_CALL, Uri.parse("tel:" + phoneNumber)));
            } catch (Exception e) {
                Log.e("MainActivity", "Failed to initiate call", e);
                Toast.makeText(this, "Failed to make call: " + e.getMessage(), 
                             Toast.LENGTH_LONG).show();
            }
        } else {
            Toast.makeText(this, "Call permission not granted", Toast.LENGTH_SHORT).show();
            checkAndRequestPermissions();
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
