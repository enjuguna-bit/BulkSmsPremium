package com.afriserve.smsmanager.data.parser;

import android.util.Log;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Enhanced Phone number normalization utility with country-specific configurations
 * Handles various phone number formats and normalizes them to standard E.164 format
 */
public class PhoneNormalizer {
    private static final String TAG = "PhoneNormalizer";
    
    // Default country code for phone normalization (Kenya)
    public static final String DEFAULT_COUNTRY_CODE = "254";
    
    // Country-specific configurations for phone normalization
    private static final Map<String, CountryConfig> COUNTRY_CONFIGS = new HashMap<>();
    
    static {
        // Kenya: 7XXXXXXXX or 07XXXXXXXX
        COUNTRY_CONFIGS.put("254", new CountryConfig(9, 10));
        
        // US/Canada: 10 digits
        COUNTRY_CONFIGS.put("1", new CountryConfig(10, 10));
        
        // UK: 10 digits or 11 with 0
        COUNTRY_CONFIGS.put("44", new CountryConfig(10, 11));
        
        // India: 10 digits
        COUNTRY_CONFIGS.put("91", new CountryConfig(10, 10));
        
        // South Africa: 9 digits or 10 with 0
        COUNTRY_CONFIGS.put("27", new CountryConfig(9, 10));
        
        // Nigeria: 10 digits or 11 with 0
        COUNTRY_CONFIGS.put("234", new CountryConfig(10, 11));
        
        // Tanzania: 9 digits or 10 with 0
        COUNTRY_CONFIGS.put("255", new CountryConfig(9, 10));
        
        // Uganda: 9 digits or 10 with 0
        COUNTRY_CONFIGS.put("256", new CountryConfig(9, 10));
    }
    
    // Kenya phone number patterns
    private static final Pattern KENYA_MOBILE_PATTERN = Pattern.compile("^(?:\\+254|0)?[17]\\d{8}$");
    private static final Pattern INTERNATIONAL_PATTERN = Pattern.compile("^\\+\\d{10,15}$");
    private static final Pattern LOCAL_PATTERN = Pattern.compile("^0\\d{9}$");
    
    /**
     * Country configuration data class
     */
    private static class CountryConfig {
        final int localLength;
        final int withZeroLength;
        
        CountryConfig(int localLength, int withZeroLength) {
            this.localLength = localLength;
            this.withZeroLength = withZeroLength;
        }
    }
    
    /**
     * Normalize phone number to E.164 format with reasonable validation
     * Accepts various formats and normalizes them, rejecting only obviously invalid numbers
     */
    public static String normalizePhone(String phone) {
        return normalizePhone(phone, DEFAULT_COUNTRY_CODE);
    }
    
    /**
     * Normalize phone number to E.164 format with country code override
     */
    public static String normalizePhone(String phone, String countryCode) {
        if (phone == null || phone.trim().isEmpty()) {
            return "";
        }
        
        // Remove all non-numeric characters except leading +
        String cleaned = phone.trim();
        boolean hasLeadingPlus = cleaned.startsWith("+");
        cleaned = cleaned.replaceAll("[^\\d]", "");
        
        if (hasLeadingPlus) {
            cleaned = "+" + cleaned;
        }
        
        // If empty after cleaning, return empty
        if (cleaned.isEmpty()) {
            return "";
        }
        
        // Handle different formats
        if (cleaned.startsWith("+")) {
            // International format: +XXXXXXXXX
            return handleInternationalFormat(cleaned, countryCode);
        } else {
            // Local format: various possibilities
            return handleLocalFormat(cleaned, countryCode);
        }
    }
    
    /**
     * Handle international format phone numbers
     */
    private static String handleInternationalFormat(String cleaned, String countryCode) {
        String withoutPlus = cleaned.substring(1);
        
        // If it's already +254 format, validate basic length for Kenya
        if (withoutPlus.startsWith("254")) {
            String localNumber = withoutPlus.substring(3);
            // Accept any 6-9 digit number for Kenya (be more permissive)
            if (localNumber.length() >= 6 && localNumber.length() <= 9) {
                return "+254" + padAndTruncate(localNumber, 9);
            }
        }
        
        // For other international numbers, accept reasonable lengths (7-15 digits)
        if (withoutPlus.length() >= 7 && withoutPlus.length() <= 15) {
            return cleaned;
        }
        
        return ""; // Invalid international format
    }
    
