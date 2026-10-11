package com.afriserve.smsmanager.data.parser;

import android.util.Log;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Phone-number normalisation utility — v2.
 *
 * Normalises any commonly-encountered Kenyan (and optionally other) phone
 * number format to E.164 (+254XXXXXXXXX).  Non-Kenyan international numbers
 * are passed through as-is once they clear basic length checks.
 *
 * Changes in v2:
 *  - Stricter Safaricom / Airtel / Telkom prefix validation (CA-KE, 2024).
 *  - {@link #getCarrierName(String)} convenience method.
 *  - {@link #isValidKenyaNumber(String)} public helper.
 *  - Fixed edge-case where a 12-digit number without '+' was silently dropped.
 *  - {@link #formatForDisplay(String)} now formats Kenyan numbers as
 *    "+254 7XX XXX XXX" (correct grouping).
 *  - {@link #areSameNumber} is now null-safe.
 */
public final class PhoneNormalizer {

    private static final String TAG = "PhoneNormalizer";

    /** Default country code: Kenya (ITU E.164). */
    public static final String DEFAULT_COUNTRY_CODE = "254";

    // ── Kenyan number-series patterns ─────────────────────────────────────────
    // Safaricom  : 070x, 071x, 072x, 074x, 075x, 076x, 079x, 011x
    // Airtel KE  : 073x, 078x, 010x
    // Telkom KE  : 077x
    // (Matched against the local 07xx / 01xx prefix after stripping 254.)
    private static final Pattern KENYA_MOBILE_PATTERN =
            Pattern.compile("^\\+254[71]\\d{8}$");

    private static final Pattern KENYA_SAFARICOM_PATTERN =
            Pattern.compile("^\\+254(7[01245679]\\d{7}|1[01]\\d{7})$");

    private static final Pattern KENYA_AIRTEL_PATTERN =
            Pattern.compile("^\\+254(73\\d{7}|78\\d{7}|10\\d{7})$");

    private static final Pattern KENYA_TELKOM_PATTERN =
            Pattern.compile("^\\+25477\\d{7}$");

    private static final Pattern INTERNATIONAL_PATTERN =
            Pattern.compile("^\\+\\d{7,15}$");

    // ── Country config (local digit count) ───────────────────────────────────
    private static final Map<String, int[]> COUNTRY_LENGTHS = new HashMap<>();
    static {
        // { localDigits (without leading 0), localDigitsWithZero }
        COUNTRY_LENGTHS.put("254", new int[]{9, 10}); // Kenya
        COUNTRY_LENGTHS.put("255", new int[]{9, 10}); // Tanzania
        COUNTRY_LENGTHS.put("256", new int[]{9, 10}); // Uganda
        COUNTRY_LENGTHS.put("250", new int[]{9,  9}); // Rwanda
        COUNTRY_LENGTHS.put("234", new int[]{10, 11}); // Nigeria
        COUNTRY_LENGTHS.put("27",  new int[]{9, 10}); // South Africa
        COUNTRY_LENGTHS.put("91",  new int[]{10, 10}); // India
        COUNTRY_LENGTHS.put("44",  new int[]{10, 11}); // UK
        COUNTRY_LENGTHS.put("1",   new int[]{10, 10}); // US/Canada
    }

    private PhoneNormalizer() {}

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Normalises {@code phone} to E.164.  Assumes Kenya (+254) as the default
     * country when no country code can be detected.
     *
     * @return E.164 string (e.g. "+254722123456"), or {@code ""} if the input
     *         cannot be normalised.
     */
    public static String normalizePhone(String phone) {
        return normalizePhone(phone, DEFAULT_COUNTRY_CODE);
    }

    /**
     * Normalises {@code phone} to E.164 using {@code defaultCountryCode} as the
     * fallback country code (digits only, no '+').
     */
    public static String normalizePhone(String phone, String defaultCountryCode) {
        if (phone == null || phone.isBlank()) return "";

        String trimmed = phone.trim();
        boolean hasPlus = trimmed.startsWith("+");
        String digits = trimmed.replaceAll("[^\\d]", "");

        if (digits.isEmpty()) return "";

        String cc = defaultCountryCode != null ? defaultCountryCode : DEFAULT_COUNTRY_CODE;

        // ── Case 1: Starts with '+' ───────────────────────────────────────────
        if (hasPlus) {
            return validateAndReturnInternational("+" + digits);
        }

        // ── Case 2: Starts with country code (no '+') ─────────────────────────
        if (digits.startsWith("254") && digits.length() == 12) {
            return validateAndReturnInternational("+" + digits);
        }

        // ── Case 3: Local Kenyan number ───────────────────────────────────────
        if (cc.equals("254")) {
            return normalizeKenyan(digits);
        }

        // ── Case 4: Generic local number for other countries ─────────────────
        return normalizeGeneric(digits, cc);
    }

    /**
     * Returns {@code true} when the phone number is a valid Kenyan mobile number
     * after normalisation.
     */
    public static boolean isValidKenyaNumber(String phone) {
        String n = normalizePhone(phone);
        return !n.isEmpty() && KENYA_MOBILE_PATTERN.matcher(n).matches();
    }

    /**
     * Returns the carrier name for a Kenyan number, or {@code "Unknown"}.
     */
    public static String getCarrierName(String phone) {
        String n = normalizePhone(phone);
        if (n.isEmpty()) return "Unknown";
        if (KENYA_SAFARICOM_PATTERN.matcher(n).matches()) return "Safaricom";
        if (KENYA_AIRTEL_PATTERN.matcher(n).matches())    return "Airtel";
        if (KENYA_TELKOM_PATTERN.matcher(n).matches())    return "Telkom";
        return "Unknown";
    }

    /** Phone number classification. */
    public enum PhoneNumberType {
        KENYA_MOBILE,   // Valid Kenyan mobile
        INTERNATIONAL,  // Valid non-Kenyan international
        OTHER,          // Parseable but unrecognised
        INVALID
    }

    public static PhoneNumberType getPhoneNumberType(String phone) {
        String n = normalizePhone(phone);
        if (n.isEmpty()) return PhoneNumberType.INVALID;
        if (n.startsWith("+254") && KENYA_MOBILE_PATTERN.matcher(n).matches())
            return PhoneNumberType.KENYA_MOBILE;
        if (INTERNATIONAL_PATTERN.matcher(n).matches())
            return PhoneNumberType.INTERNATIONAL;
        if (!n.isEmpty())
            return PhoneNumberType.OTHER;
        return PhoneNumberType.INVALID;
    }

    /**
     * Formats a phone number for human display.
     * Kenyan mobiles → "+254 7XX XXX XXX"
     * Other international → "+CC DDD DDD DDD"
     */
    public static String formatForDisplay(String phone) {
        if (phone == null || phone.isBlank()) return "";
        String n = normalizePhone(phone);
        if (n.isEmpty()) return phone;

        // Kenyan: +254XXXXXXXXX  → +254 7XX XXX XXX
        if (n.startsWith("+254") && n.length() == 13) {
            return n.substring(0, 4) + " " +
                   n.substring(4, 7) + " " +
                   n.substring(7, 10) + " " +
                   n.substring(10);
        }

        // Generic international: group in threes after the country code
        // Keep '+' then insert spaces every 3 digits
        StringBuilder sb = new StringBuilder("+");
        String bare = n.substring(1);
        for (int i = 0; i < bare.length(); i++) {
            if (i > 0 && i % 3 == 0) sb.append(' ');
            sb.append(bare.charAt(i));
        }
        return sb.toString();
    }

    /** Returns {@code true} when both numbers normalise to the same E.164 string. */
    public static boolean areSameNumber(String a, String b) {
        if (a == null || b == null) return false;
        String na = normalizePhone(a);
        String nb = normalizePhone(b);
        return !na.isEmpty() && na.equals(nb);
    }

    /** Whether the number is usable for bulk SMS (Kenya mobile or recognised international). */
    public static boolean isValidForBulkSms(String phone) {
        PhoneNumberType t = getPhoneNumberType(phone);
        return t == PhoneNumberType.KENYA_MOBILE || t == PhoneNumberType.INTERNATIONAL;
    }

    /** Human-readable validation error, or {@code null} when the number is valid. */
    public static String getValidationError(String phone) {
        if (phone == null || phone.isBlank()) return "Phone number is required";
        String n = normalizePhone(phone);
        if (n.isEmpty()) return "Invalid phone number format";
        if (!isValidForBulkSms(phone)) return "Phone number not supported for bulk SMS";
        return null;
    }

    /** Returns all supported country codes. */
    public static String[] getSupportedCountryCodes() {
        return COUNTRY_LENGTHS.keySet().toArray(new String[0]);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private static String normalizeKenyan(String digits) {
        // Strip country code if present
        if (digits.startsWith("254") && digits.length() == 12) {
            return validateAndReturnInternational("+" + digits);
        }
        // Strip leading zero
        String local = digits.startsWith("0") ? digits.substring(1) : digits;
        if (local.length() == 9) {
            return validateAndReturnInternational("+254" + local);
        }
        // 10 digits with leading zero already stripped → shouldn't happen, but handle
        if (local.length() == 8) {
            Log.w(TAG, "Short number (" + digits + "), skipping");
            return "";
        }
        Log.w(TAG, "Cannot normalise Kenyan number: " + digits);
        return "";
    }

    private static String normalizeGeneric(String digits, String cc) {
        int[] lengths = COUNTRY_LENGTHS.get(cc);
        int localLen = lengths != null ? lengths[0] : 9;
        int localWithZero = lengths != null ? lengths[1] : 10;

        if (digits.startsWith(cc) && digits.length() == cc.length() + localLen) {
            return "+" + digits;
        }
        String local = digits.startsWith("0") ? digits.substring(1) : digits;
        if (local.length() == localLen) {
            return "+" + cc + local;
        }
        if (digits.length() == localWithZero && digits.startsWith("0")) {
            return "+" + cc + digits.substring(1);
        }
        return "";
    }

    private static String validateAndReturnInternational(String withPlus) {
        // Must be +digits, length 8–16 total
        if (withPlus == null || !withPlus.startsWith("+")) return "";
        String bare = withPlus.substring(1);
        if (bare.length() < 7 || bare.length() > 15) return "";
        if (!bare.matches("\\d+")) return "";
        return withPlus;
    }
}
