package com.afriserve.smsmanager.data.compliance;

import android.content.Context;
import android.util.Log;

import dagger.hilt.android.qualifiers.ApplicationContext;
import io.reactivex.rxjava3.core.BackpressureStrategy;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.subjects.BehaviorSubject;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Rate limiting manager for SMS sending.
 *
 * Upgraded v2: replaced US/UK carrier stubs with real Kenyan carrier detection
 * based on ITU-T E.164 number-plan prefixes for Safaricom, Airtel Kenya, and
 * Telkom Kenya.  Conservative limits align with what the carriers permit for
 * application-to-person (A2P) traffic on device SIM cards.
 */
@Singleton
public class RateLimitManager {

    private static final String TAG = "RateLimitManager";

    // ── Kenyan carrier identifiers ────────────────────────────────────────────
    public static final String CARRIER_SAFARICOM = "SAFARICOM";
    public static final String CARRIER_AIRTEL    = "AIRTEL_KE";
    public static final String CARRIER_TELKOM    = "TELKOM_KE";
    public static final String CARRIER_UNKNOWN   = "UNKNOWN";

    /**
     * Conservative per-minute limits for on-device SIM sending.
     * Safaricom allows slightly higher throughput; Telkom is more restrictive.
     */
    private static final Map<String, Integer> CARRIER_RATE_LIMITS;
    static {
        CARRIER_RATE_LIMITS = new HashMap<>();
        CARRIER_RATE_LIMITS.put(CARRIER_SAFARICOM, 60);  // 60 msgs/min
        CARRIER_RATE_LIMITS.put(CARRIER_AIRTEL,    40);  // 40 msgs/min
        CARRIER_RATE_LIMITS.put(CARRIER_TELKOM,    30);  // 30 msgs/min
        CARRIER_RATE_LIMITS.put(CARRIER_UNKNOWN,   20);  // safe default
    }

    /**
     * Kenyan number prefixes → carrier mapping.
     *
     * Source: Communications Authority of Kenya (CA) number allocation, 2024.
     *   Safaricom  : 070x, 071x, 072x, 074x, 075x, 076x, 079x, 011x
     *   Airtel KE  : 073x, 078x, 010x
     *   Telkom KE  : 077x
     *
     * Numbers are matched after stripping the country code (254) and any
     * leading zero so that the first four digits are checked.
     */
    private static final Map<String, String> PREFIX_CARRIER_MAP;
    static {
        PREFIX_CARRIER_MAP = new HashMap<>();
        // Safaricom (7-series + 1-series)
        for (String p : new String[]{"0700","0701","0702","0703","0704","0705","0706","0707","0708","0709",
                                     "0710","0711","0712","0713","0714","0715","0716","0717","0718","0719",
                                     "0720","0721","0722","0723","0724","0725","0726","0727","0728","0729",
                                     "0740","0741","0742","0743","0744","0745","0746","0747","0748","0749",
                                     "0750","0751","0752","0753","0754","0755","0756","0757","0758","0759",
                                     "0760","0761","0762","0763","0764","0765","0766","0767","0768","0769",
                                     "0790","0791","0792","0793","0794","0795","0796","0797","0798","0799",
                                     "0110","0111","0112","0113","0114","0115","0116","0117","0118","0119"}) {
            PREFIX_CARRIER_MAP.put(p, CARRIER_SAFARICOM);
        }
        // Airtel Kenya (7-series + 1-series)
        for (String p : new String[]{"0730","0731","0732","0733","0734","0735","0736","0737","0738","0739",
                                     "0780","0781","0782","0783","0784","0785","0786","0787","0788","0789",
                                     "0100","0101","0102","0103","0104","0105","0106","0107","0108","0109"}) {
            PREFIX_CARRIER_MAP.put(p, CARRIER_AIRTEL);
        }
        // Telkom Kenya (7-series)
        for (String p : new String[]{"0770","0771","0772","0773","0774","0775","0776","0777","0778","0779"}) {
            PREFIX_CARRIER_MAP.put(p, CARRIER_TELKOM);
        }
    }