    /**
     * Handle local format phone numbers
     */
    private static String handleLocalFormat(String cleaned, String countryCode) {
        String digits = cleaned;
        
        // Handle leading 254 (without +) - international format without +
        if (digits.startsWith("254")) {
            digits = digits.substring(3);
        }
        
        // Remove leading zero if present
        if (digits.startsWith("0")) {
            digits = digits.substring(1);
        }
        
        // Accept reasonable local lengths (6-10 digits, be permissive)
        if (digits.length() >= 6 && digits.length() <= 10) {
            // Pad to 9 digits for Kenyan numbers, but accept other lengths for international
            if (digits.length() <= 9) {
                return "+254" + padAndTruncate(digits, 9);
            } else {
                // Assume it's already an international number
                return "+" + digits;
            }
        }
        
        return ""; // Invalid local format
    }
    
    /**
     * Pad or truncate number to target length
     */
    private static String padAndTruncate(String number, int targetLength) {
        if (number.length() >= targetLength) {
            return number.substring(number.length() - targetLength);
        } else {
            // Pad with leading zeros
            return String.format("%0" + targetLength + "d", Integer.parseInt(number));
        }
    }
    
    /**
     * Get phone number type
     */
    public static PhoneNumberType getPhoneNumberType(String phoneNumber) {
        if (phoneNumber == null) return PhoneNumberType.INVALID;
        
        String normalized = normalizePhone(phoneNumber);
        if (normalized.isEmpty()) return PhoneNumberType.INVALID;
        
        if (normalized.startsWith("+254")) {
            if (isValidKenyaMobile(normalized)) {
                return PhoneNumberType.KENYA_MOBILE;
            }
        } else if (normalized.startsWith("+")) {
            return PhoneNumberType.INTERNATIONAL;
        }
        
        return PhoneNumberType.OTHER;
    }
    
    /**
     * Format phone number for display
     */
    public static String formatForDisplay(String phoneNumber) {
        if (phoneNumber == null) return "";
        
        String normalized = normalizePhone(phoneNumber);
        if (normalized.isEmpty()) return phoneNumber;
        
        PhoneNumberType type = getPhoneNumberType(normalized);
        
        switch (type) {
            case KENYA_MOBILE:
                // Format: +254 7XX XXX XXX
                if (normalized.length() == 13) {
                    return normalized.substring(0, 4) + " " + 
                           normalized.substring(4, 7) + " " + 
                           normalized.substring(7, 10) + " " + 
                           normalized.substring(10);
                }
                break;
            case INTERNATIONAL:
                // Format: +XXX XXX XXX XXX (group by 3)
                StringBuilder formatted = new StringBuilder();
                for (int i = 0; i < normalized.length(); i++) {
                    if (i > 0 && i % 3 == 0) {
                        formatted.append(" ");
                    }
                    formatted.append(normalized.charAt(i));
                }
                return formatted.toString();
        }
        
        return normalized;
    }
    
    /**
     * Check if two phone numbers are the same
     */
    public static boolean areSameNumber(String phone1, String phone2) {
        String normalized1 = normalizePhone(phone1);
        String normalized2 = normalizePhone(phone2);
        
        return !normalized1.isEmpty() && normalized1.equals(normalized2);
    }
    
    /**
     * Extract country code from phone number
     */
    public static String extractCountryCode(String phoneNumber) {
        if (phoneNumber == null) return null;
        
        String normalized = normalizePhone(phoneNumber);
        if (normalized.isEmpty() || !normalized.startsWith("+")) {
            return null;
        }
        
        // Extract country code (first 2-4 digits after +)
        for (int i = 2; i <= 4; i++) {
            if (normalized.length() > i) {
                String code = normalized.substring(0, i);
                if (isValidCountryCode(code)) {
                    return code;
                }
            }
        }
        
        return null;
    }
    
    /**
     * Check if country code is valid
     */
    private static boolean isValidCountryCode(String countryCode) {
        // Basic validation - country codes are 2-4 digits
        return countryCode.matches("^\\+\\d{2,4}$");
    }
    
