package com.afriserve.smsmanager.utils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.afriserve.smsmanager.data.compliance.RateLimitManager;
import com.afriserve.smsmanager.data.parser.PhoneNormalizer;

/**
 * KenyaCarrierHelper
 *
 * Convenience façade that combines {@link PhoneNormalizer} and
 * {@link RateLimitManager} carrier-detection to provide rich carrier
 * metadata for display and compliance decisions.
 *
 * Example usage:
 * <pre>
 *   CarrierInfo info = KenyaCarrierHelper.getCarrierInfo("0722123456");
 *   // info.carrier    → "Safaricom"
 *   // info.e164       → "+254722123456"
 *   // info.rateLimit  → 60  (msgs/min)
 * </pre>
 */
public final class KenyaCarrierHelper {

    private KenyaCarrierHelper() {}

    // ── Data class ────────────────────────────────────────────────────────────

    public static final class CarrierInfo {
        /** Human-readable carrier name, e.g. "Safaricom". */
        @NonNull  public final String carrier;
        /** E.164-normalised number, e.g. "+254722123456". Empty if invalid. */
        @NonNull  public final String e164;
        /** Per-minute rate limit for this carrier (from RateLimitManager). */
        public final int rateLimit;
        /** True when the number is a recognised Kenyan mobile. */
        public final boolean isKenyanMobile;

        CarrierInfo(@NonNull String carrier, @NonNull String e164,
                    int rateLimit, boolean isKenyanMobile) {
            this.carrier        = carrier;
            this.e164           = e164;
            this.rateLimit      = rateLimit;
            this.isKenyanMobile = isKenyanMobile;
        }

        @Override
        public String toString() {
            return "CarrierInfo{carrier='" + carrier + "', e164='" + e164 +
                   "', rateLimit=" + rateLimit + ", isKenyan=" + isKenyanMobile + '}';
        }
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Returns full carrier metadata for {@code rawPhone}.  Never returns null.
     * If the number cannot be normalised, {@link CarrierInfo#e164} is empty and
     * {@link CarrierInfo#carrier} is "Unknown".
     */
    @NonNull
    public static CarrierInfo getCarrierInfo(@Nullable String rawPhone) {
        String e164    = PhoneNormalizer.normalizePhone(rawPhone);
        boolean kenyan = PhoneNormalizer.isValidKenyaNumber(rawPhone);
        String carrier = kenyan
                ? PhoneNormalizer.getCarrierName(rawPhone)
                : "Unknown";
        int limit      = getRateLimit(carrier);
        return new CarrierInfo(carrier, e164, limit, kenyan);
    }

    /**
     * Returns the per-minute rate limit for the given carrier display name.
     * Falls back to 20 for unrecognised carriers.
     */
    public static int getRateLimit(@Nullable String carrierDisplayName) {
        if (carrierDisplayName == null) return 20;
        switch (carrierDisplayName) {
            case "Safaricom": return 60;
            case "Airtel":    return 40;
            case "Telkom":    return 30;
            default:          return 20;
        }
    }

    /**
     * Converts a carrier display name to the internal identifier used by
     * {@link RateLimitManager}.
     */
    @NonNull
    public static String toRateLimitCarrierId(@Nullable String displayName) {
        if (displayName == null) return RateLimitManager.CARRIER_UNKNOWN;
        switch (displayName) {
            case "Safaricom": return RateLimitManager.CARRIER_SAFARICOM;
            case "Airtel":    return RateLimitManager.CARRIER_AIRTEL;
            case "Telkom":    return RateLimitManager.CARRIER_TELKOM;
            default:          return RateLimitManager.CARRIER_UNKNOWN;
        }
    }

    /**
     * Quick validity check: returns true when the number is a recognised
     * Kenyan mobile on any of the three major carriers.
     */
    public static boolean isKenyanMobile(@Nullable String phone) {
        return PhoneNormalizer.isValidKenyaNumber(phone);
    }
}
