package com.afriserve.smsmanager.data.utils;

import androidx.annotation.Nullable;

import com.afriserve.smsmanager.data.parser.PhoneNormalizer;

/**
 * PhoneNumberUtils — v2
 *
 * Previously contained its own normalisation logic which diverged from
 * PhoneNormalizer (the authoritative implementation). All methods now
 * delegate to PhoneNormalizer so there is a single source of truth for
 * phone number handling across the entire app.
 *
 * The public API surface is preserved for backwards compatibility.
 */
public final class PhoneNumberUtils {

    private PhoneNumberUtils() {}

    /**
     * Normalises a phone number to E.164 format (+254XXXXXXXXX for Kenya).
     * Returns {@code null} (not empty string) when the input is invalid,
     * to preserve the contract this class had before.
     */
    @Nullable
    public static String normalizePhoneNumber(@Nullable String phoneNumber) {
        if (phoneNumber == null || phoneNumber.isBlank()) return null;
        String e164 = PhoneNormalizer.normalizePhone(phoneNumber);
        return e164.isEmpty() ? null : e164;
    }

    /**
     * Returns a display-formatted phone number.
     * Delegates to PhoneNormalizer.formatForDisplay().
     */
    public static String formatForDisplay(@Nullable String normalizedNumber) {
        if (normalizedNumber == null || normalizedNumber.isEmpty()) return "Unknown";
        return PhoneNormalizer.formatForDisplay(normalizedNumber);
    }

    /**
     * Returns the last {@code n} digits of the normalised phone number.
     */
    @Nullable
    public static String getLastNDigits(@Nullable String phoneNumber, int n) {
        if (phoneNumber == null || n <= 0) return null;
        String norm = normalizePhoneNumber(phoneNumber);
        if (norm == null) return null;
        String digits = norm.replaceAll("[^\\d]", "");
        return digits.length() <= n ? digits : digits.substring(digits.length() - n);
    }

    /**
     * Returns {@code true} when both numbers normalise to the same E.164 string.
     */
    public static boolean areSameNumber(@Nullable String phone1, @Nullable String phone2) {
        return PhoneNormalizer.areSameNumber(phone1, phone2);
    }

    public static boolean isValidPhoneNumber(@Nullable String phoneNumber) {
        return PhoneNormalizer.isValidForBulkSms(phoneNumber);
    }
}
