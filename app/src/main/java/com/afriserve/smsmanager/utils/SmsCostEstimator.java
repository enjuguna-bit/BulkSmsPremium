package com.afriserve.smsmanager.utils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * SmsCostEstimator
 *
 * Provides Kenyan-market SMS cost estimates for on-device (SIM-based) sending.
 *
 * <h3>Pricing basis (as of Q1 2025)</h3>
 * <ul>
 *   <li>Safaricom retail SMS rate: ~KES 1.00/SMS for person-to-person.</li>
 *   <li>Safaricom bulk A2P (via reseller or direct API): KES 0.22–0.40 per SMS
 *       depending on volume tier.</li>
 *   <li>Airtel Kenya A2P bulk: KES 0.25–0.45 per SMS.</li>
 *   <li>Telkom Kenya A2P bulk: KES 0.20–0.35 per SMS.</li>
 * </ul>
 *
 * These figures are used to give users a cost preview before launching a campaign.
 * They are <b>estimates only</b> — actual charges depend on the user's SIM contract.
 *
 * <h3>SMS part rules (GSM-7)</h3>
 * <ul>
 *   <li>1 part  : ≤ 160 characters</li>
 *   <li>2 parts : 161–306 characters (153 chars/part with UDH header)</li>
 *   <li>N parts : ⌈chars / 153⌉</li>
 * </ul>
 *
 * <h3>Usage example</h3>
 * <pre>
 *   SmsCostEstimator.CostEstimate est = SmsCostEstimator.estimate(
 *       "Hello {{name}}, your loan of KES {{amount}} is due.", 500, "Safaricom");
 *   // est.totalParts   → 1000
 *   // est.minCostKes   → 220.00  (KES 0.22 × 1000)
 *   // est.maxCostKes   → 400.00  (KES 0.40 × 1000)
 *   // est.summary()    → "~KES 220 – 400 (1,000 SMS parts, 500 recipients)"
 * </pre>
 */
public final class SmsCostEstimator {

    // ── GSM-7 character boundaries ────────────────────────────────────────────

    /** Maximum characters in a single-part GSM-7 SMS (no UDH). */
    public static final int SINGLE_PART_MAX    = 160;
    /** Maximum characters per part in a multipart GSM-7 SMS (UDH takes 7 octets). */
    public static final int MULTIPART_PART_MAX = 153;

    // ── Per-SMS pricing tiers (KES) ───────────────────────────────────────────

    /** Safaricom A2P bulk rate range (KES per SMS part). */
    public static final double SAFARICOM_RATE_MIN = 0.22;
    public static final double SAFARICOM_RATE_MAX = 0.40;

    /** Airtel Kenya A2P bulk rate range (KES per SMS part). */
    public static final double AIRTEL_RATE_MIN    = 0.25;
    public static final double AIRTEL_RATE_MAX    = 0.45;

    /** Telkom Kenya A2P bulk rate range (KES per SMS part). */
    public static final double TELKOM_RATE_MIN    = 0.20;
    public static final double TELKOM_RATE_MAX    = 0.35;

    /** Conservative default for unknown carriers (uses highest typical rate). */
    public static final double UNKNOWN_RATE_MIN   = 0.25;
    public static final double UNKNOWN_RATE_MAX   = 0.50;

    // ── Private constructor ───────────────────────────────────────────────────

    private SmsCostEstimator() {}

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Result of a cost estimation.
     */
    public static final class CostEstimate {
        /** Number of SMS parts for a single message (based on character count). */
        public final int partsPerMessage;
        /** Total SMS parts across all recipients. */
        public final int totalParts;
        /** Number of recipients. */
        public final int recipientCount;
        /** Lower-bound total cost in KES. */
        public final double minCostKes;
        /** Upper-bound total cost in KES. */
        public final double maxCostKes;
        /** Character count of the message. */
        public final int charCount;
        /** Carrier used for rate lookup. */
        @NonNull public final String carrier;

        CostEstimate(int partsPerMessage, int recipientCount,
                     double minCostKes, double maxCostKes,
                     int charCount, @NonNull String carrier) {
            this.partsPerMessage  = partsPerMessage;
            this.recipientCount   = recipientCount;
            this.totalParts       = partsPerMessage * recipientCount;
            this.minCostKes       = minCostKes;
            this.maxCostKes       = maxCostKes;
            this.charCount        = charCount;
            this.carrier          = carrier;
        }

