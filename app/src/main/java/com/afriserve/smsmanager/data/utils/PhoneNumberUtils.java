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
    
    public static String normalizePhoneNumber(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.trim().isEmpty()) {
            return null;
        }
        String cleaned = phoneNumber.trim().replaceAll("[\\s\\-\\(\\)\\.]", "");
        String normalized = cleaned.replaceAll(PHONE_NUMBER_PATTERN, "");
        if (normalized.isEmpty()) {
            return null;
        }
        if (cleaned.startsWith(INTERNATIONAL_PREFIX) && !normalized.startsWith(INTERNATIONAL_PREFIX)) {
            normalized = INTERNATIONAL_PREFIX + normalized;
        }
        if (!normalized.startsWith(INTERNATIONAL_PREFIX)) {
            normalized = normalized.replaceAll("^0+", "");
        }
        if (normalized.replaceAll("[^0-9]", "").length() < 3) {
            return null;
        }
        return normalized;
    }

    public static String formatForDisplay(String normalizedNumber) {
        if (normalizedNumber == null || normalizedNumber.isEmpty()) {
            return "Unknown";
        }
        if (normalizedNumber.startsWith(INTERNATIONAL_PREFIX)) {
            return normalizedNumber;
        }
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
        return normalizedNumber;
    }

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

    public static boolean areSameNumber(String phone1, String phone2) {
        String norm1 = normalizePhoneNumber(phone1);
        String norm2 = normalizePhoneNumber(phone2);
        if (norm1 == null || norm2 == null) {
            return false;
        }
        if (norm1.equals(norm2)) {
            return true;
        }
        String digits1 = norm1.replaceAll("[^0-9]", "");
        String digits2 = norm2.replaceAll("[^0-9]", "");
        if (digits1.length() >= 7 && digits2.length() >= 7) {
            String last7_1 = digits1.substring(digits1.length() - 7);
            String last7_2 = digits2.substring(digits2.length() - 7);
            if (last7_1.equals(last7_2)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isValidPhoneNumber(String phoneNumber) {
        String normalized = normalizePhoneNumber(phoneNumber);
        if (normalized == null) {
            return false;
        }
        String digits = normalized.replaceAll("[^0-9]", "");
        return digits.length() >= 7 && digits.length() <= 15;
    }

    public static String getCountryCode(String normalizedNumber) {
        if (normalizedNumber == null || !normalizedNumber.startsWith(INTERNATIONAL_PREFIX)) {
            return null;
        }
        String digits = normalizedNumber.substring(1);
        if (digits.startsWith("1")) return "1";
        if (digits.startsWith("44")) return "44";
        if (digits.startsWith("33")) return "33";
        if (digits.startsWith("49")) return "49";
        if (digits.startsWith("91")) return "91";
        if (digits.startsWith("86")) return "86";
        if (digits.startsWith("81")) return "81";
        if (digits.startsWith("82")) return "82";
        if (digits.startsWith("39")) return "39";
        if (digits.startsWith("34")) return "34";
        if (digits.startsWith("31")) return "31";
        if (digits.startsWith("46")) return "46";
        if (digits.startsWith("47")) return "47";
        if (digits.startsWith("48")) return "48";
        if (digits.startsWith("351")) return "351";
        if (digits.startsWith("358")) return "358";
        if (digits.startsWith("41")) return "41";
        if (digits.startsWith("43")) return "43";
        if (digits.startsWith("32")) return "32";
        if (digits.startsWith("45")) return "45";
        if (digits.startsWith("353")) return "353";
        if (digits.startsWith("352")) return "352";
        if (digits.startsWith("372")) return "372";
        if (digits.startsWith("371")) return "371";
        if (digits.startsWith("370")) return "370";
        if (digits.length() >= 3) {
            return digits.substring(0, 3);
        } else if (digits.length() >= 2) {
            return digits.substring(0, 2);
        } else {
            return digits.substring(0, 1);
        }
    }

    private PhoneNumberUtils() {
        throw new AssertionError("PhoneNumberUtils is a utility class and should not be instantiated");
    }
}
