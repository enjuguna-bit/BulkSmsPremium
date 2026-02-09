package com.afriserve.smsmanager.data.utils;

/**
 * Utility class for consistent phone number normalization across all components
 * Ensures phone numbers are formatted consistently for database storage, 
 * contact resolution, and search operations
 */
public final class PhoneNumberUtils {
    
    private static final String TAG = "PhoneNumberUtils";
    
    // Common phone number patterns
    private static final String PHONE_NUMBER_PATTERN = "[^0-9+]";
    private static final String INTERNATIONAL_PREFIX = "+";
    private static final String COUNTRY_CODE_US = "1";
    
    /**
     * Normalizes phone number for consistent storage and lookup
     * 
     * Rules:
     * 1. Remove all non-numeric characters except + sign
     * 2. Preserve leading + for international numbers
     * 3. Remove leading zeros for normalized format
     * 4. Return null for invalid input
     * 
     * @param phoneNumber Raw phone number
     * @return Normalized phone number or null if invalid
     */
    public static String normalizePhoneNumber(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.trim().isEmpty()) {
            return null;
        }
        
        // Remove whitespace and common separators
        String cleaned = phoneNumber.trim().replaceAll("[\\s\\-\\(\\)\\.]", "");
        
        // Keep only digits and + sign
        String normalized = cleaned.replaceAll(PHONE_NUMBER_PATTERN, "");
        
        // Handle empty result
        if (normalized.isEmpty()) {
            return null;
        }
        
        // Preserve leading + for international numbers
        if (cleaned.startsWith(INTERNATIONAL_PREFIX) && !normalized.startsWith(INTERNATIONAL_PREFIX)) {
            normalized = INTERNATIONAL_PREFIX + normalized;
        }
        
        // Remove leading zeros (except after +)
        if (!normalized.startsWith(INTERNATIONAL_PREFIX)) {
            normalized = normalized.replaceAll("^0+", "");
        }
        
        // Validate result has at least some digits
        if (normalized.replaceAll("[^0-9]", "").length() < 3) {
            return null;
        }
        