    // ── State ─────────────────────────────────────────────────────────────────
    private final Map<String, RateLimitTracker>   rateLimitTrackers = new ConcurrentHashMap<>();
    private final BehaviorSubject<RateLimitStatus> rateLimitStatus   = BehaviorSubject.createDefault(RateLimitStatus.OK);
    private final AtomicLong lastGlobalSendTime = new AtomicLong(0);

    /** Minimum wall-clock gap between any two consecutive sends (100 ms). */
    private static final long MIN_GLOBAL_INTERVAL_MS = 100L;

    @Inject
    public RateLimitManager(@ApplicationContext Context context) {
        // Context retained for future use (e.g. TelephonyManager carrier name lookup)
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Returns the number of milliseconds the caller should wait before sending
     * the next message to {@code phoneNumber}.  Returns 0 when the send may
     * proceed immediately.
     */
    public long getDelayBeforeNextSend(String phoneNumber) {
        enforceGlobalInterval();
        String carrier = detectCarrier(phoneNumber);
        RateLimitTracker tracker = rateLimitTrackers.computeIfAbsent(carrier, this::createTracker);
        return tracker.calculateDelay();
    }

    /** Records a completed send for rate-limiting bookkeeping. */
    public void recordSend(String phoneNumber) {
        lastGlobalSendTime.set(System.currentTimeMillis());
        String carrier = detectCarrier(phoneNumber);
        RateLimitTracker tracker = rateLimitTrackers.get(carrier);
        if (tracker != null) tracker.recordSend();
        updateGlobalStatus();
        Log.d(TAG, "Recorded send for carrier: " + carrier);
    }

    public Flowable<RateLimitStatus> getRateLimitStatus() {
        return rateLimitStatus.toFlowable(BackpressureStrategy.LATEST);
    }

    public boolean isRateLimited() {
        return rateLimitStatus.getValue() != RateLimitStatus.OK;
    }

    public RateLimitStats getStats() {
        Map<String, CarrierStats> carrierStats = new HashMap<>();
        for (Map.Entry<String, RateLimitTracker> entry : rateLimitTrackers.entrySet()) {
            RateLimitTracker t = entry.getValue();
            carrierStats.put(entry.getKey(),
                    new CarrierStats(entry.getKey(), t.getCurrentRate(), t.getLimit(), t.isRateLimited()));
        }
        return new RateLimitStats(carrierStats, rateLimitStatus.getValue());
    }

    public void resetTracking() {
        rateLimitTrackers.clear();
        rateLimitStatus.onNext(RateLimitStatus.OK);
        lastGlobalSendTime.set(0);
        Log.d(TAG, "Rate limit tracking reset");
    }

    // ── Carrier detection ─────────────────────────────────────────────────────

    /**
     * Detects the Kenyan carrier from a phone number by matching its 4-digit
     * local prefix (in 0XXXXXXXXX form).  Numbers that do not normalise to a
     * recognised Kenyan prefix fall back to {@link #CARRIER_UNKNOWN}.
     */
    public static String detectCarrier(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.isBlank()) return CARRIER_UNKNOWN;

        String digits = phoneNumber.replaceAll("[^\\d]", "");

        // Normalise to local 0XXXXXXXXX (10 digits) for prefix lookup
        String local;
        if (digits.startsWith("254") && digits.length() == 12) {
            local = "0" + digits.substring(3); // 254XXXXXXXXX → 0XXXXXXXXX
        } else if (digits.startsWith("0") && digits.length() == 10) {
            local = digits;
        } else if ((digits.startsWith("7") || digits.startsWith("1")) && digits.length() == 9) {
            local = "0" + digits;
        } else {
            return CARRIER_UNKNOWN;
        }

        // Match first 4 characters (e.g. "0722")
        if (local.length() >= 4) {
            String prefix4 = local.substring(0, 4);
            String carrier = PREFIX_CARRIER_MAP.get(prefix4);
            if (carrier != null) return carrier;
        }

        return CARRIER_UNKNOWN;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void enforceGlobalInterval() {
        long elapsed = System.currentTimeMillis() - lastGlobalSendTime.get();
        if (elapsed < MIN_GLOBAL_INTERVAL_MS) {
            try { Thread.sleep(MIN_GLOBAL_INTERVAL_MS - elapsed); } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void updateGlobalStatus() {
        boolean anyLimited = false;
        boolean anyWarning = false;
        for (RateLimitTracker t : rateLimitTrackers.values()) {
            if (t.isRateLimited()) { anyLimited = true; break; }
            double pct = t.getLimit() > 0 ? (double) t.getCurrentRate() / t.getLimit() : 0;
            if (pct >= 0.80) anyWarning = true;
        }
        if (anyLimited) rateLimitStatus.onNext(RateLimitStatus.LIMITED);
        else if (anyWarning) rateLimitStatus.onNext(RateLimitStatus.WARNING);
        else rateLimitStatus.onNext(RateLimitStatus.OK);
    }

    private RateLimitTracker createTracker(String carrier) {
        int limit = CARRIER_RATE_LIMITS.getOrDefault(carrier, 20);
        return new RateLimitTracker(carrier, limit);
    }

    // ── Inner classes ─────────────────────────────────────────────────────────

    private static class RateLimitTracker {
        private final int limitPerMinute;
        private final AtomicLong messagesInCurrentMinute = new AtomicLong(0);
        private final AtomicLong currentMinuteStart      = new AtomicLong(System.currentTimeMillis());
        private final AtomicLong lastSendTime            = new AtomicLong(0);

        RateLimitTracker(String carrier, int limitPerMinute) {
            this.limitPerMinute = limitPerMinute;
        }

        long calculateDelay() {
            long now = System.currentTimeMillis();
            refreshWindow(now);
            if (messagesInCurrentMinute.get() >= limitPerMinute) {
                long nextMinute = currentMinuteStart.get() + 60_000L;
                return Math.max(0, nextMinute - now);
            }
            long minInterval = limitPerMinute > 0 ? 60_000L / limitPerMinute : 1000L;
            return Math.max(0, minInterval - (now - lastSendTime.get()));
        }

        void recordSend() {
            long now = System.currentTimeMillis();
            refreshWindow(now);
            messagesInCurrentMinute.incrementAndGet();
            lastSendTime.set(now);
        }

        double getCurrentRate() { return messagesInCurrentMinute.get(); }
        int    getLimit()       { return limitPerMinute; }
        boolean isRateLimited() { return getCurrentRate() >= limitPerMinute; }

        private void refreshWindow(long now) {
            long window = (now / 60_000L) * 60_000L;
            if (currentMinuteStart.get() != window) {
                currentMinuteStart.set(window);
                messagesInCurrentMinute.set(0);
            }
        }
    }

    public enum RateLimitStatus { OK, WARNING, LIMITED, BLOCKED }

    public static class RateLimitStats {
        public final Map<String, CarrierStats> carrierStats;
        public final RateLimitStatus globalStatus;
        public RateLimitStats(Map<String, CarrierStats> carrierStats, RateLimitStatus globalStatus) {
            this.carrierStats = carrierStats;
            this.globalStatus = globalStatus;
        }
        public boolean isNearLimit() {
            if (globalStatus == RateLimitStatus.WARNING || globalStatus == RateLimitStatus.LIMITED) return true;
            for (CarrierStats c : carrierStats.values()) if (c.isRateLimited) return true;
            return false;
        }
        public int getUsagePercentage() {
            if (carrierStats.isEmpty()) return 0;
            double sum = 0; int n = 0;
            for (CarrierStats c : carrierStats.values()) {
                if (c.limit > 0) { sum += (c.currentRate / c.limit) * 100.0; n++; }
            }
            return n == 0 ? 0 : (int) Math.round(sum / n);
        }
    }

    public static class CarrierStats {
        public final String  carrier;
        public final double  currentRate;
        public final int     limit;
        public final boolean isRateLimited;
        public CarrierStats(String carrier, double currentRate, int limit, boolean isRateLimited) {
            this.carrier      = carrier;
            this.currentRate  = currentRate;
            this.limit        = limit;
            this.isRateLimited = isRateLimited;
        }
    }
}