        /**
         * Returns a human-readable one-line summary of the cost estimate.
         * Example: "~KES 220 – 400 (1,000 SMS parts, 500 recipients)"
         */
        @NonNull
        public String summary() {
            if (Math.abs(minCostKes - maxCostKes) < 0.01) {
                return String.format("~KES %.0f (%,d SMS part%s, %,d recipient%s)",
                        minCostKes,
                        totalParts,  totalParts  == 1 ? "" : "s",
                        recipientCount, recipientCount == 1 ? "" : "s");
            }
            return String.format("~KES %.0f – %.0f (%,d SMS part%s, %,d recipient%s)",
                    minCostKes, maxCostKes,
                    totalParts,  totalParts  == 1 ? "" : "s",
                    recipientCount, recipientCount == 1 ? "" : "s");
        }

        /**
         * Returns the mid-point cost estimate (average of min and max).
         */
        public double midCostKes() {
            return (minCostKes + maxCostKes) / 2.0;
        }
    }

    /**
     * Estimates the cost for sending {@code messageText} to {@code recipientCount}
     * recipients using the rate for {@code carrier}.
     *
     * @param messageText    The raw message text (template variables not expanded;
     *                       use the resolved character count for accuracy).
     * @param recipientCount Number of recipients.
     * @param carrier        Carrier display name: "Safaricom", "Airtel", "Telkom",
     *                       or any other value (falls back to unknown rates).
     * @return a {@link CostEstimate}; never null.
     */
    @NonNull
    public static CostEstimate estimate(@Nullable String messageText,
                                        int recipientCount,
                                        @Nullable String carrier) {
        int charCount      = messageText != null ? messageText.length() : 0;
        int partsPerMsg    = smsPartCount(charCount);
        double[] rates     = ratesForCarrier(carrier);
        int safeParts      = Math.max(0, partsPerMsg) * Math.max(0, recipientCount);
        return new CostEstimate(
                partsPerMsg,
                Math.max(0, recipientCount),
                safeParts * rates[0],
                safeParts * rates[1],
                charCount,
                carrier != null ? carrier : "Unknown"
        );
    }

    /**
     * Overload that accepts a pre-computed character count — useful when the
     * template has been expanded (variables replaced) and length is already known.
     */
    @NonNull
    public static CostEstimate estimateByCharCount(int charCount,
                                                   int recipientCount,
                                                   @Nullable String carrier) {
        int partsPerMsg = smsPartCount(charCount);
        double[] rates  = ratesForCarrier(carrier);
        int safeParts   = Math.max(0, partsPerMsg) * Math.max(0, recipientCount);
        return new CostEstimate(
                partsPerMsg,
                Math.max(0, recipientCount),
                safeParts * rates[0],
                safeParts * rates[1],
                charCount,
                carrier != null ? carrier : "Unknown"
        );
    }

    /**
     * Calculates the number of SMS parts for a message of {@code charCount}
     * characters using GSM-7 encoding rules.
     */
    public static int smsPartCount(int charCount) {
        if (charCount <= 0)              return 1;
        if (charCount <= SINGLE_PART_MAX) return 1;
        return (int) Math.ceil((double) charCount / MULTIPART_PART_MAX);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /** Returns {minRate, maxRate} for the given carrier display name. */
    private static double[] ratesForCarrier(@Nullable String carrier) {
        if (carrier == null) return new double[]{UNKNOWN_RATE_MIN, UNKNOWN_RATE_MAX};
        switch (carrier) {
            case "Safaricom": return new double[]{SAFARICOM_RATE_MIN, SAFARICOM_RATE_MAX};
            case "Airtel":    return new double[]{AIRTEL_RATE_MIN,    AIRTEL_RATE_MAX};
            case "Telkom":    return new double[]{TELKOM_RATE_MIN,    TELKOM_RATE_MAX};
            default:          return new double[]{UNKNOWN_RATE_MIN,   UNKNOWN_RATE_MAX};
        }
    }
}
