package com.bulksms.smsmanager.auth;

/**
 * Callback interface for biometric authentication results
 */
public interface BiometricAuthCallback {
    /**
     * Called when biometric authentication succeeds
     */
    void onSuccess();
    
    /**
     * Called when biometric authentication fails with an error
     * @param error The error message describing the failure
     */
    void onError(String error);
    
    /**
     * Called when user cancels the biometric authentication
     */
    void onCancel();
}