    /**
     * Validate Kenya mobile number
     */
    private static boolean isValidKenyaMobile(String phoneNumber) {
        return KENYA_MOBILE_PATTERN.matcher(phoneNumber).matches();
    }
    
    /**
     * Validate international number
     */
    private static boolean isValidInternational(String phoneNumber) {
        return INTERNATIONAL_PATTERN.matcher(phoneNumber).matches();
    }
    
    /**
     * Validate local number
     */
    private static boolean isValidLocal(String phoneNumber) {
        return LOCAL_PATTERN.matcher(phoneNumber).matches();
    }
    
    /**
     * Validate phone number for bulk SMS sending
     */
    public static boolean isValidForBulkSms(String phoneNumber) {
        PhoneNumberType type = getPhoneNumberType(phoneNumber);
        return type == PhoneNumberType.KENYA_MOBILE || type == PhoneNumberType.INTERNATIONAL;
    }
    
    /**
     * Get validation error message
     */
    public static String getValidationError(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.trim().isEmpty()) {
            return "Phone number is required";
        }
        
        String normalized = normalizePhone(phoneNumber);
        if (normalized.isEmpty()) {
            return "Invalid phone number format";
        }
        
        PhoneNumberType type = getPhoneNumberType(normalized);
        if (type == PhoneNumberType.INVALID) {
            return "Invalid phone number format";
        }
        
        if (!isValidForBulkSms(phoneNumber)) {
            return "Phone number is not supported for bulk SMS";
        }
        
        return null;
    }
    
    /**
     * Get country-specific validation info
     */
    public static CountryValidationInfo getCountryValidationInfo(String countryCode) {
        CountryConfig config = COUNTRY_CONFIGS.get(countryCode);
        if (config == null) {
            return new CountryValidationInfo(countryCode, 7, 15, false);
        }
        
        return new CountryValidationInfo(
            countryCode, 
            config.localLength, 
            config.localLength + 3, // + country code
            true
        );
    }
    
    /**
     * Country validation info data class
     */
    public static class CountryValidationInfo {
        public final String countryCode;
        public final int minLocalLength;
        public final int maxInternationalLength;
        public final boolean isSupported;
        
        CountryValidationInfo(String countryCode, int minLocalLength, int maxInternationalLength, boolean isSupported) {
            this.countryCode = countryCode;
            this.minLocalLength = minLocalLength;
            this.maxInternationalLength = maxInternationalLength;
            this.isSupported = isSupported;
        }
        
        @Override
        public String toString() {
            return "CountryValidationInfo{" +
                   "countryCode='" + countryCode + '\'' +
                   ", minLocalLength=" + minLocalLength +
                   ", maxInternationalLength=" + maxInternationalLength +
                   ", isSupported=" + isSupported +
                   '}';
        }
    }
    
    /**
     * Phone number types
     */
    public enum PhoneNumberType {
        KENYA_MOBILE,
        INTERNATIONAL,
        OTHER,
        INVALID
    }
    
    /**
     * Get all supported country codes
     */
    public static String[] getSupportedCountryCodes() {
        return COUNTRY_CONFIGS.keySet().toArray(new String[0]);
    }
    
    /**
     * Check if country code is supported
     */
    public static boolean isCountryCodeSupported(String countryCode) {
        return COUNTRY_CONFIGS.containsKey(countryCode);
    }
    
    /**
     * Normalize phone number with automatic country detection
     */
    public static String normalizePhoneAuto(String phone) {
        if (phone == null) return "";
        
        // Try to detect country from the number
        String detectedCountry = detectCountryCode(phone);
        return normalizePhone(phone, detectedCountry);
    }
    
    /**
     * Detect country code from phone number
     */
    private static String detectCountryCode(String phone) {
        String cleaned = phone.replaceAll("[^\\d+]", "");
        
        if (cleaned.startsWith("+")) {
            String withoutPlus = cleaned.substring(1);
            
            // Try to match known country codes
            for (String countryCode : COUNTRY_CONFIGS.keySet()) {
                if (withoutPlus.startsWith(countryCode)) {
                    return countryCode;
                }
            }
        }
        
        return DEFAULT_COUNTRY_CODE;
    }
}
