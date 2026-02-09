package com.afriserve.smsmanager.auth;

/**
 * Enum representing biometric status
 */
public enum BiometricStatus {
    AVAILABLE,      // Biometric available and ready
    NOT_ENROLLED,   // No biometric enrolled
    NO_HARDWARE,    // No biometric hardware
    HW_UNAVAILABLE, // Hardware unavailable
    UNKNOWN         // Unknown status
}
