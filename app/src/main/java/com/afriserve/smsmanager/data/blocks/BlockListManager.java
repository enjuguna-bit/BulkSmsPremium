package com.afriserve.smsmanager.data.blocks;

import android.content.Context;
import android.content.SharedPreferences;

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

    private String normalize(String phoneNumber) {
        String normalized = PhoneNumberUtils.normalizePhoneNumber(phoneNumber);
        if (normalized != null && !normalized.isEmpty()) {
            return normalized;
        }
        return phoneNumber != null ? phoneNumber.trim() : null;
    }
}
