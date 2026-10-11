package com.afriserve.smsmanager.data.compliance;

import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.afriserve.smsmanager.data.dao.OptOutDao;
import com.afriserve.smsmanager.data.entity.OptOutEntity;
import com.afriserve.smsmanager.data.parser.PhoneNormalizer;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Set;
import java.util.Collections;

import javax.inject.Inject;
import javax.inject.Singleton;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * ComplianceManager — v2
 *
 * Manages opt-out lists and time-window restrictions for Kenyan A2P SMS.
 *
 * Changes vs v1:
 *  - Removed all US/UK/India DND stub data — this app targets Kenya.
 *  - Phone normalisation now delegates to {@link PhoneNormalizer} so the same
 *    rules apply everywhere.
 *  - Time-restriction window now uses Africa/Nairobi timezone (EAT, UTC+3)
 *    instead of device local time.
 *  - {@code checkCompliance} is non-blocking; the opt-out look-up is
 *    cached in memory after first load so the DB is not hit per-message.
 *  - Added {@link #isOptedOutSync(String)} for callers that cannot use Rx.
 *  - Added "BULK_MARKETING" campaign type. Bulk sending is not blocked by the
 *    marketing quiet-hours rule; the caller still performs opt-out checks.
 *  - {@link ComplianceStats} now reports total opt-outs correctly.
 */
@Singleton
public class ComplianceManager {

    private static final String TAG = "ComplianceManager";

    /** Africa/Nairobi (EAT = UTC+3). All time-window checks use this zone. */
    private static final ZoneId EAT = ZoneId.of("Africa/Nairobi");

    // ── In-memory opt-out cache ───────────────────────────────────────────────
    /** Thread-safe set of E.164-normalised opted-out numbers. */
    private final Set<String> optOutCache = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private volatile boolean cacheLoaded = false;

    private final OptOutDao optOutDao;

    private final MutableLiveData<ComplianceStats> _complianceStats = new MutableLiveData<>();
    public final LiveData<ComplianceStats> complianceStats = _complianceStats;

    @Inject
    public ComplianceManager(OptOutDao optOutDao) {
        this.optOutDao = optOutDao;
        loadOptOutCache();
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Checks whether a phone number may be sent an SMS for the given campaign type.
     *
     * <p>Rules checked (in order):
     * <ol>
     *   <li>Phone-number format validity (via PhoneNormalizer).</li>
     *   <li>Internal opt-out list.</li>
     *   <li>Time-window restriction (EAT).</li>
     * </ol>
     */
    public Single<ComplianceResult> checkCompliance(String phoneNumber, String campaignType) {
        return Single.fromCallable(() -> {
            // 1. Validate & normalise
            String e164 = PhoneNormalizer.normalizePhone(phoneNumber);
            if (e164.isEmpty()) {
                return ComplianceResult.fail("Invalid phone number format",
                        ComplianceViolation.INVALID_FORMAT);
            }

            // 2. Opt-out list
            if (isOptedOutSync(e164)) {
                return ComplianceResult.fail("Number has opted out",
                        ComplianceViolation.OPTED_OUT);
            }

            // 3. Time restriction
            if (!isAllowedSendTime(campaignType)) {
                return ComplianceResult.fail(
                        "Sending not allowed at this time (EAT hours restriction)",
                        ComplianceViolation.TIME_RESTRICTION);
            }

            return ComplianceResult.pass();
        }).subscribeOn(Schedulers.io());
    }

    /** Adds a number to the opt-out list and updates the in-memory cache. */
    public Completable addToOptOut(String phoneNumber, String reason) {
        return Completable.fromAction(() -> {
            String e164 = PhoneNormalizer.normalizePhone(phoneNumber);
            if (e164.isEmpty()) throw new IllegalArgumentException("Invalid phone: " + phoneNumber);

            OptOutEntity entity = new OptOutEntity();
            entity.phoneNumber = e164;
            entity.reason      = reason != null ? reason : "USER_REQUEST";
            entity.optOutTime  = System.currentTimeMillis();
            entity.source      = "USER_REQUEST";

            optOutDao.insertOptOut(entity).blockingAwait();
            optOutCache.add(e164);
            updateStats();
            Log.d(TAG, "Opted out: " + e164);
        }).subscribeOn(Schedulers.io());
    }

    /** Removes a number from the opt-out list (admin action). */
    public Completable removeFromOptOut(String phoneNumber) {
        return Completable.fromAction(() -> {
            String e164 = PhoneNormalizer.normalizePhone(phoneNumber);
            optOutDao.deleteOptOutByPhone(e164).blockingAwait();
            optOutCache.remove(e164);
            updateStats();
            Log.d(TAG, "Removed opt-out: " + e164);
        }).subscribeOn(Schedulers.io());
    }

    /** Returns {@code true} when the E.164 number is in the opt-out cache. */
    public boolean isOptedOutSync(String e164Phone) {
        if (!cacheLoaded) loadOptOutCacheSync();
        return optOutCache.contains(e164Phone);
    }

    /** Validates that a phone number can be normalised to E.164. */
    public boolean isValidPhoneNumber(String phoneNumber) {
        return !PhoneNormalizer.normalizePhone(phoneNumber).isEmpty();
    }

    /** Normalises a phone number via PhoneNormalizer (Kenya-first). */
    public String normalizePhoneNumber(String phoneNumber) {
        return PhoneNormalizer.normalizePhone(phoneNumber);
    }

    public Single<ComplianceStats> getComplianceStatistics() {
        return Single.fromCallable(() -> new ComplianceStats(optOutCache.size()))
                .subscribeOn(Schedulers.io());
    }

    // ── Time-window logic (Africa/Nairobi) ────────────────────────────────────

    /**
     * Determines whether the current EAT time falls within the allowed sending
     * window for the given campaign type.
     *
     * <ul>
     *   <li>MARKETING                    : 08:00–21:00 EAT</li>
     *   <li>BULK_MARKETING               : always allowed</li>
     *   <li>TRANSACTIONAL               : 07:00–22:00 EAT</li>
     *   <li>EMERGENCY                   : always allowed</li>
     *   <li>(default)                   : 08:00–21:00 EAT</li>
     * </ul>
     */
    private boolean isAllowedSendTime(String campaignType) {
        int hour = ZonedDateTime.now(EAT).getHour();
        if (campaignType == null) return hour >= 8 && hour < 21;
        switch (campaignType.toUpperCase()) {
            case "MARKETING":
                return hour >= 8 && hour < 21;
            case "BULK_MARKETING":
                return true;
            case "TRANSACTIONAL":
                return hour >= 7 && hour < 22;
            case "EMERGENCY":
                return true;
            default:
                return hour >= 8 && hour < 21;
        }
    }

    // ── Cache helpers ─────────────────────────────────────────────────────────

    private void loadOptOutCache() {
        Schedulers.io().scheduleDirect(() -> {
            loadOptOutCacheSync();
            updateStats();
        });
    }

    private synchronized void loadOptOutCacheSync() {
        if (cacheLoaded) return;
        try {
            java.util.List<String> allActiveNumbers = optOutDao.getAllActiveOptOutNumbers().blockingGet();
            if (allActiveNumbers != null) {
                for (String phoneNumber : allActiveNumbers) {
                    if (phoneNumber != null && !phoneNumber.isEmpty()) {
                        optOutCache.add(phoneNumber);
                    }
                }
            }
            cacheLoaded = true;
            Log.d(TAG, "Opt-out cache loaded: " + optOutCache.size() + " entries");
        } catch (Exception ex) {
            Log.e(TAG, "Failed to load opt-out cache", ex);
        }
    }

    private void updateStats() {
        _complianceStats.postValue(new ComplianceStats(optOutCache.size()));
    }

    // ── Inner classes ─────────────────────────────────────────────────────────

    public static final class ComplianceResult {
        private final boolean compliant;
        private final String reason;
        private final ComplianceViolation violationType;

        public ComplianceResult() {
            this(false, "Compliance status unavailable", null);
        }

        private ComplianceResult(boolean compliant, String reason, ComplianceViolation violationType) {
            this.compliant     = compliant;
            this.reason        = reason;
            this.violationType = violationType;
        }

        public static ComplianceResult pass() {
            return new ComplianceResult(true, null, null);
        }

        public static ComplianceResult fail(String reason, ComplianceViolation violation) {
            return new ComplianceResult(false, reason, violation);
        }

        public boolean isCompliant()           { return compliant; }
        public String getReason()              { return reason; }
        public ComplianceViolation getViolationType() { return violationType; }

        // Compatibility setters (used by BulkSmsService)
        public void setCompliant(boolean v)    { /* immutable — use factory methods */ }
        public void setReason(String r)        { /* immutable */ }
        public void setViolationType(ComplianceViolation v) { /* immutable */ }
    }

    public enum ComplianceViolation {
        INVALID_FORMAT,
        OPTED_OUT,
        TIME_RESTRICTION,
        CONTENT_VIOLATION
    }

    public static final class ComplianceStats {
        public final int optOutCount;
        public final int totalBlocked;

        public ComplianceStats(int optOutCount) {
            this.optOutCount  = optOutCount;
            this.totalBlocked = optOutCount;
        }

        @Override
        public String toString() {
            return "ComplianceStats{optOutCount=" + optOutCount + '}';
        }
    }
}
