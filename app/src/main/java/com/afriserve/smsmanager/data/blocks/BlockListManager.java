package com.afriserve.smsmanager.data.blocks;

import android.content.Context;
import android.content.SharedPreferences;

import android.util.Log;
import com.afriserve.smsmanager.data.dao.OptOutDao;
import com.afriserve.smsmanager.data.entity.OptOutEntity;
import com.afriserve.smsmanager.data.utils.PhoneNumberUtils;

import java.util.HashSet;
import java.util.Set;

/**
 * Simple block list manager backed by SharedPreferences.
 */
public class BlockListManager {
    private static final String PREFS_NAME = "blocked_numbers";
    private static final String KEY_BLOCKED_SET = "blocked_set";

    private final SharedPreferences prefs;

    public BlockListManager(Context context) {
        this.prefs = context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public boolean isBlocked(String phoneNumber) {
        String normalized = normalize(phoneNumber);
        if (normalized == null) return false;
        Set<String> blocked = prefs.getStringSet(KEY_BLOCKED_SET, new HashSet<>());
        return blocked != null && blocked.contains(normalized);
    }

    public void block(String phoneNumber) {
        String normalized = normalize(phoneNumber);
        if (normalized == null) return;
        Set<String> blocked = new HashSet<>(prefs.getStringSet(KEY_BLOCKED_SET, new HashSet<>()));
        blocked.add(normalized);
        prefs.edit().putStringSet(KEY_BLOCKED_SET, blocked).apply();
    }

    public void unblock(String phoneNumber) {
        String normalized = normalize(phoneNumber);
        if (normalized == null) return;
        Set<String> blocked = new HashSet<>(prefs.getStringSet(KEY_BLOCKED_SET, new HashSet<>()));
        blocked.remove(normalized);
        prefs.edit().putStringSet(KEY_BLOCKED_SET, blocked).apply();
    }

    public Set<String> getAllBlocked() {
        return new HashSet<>(prefs.getStringSet(KEY_BLOCKED_SET, new HashSet<>()));
    }

    // ── Dual-store helpers ────────────────────────────────────────────────────

    /**
     * Blocks a number in SharedPreferences AND inserts it into {@link OptOutDao}
     * so that {@link com.afriserve.smsmanager.data.compliance.ComplianceManager}
     * also recognises the number as opted-out.
     *
     * <p>Must be called from a background thread when {@code optOutDao} is provided.
     *
     * @param phoneNumber phone number to block
     * @param optOutDao   Room DAO for the opt_outs table; pass null to skip DB sync.
     * @param reason      human-readable reason stored in the opt_outs record
     */
    public void blockAndOptOut(String phoneNumber, OptOutDao optOutDao, String reason) {
        block(phoneNumber);
        if (optOutDao == null) return;
        String normalized = normalize(phoneNumber);
        if (normalized == null) return;
        try {
            // Only insert if not already opted-out to avoid duplicate-key errors.
            try { optOutDao.getOptOutByPhone(normalized).blockingGet(); return; }
            catch (Exception notFound) { /* proceed */ }

            OptOutEntity entity = new OptOutEntity();
            entity.phoneNumber = normalized;
            entity.reason      = reason != null ? reason : "BLOCKED";
            entity.optOutTime  = System.currentTimeMillis();
            entity.source      = "BLOCK_LIST";
            entity.isActive    = true;
            optOutDao.insertOptOut(entity).blockingAwait();
            Log.d("BlockListManager", "Synced block to opt-out DB: " + normalized);
        } catch (Exception e) {
            Log.w("BlockListManager", "Failed to sync block to opt-out DB", e);
        }
    }

    /**
     * Removes a block in SharedPreferences AND deactivates the opt-out record in DB.
     *
     * <p>Must be called from a background thread when {@code optOutDao} is provided.
     */
    public void unblockAndReactivate(String phoneNumber, OptOutDao optOutDao) {
        unblock(phoneNumber);
        if (optOutDao == null) return;
        String normalized = normalize(phoneNumber);
        if (normalized == null) return;
        try {
            optOutDao.deactivateOptOut(normalized).blockingAwait();
            Log.d("BlockListManager", "Synced unblock to opt-out DB: " + normalized);
        } catch (Exception e) {
            Log.w("BlockListManager", "Failed to sync unblock to opt-out DB", e);
        }
    }

    /**
     * One-time migration: reads all active opt-outs from {@link OptOutDao} and
     * adds them to the SharedPreferences block list.  Safe to call multiple times.
     *
     * <p>Must be called from a background thread.
     */
    public void syncFromOptOutDao(OptOutDao optOutDao) {
        if (optOutDao == null) return;
        try {
            java.util.List<String> numbers = optOutDao.getAllActiveOptOutNumbers().blockingGet();
            if (numbers == null || numbers.isEmpty()) return;
            Set<String> blocked = new HashSet<>(prefs.getStringSet(KEY_BLOCKED_SET, new HashSet<>()));
            boolean changed = false;
            for (String n : numbers) {
                if (n != null && !n.isEmpty() && blocked.add(n)) changed = true;
            }
            if (changed) prefs.edit().putStringSet(KEY_BLOCKED_SET, blocked).apply();
            Log.d("BlockListManager", "Synced " + numbers.size() + " opt-out(s) into block list");
        } catch (Exception e) {
            Log.w("BlockListManager", "syncFromOptOutDao failed", e);
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private String normalize(String phoneNumber) {
        String normalized = PhoneNumberUtils.normalizePhoneNumber(phoneNumber);
        if (normalized != null && !normalized.isEmpty()) {
            return normalized;
        }
        return phoneNumber != null ? phoneNumber.trim() : null;
    }
}