        return normalized;
    }
    
    /**
     * Formats phone number for display purposes
     * 
     * @param normalizedNumber Normalized phone number
     * @return Formatted phone number for UI display
     */
    public static String formatForDisplay(String normalizedNumber) {
        if (normalizedNumber == null || normalizedNumber.isEmpty()) {
            return "Unknown";
        }
        
        // If it's an international number, keep it as is
        if (normalizedNumber.startsWith(INTERNATIONAL_PREFIX)) {
            return normalizedNumber;
        }
        
        // For US numbers, format as (XXX) XXX-XXXX
        if (normalizedNumber.length() == 10 && 
            (normalizedNumber.startsWith(COUNTRY_CODE_US) || !normalizedNumber.startsWith("0"))) {
            
            String digits = normalizedNumber.length() == 11 && normalizedNumber.startsWith(COUNTRY_CODE_US) 
                ? normalizedNumber.substring(1) 
                : normalizedNumber;
                
            if (digits.length() == 10) {
                return String.format("(%s) %s-%s", 
                    digits.substring(0, 3),
                    digits.substring(3, 6),
                    digits.substring(6));
            }
        }
        
        // Return as-is if no specific formatting applies
        return normalizedNumber;
    }
    
    /**
     * Extracts the last N digits of a phone number for search/matching
     * 
     * @param phoneNumber Normalized phone number
     * @param lastNDigits Number of digits to extract (3-10 recommended)
     * @return Last N digits or null if invalid
     */
    public static String getLastNDigits(String phoneNumber, int lastNDigits) {
        if (phoneNumber == null || lastNDigits <= 0) {
            return null;
        }
        
        String normalized = normalizePhoneNumber(phoneNumber);
        if (normalized == null) {
            return null;
        }
        
        String digits = normalized.replaceAll("[^0-9]", "");
        if (digits.length() < lastNDigits) {
            return digits;
        }
        
        return digits.substring(digits.length() - lastNDigits);
    }
    
    /**
     * Checks if two phone numbers are likely the same
     * Uses various matching strategies for robust comparison
     * 
     * @param phone1 First phone number
     * @param phone2 Second phone number
     * @return true if numbers are likely the same
     */
    public static boolean areSameNumber(String phone1, String phone2) {
        String norm1 = normalizePhoneNumber(phone1);
        String norm2 = normalizePhoneNumber(phone2);
        
        if (norm1 == null || norm2 == null) {
            return false;
        }
        
        // Direct match
        if (norm1.equals(norm2)) {
            return true;
        }
        
        // Remove international prefix and compare
        String digits1 = norm1.replaceAll("[^0-9]", "");
        String digits2 = norm2.replaceAll("[^0-9]", "");
        
        // Compare last 7 digits (common for local numbers)
        if (digits1.length() >= 7 && digits2.length() >= 7) {
            String last7_1 = digits1.substring(digits1.length() - 7);
            String last7_2 = digits2.substring(digits2.length() - 7);
            if (last7_1.equals(last7_2)) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Validates if a string represents a valid phone number
     * 
     * @param phoneNumber Phone number to validate
     * @return true if valid phone number
     */
    public static boolean isValidPhoneNumber(String phoneNumber) {
        String normalized = normalizePhoneNumber(phoneNumber);
        if (normalized == null) {
            return false;
        }
        
        String digits = normalized.replaceAll("[^0-9]", "");
        
        // Valid phone numbers have 7-15 digits (international standard)
        return digits.length() >= 7 && digits.length() <= 15;
    }
    
    /**
     * Gets the country code from a normalized international number
     * 
     * @param normalizedNumber Normalized phone number with +
     * @return Country code or null if not international
     */
    public static String getCountryCode(String normalizedNumber) {
        if (normalizedNumber == null || !normalizedNumber.startsWith(INTERNATIONAL_PREFIX)) {
            return null;
        }
        
        String digits = normalizedNumber.substring(1); // Remove +
        
        // Common country code patterns
        if (digits.startsWith("1")) return "1"; // US/Canada
        if (digits.startsWith("44")) return "44"; // UK
        if (digits.startsWith("33")) return "33"; // France
        if (digits.startsWith("49")) return "49"; // Germany
        if (digits.startsWith("91")) return "91"; // India
        if (digits.startsWith("86")) return "86"; // China
        if (digits.startsWith("81")) return "81"; // Japan
        if (digits.startsWith("82")) return "82"; // South Korea
        if (digits.startsWith("39")) return "39"; // Italy
        if (digits.startsWith("34")) return "34"; // Spain
        if (digits.startsWith("31")) return "31"; // Netherlands
        if (digits.startsWith("46")) return "46"; // Sweden
        if (digits.startsWith("47")) return "47"; // Norway
        if (digits.startsWith("48")) return "48"; // Poland
        if (digits.startsWith("351")) return "351"; // Portugal
        if (digits.startsWith("358")) return "358"; // Finland
        if (digits.startsWith("41")) return "41"; // Switzerland
        if (digits.startsWith("43")) return "43"; // Austria
        if (digits.startsWith("32")) return "32"; // Belgium
        if (digits.startsWith("45")) return "45"; // Denmark
        if (digits.startsWith("353")) return "353"; // Ireland
        if (digits.startsWith("352")) return "352"; // Luxembourg
        if (digits.startsWith("372")) return "372"; // Estonia
        if (digits.startsWith("371")) return "371"; // Latvia
        if (digits.startsWith("370")) return "370"; // Lithuania
        
        // Default: return first 1-3 digits as country code
        if (digits.length() >= 3) {
            return digits.substring(0, 3);
        } else if (digits.length() >= 2) {
            return digits.substring(0, 2);
        } else {
            return digits.substring(0, 1);
        }
    }
    
    /**
     * Private constructor to prevent instantiation
     */
    private PhoneNumberUtils() {
        throw new AssertionError("PhoneNumberUtils is a utility class and should not be instantiated");
    }
}
