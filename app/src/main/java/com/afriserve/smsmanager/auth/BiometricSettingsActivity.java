package com.afriserve.smsmanager.auth;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.afriserve.smsmanager.R;
import com.afriserve.smsmanager.utils.ToastUtils;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Activity for managing biometric authentication settings
 */
public class BiometricSettingsActivity extends AppCompatActivity {
    private BiometricAuthManager biometricManager;
    private Switch switchBiometric;
    private TextView textStatus;
    private Button btnTestAuth, btnSetupFingerprint;
    
    private ExecutorService executor;
    private ToastUtils toastUtils;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_biometric_settings);
        
        // Initialize components
        biometricManager = BiometricAuthManager.getInstance(this);
        executor = Executors.newSingleThreadExecutor();
        toastUtils = ToastUtils.getInstance(this);
        
        // Setup UI
        setupUI();
        updateUI();
    }

    private void setupUI() {
        switchBiometric = findViewById(R.id.switchBiometric);
        textStatus = findViewById(R.id.textStatus);
        btnTestAuth = findViewById(R.id.btnTestAuth);
        btnSetupFingerprint = findViewById(R.id.btnSetupFingerprint);
        
        switchBiometric.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                enableBiometric();
            } else {
                biometricManager.disableBiometric();
                updateUI();
            }
        });
        
        btnTestAuth.setOnClickListener(v -> testBiometricAuth());
        btnSetupFingerprint.setOnClickListener(v -> openFingerprintSettings());
    }

    private void enableBiometric() {
        executor.execute(() -> {
            try {
                biometricManager.enableBiometric(this, new com.afriserve.smsmanager.auth.BiometricAuthCallback() {
                    @Override
                    public void onSuccess() {
                        runOnUiThread(() -> {
                            updateUI();
                            toastUtils.showSuccess("Biometric authentication enabled");
                        });
                    }

                    @Override
                    public void onError(String error) {
                        runOnUiThread(() -> {
                            switchBiometric.setChecked(false);
                            toastUtils.showError("Failed to enable biometric: " + error);
                        });
                    }

                    @Override
                    public void onCancel() {
                        runOnUiThread(() -> {
                            switchBiometric.setChecked(false);
                            toastUtils.showInfo("Biometric setup cancelled");
                        });
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    switchBiometric.setChecked(false);
                    toastUtils.showError("Error enabling biometric: " + e.getMessage());
                });
            }
        });
    }

    private void testBiometricAuth() {
        executor.execute(() -> {
            try {
                biometricManager.authenticate(this, new com.afriserve.smsmanager.auth.BiometricAuthCallback() {
                    @Override
                    public void onSuccess() {
                        runOnUiThread(() -> 
                            toastUtils.showSuccess("Authentication successful!"));
                    }

                    @Override
                    public void onError(String error) {
                        runOnUiThread(() -> 
                            toastUtils.showError("Authentication failed: " + error));
                    }

                    @Override
                    public void onCancel() {
                        runOnUiThread(() -> 
                            toastUtils.showInfo("Authentication cancelled"));
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> 
                    toastUtils.showError("Error during authentication: " + e.getMessage()));
            }
        });
    }

    private void openFingerprintSettings() {
        try {
            startActivity(new Intent("android.settings.SECURITY_SETTINGS"));
        } catch (Exception e) {
            try {
                startActivity(new Intent("android.settings.SETTINGS"));
            } catch (Exception ex) {
                toastUtils.showError("Cannot open settings");
            }
        }
    }

    private void updateUI() {
        BiometricStatus status = biometricManager.isBiometricAvailable();
        boolean isEnabled = biometricManager.isBiometricEnabled();
        
        // Enable/disable switch based on availability
        switchBiometric.setEnabled(status == BiometricStatus.AVAILABLE);
        switchBiometric.setChecked(isEnabled);
        textStatus.setText(biometricManager.getStatusMessage());
        
        // Update button visibility based on status
        switch (status) {
            case AVAILABLE:
                btnTestAuth.setVisibility(isEnabled ? View.VISIBLE : View.GONE);
                btnSetupFingerprint.setVisibility(View.GONE);
                break;
            case NOT_ENROLLED:
                btnTestAuth.setVisibility(View.GONE);
                btnSetupFingerprint.setVisibility(View.VISIBLE);
                btnSetupFingerprint.setText("Setup Fingerprint");
                break;
            case NO_HARDWARE:
            case HW_UNAVAILABLE:
                btnTestAuth.setVisibility(View.GONE);
                btnSetupFingerprint.setVisibility(View.GONE);
                break;
            default:
                btnTestAuth.setVisibility(View.GONE);
                btnSetupFingerprint.setVisibility(View.VISIBLE);
                btnSetupFingerprint.setText("Open Security Settings");
                break;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (executor != null) {
            executor.shutdown();
        }
    }

    // onClick handler for the finish button in layout
    public void finish(View view) {
        finish();
    }

    // Interface for biometric callbacks
    public interface BiometricAuthCallback {
        void onSuccess();
        void onError(String error);
        void onCancel();
    }
}
