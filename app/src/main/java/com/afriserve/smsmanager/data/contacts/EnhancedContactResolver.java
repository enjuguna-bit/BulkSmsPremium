package com.afriserve.smsmanager.data.contacts;

import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import javax.inject.Inject;
import javax.inject.Singleton;

import com.afriserve.smsmanager.data.utils.PhoneNumberUtils;

import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * Enhanced contact resolver with partial matching and fallback strategies
 * Supports fuzzy matching, local contacts fallback, and advanced search capabilities
 */
@Singleton
public class EnhancedContactResolver {
    
    private static final String TAG = "EnhancedContactResolver";
    
    // Fallback strategies
    private static final int MIN_PARTIAL_MATCH_LENGTH = 3;
    private static final double SIMILARITY_THRESHOLD = 0.7;
    
    private final ContentResolver contentResolver;
    private final ConcurrentHashMap<String, LocalContact> localContactsCache;
    
    @Inject
    public EnhancedContactResolver(android.content.Context context) {
        this.contentResolver = context.getContentResolver();
        this.localContactsCache = new ConcurrentHashMap<>();
        
        // Load local contacts cache
        loadLocalContactsCache();
    }
    
    /**
     * Enhanced contact name resolution with fallback strategies
     */
    public Single<String> resolveContactName(String phoneNumber) {
        return Single.fromCallable(() -> {
            // Try exact match first
            String exactMatch = findExactMatch(phoneNumber);
            if (exactMatch != null && !exactMatch.equals(phoneNumber)) {
                return exactMatch;
            }
            
            // Try partial match
            String partialMatch = findPartialMatch(phoneNumber);
            if (partialMatch != null && !partialMatch.equals(phoneNumber)) {
                Log.d(TAG, "Found partial match for " + phoneNumber + ": " + partialMatch);
                return partialMatch;
            }
            
            // Try local contacts fallback
            String localMatch = findLocalContact(phoneNumber);
            if (localMatch != null && !localMatch.equals(phoneNumber)) {
                Log.d(TAG, "Found local contact for " + phoneNumber + ": " + localMatch);
                return localMatch;
            }
            
            // Try fuzzy matching
            String fuzzyMatch = findFuzzyMatch(phoneNumber);
            if (fuzzyMatch != null && !fuzzyMatch.equals(phoneNumber)) {
                Log.d(TAG, "Found fuzzy match for " + phoneNumber + ": " + fuzzyMatch);
                return fuzzyMatch;
            }
            
            // Return original phone number if no match found
            return phoneNumber;
            
        }).subscribeOn(Schedulers.io());
    }
    
    /**
     * Find exact contact match
     */
    private String findExactMatch(String phoneNumber) {
        String normalized = PhoneNumberUtils.normalizePhoneNumber(phoneNumber);
        if (normalized == null) return phoneNumber;
        
        try {
            Uri uri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(normalized)
            );
            
            String[] projection = {
                ContactsContract.PhoneLookup.DISPLAY_NAME,
                ContactsContract.PhoneLookup._ID
            };
            
            Cursor cursor = contentResolver.query(uri, projection, null, null, null);
            
            if (cursor != null) {
                try {
                    if (cursor.moveToFirst()) {
                        int nameColumn = cursor.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME);
                        return cursor.getString(nameColumn);
                    }
                } finally {
                    cursor.close();
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Exact match query failed for " + phoneNumber, e);
        }
        
        return phoneNumber;
    }
    
    /**
     * Find partial match using last N digits
     */
    private String findPartialMatch(String phoneNumber) {
        String normalized = PhoneNumberUtils.normalizePhoneNumber(phoneNumber);
        if (normalized == null || normalized.length() < MIN_PARTIAL_MATCH_LENGTH) {
            return phoneNumber;
        }
        
        // Try last 7 digits, then 6, then 5
        for (int length = Math.min(7, normalized.length()); length >= MIN_PARTIAL_MATCH_LENGTH; length--) {
            String lastDigits = PhoneNumberUtils.getLastNDigits(phoneNumber, length);
            if (lastDigits != null) {
                String match = searchByPartialNumber(lastDigits);
                if (match != null) {
                    return match;
                }
            }
        }
        
        return phoneNumber;
    }
    
    /**
     * Search contacts by partial phone number
     */
    private String searchByPartialNumber(String partialNumber) {
        try {
            // Use a LIKE query to find partial matches
            String selection = ContactsContract.CommonDataKinds.Phone.NUMBER + " LIKE ?";
            String[] selectionArgs = {"%" + partialNumber};
            
            Cursor cursor = contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                new String[]{ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME},
                selection,
                selectionArgs,
                null
            );
            
            if (cursor != null) {
                try {
                    if (cursor.moveToFirst()) {
                        int nameColumn = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME);
                        return cursor.getString(nameColumn);
                    }
                } finally {
                    cursor.close();
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Partial match search failed for " + partialNumber, e);
        }
        
        return null;
    }
    
    /**
     * Find contact in local cache
     */
    private String findLocalContact(String phoneNumber) {
        String normalized = PhoneNumberUtils.normalizePhoneNumber(phoneNumber);
        if (normalized == null) return phoneNumber;
        
        LocalContact localContact = localContactsCache.get(normalized);
        if (localContact != null) {
            return localContact.name;
        }
        
        // Try partial match in local cache
        for (LocalContact contact : localContactsCache.values()) {
            if (phoneNumberMatches(contact.phoneNumber, normalized)) {
                return contact.name;
            }
        }
        
        return phoneNumber;
    }
    
    /**
     * Find fuzzy match using similarity algorithms
     */
    private String findFuzzyMatch(String phoneNumber) {
        String normalized = PhoneNumberUtils.normalizePhoneNumber(phoneNumber);
        if (normalized == null || normalized.length() < MIN_PARTIAL_MATCH_LENGTH) {
            return phoneNumber;
        }
        
        try {
            // Get all contacts and find best match
            Cursor cursor = contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                new String[]{
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER
                },
                null,
                null,
                null
            );
            
            if (cursor != null) {
                try {
                    String bestMatch = null;
                    double bestScore = 0.0;
                    
                    while (cursor.moveToNext()) {
                        int nameIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME);
                        int phoneIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER);
                        if (nameIdx < 0 || phoneIdx < 0) continue;
                        String name = cursor.getString(nameIdx);
                        String contactPhone = cursor.getString(phoneIdx);
                        
                        String normalizedContact = PhoneNumberUtils.normalizePhoneNumber(contactPhone);
                        if (normalizedContact != null) {
                            double similarity = calculateSimilarity(normalized, normalizedContact);
                            if (similarity > bestScore && similarity >= SIMILARITY_THRESHOLD) {
                                bestScore = similarity;
                                bestMatch = name;
                            }
                        }
                    }
                    
                    if (bestMatch != null) {
                        return bestMatch;
                    }
                } finally {
                    cursor.close();
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Fuzzy match search failed for " + phoneNumber, e);
        }
        
        return phoneNumber;
    }
    
    /**
     * Calculate similarity between two phone numbers
     */
    private double calculateSimilarity(String phone1, String phone2) {
        if (phone1.equals(phone2)) {
            return 1.0;
        }
        
        // Remove non-digits for comparison
        String digits1 = phone1.replaceAll("[^0-9]", "");
        String digits2 = phone2.replaceAll("[^0-9]", "");
        
        // Check if they end with the same digits (most common matching pattern)
        int minLength = Math.min(digits1.length(), digits2.length());
        int matchingSuffix = 0;
        
        for (int i = 1; i <= minLength; i++) {
            if (digits1.charAt(digits1.length() - i) == digits2.charAt(digits2.length() - i)) {
                matchingSuffix++;
            } else {
                break;
            }
        }
        
        // Calculate similarity based on matching suffix
        double suffixSimilarity = (double) matchingSuffix / Math.max(digits1.length(), digits2.length());
        
        // Bonus for same length
        double lengthBonus = digits1.length() == digits2.length() ? 0.1 : 0.0;
        
        return Math.min(1.0, suffixSimilarity + lengthBonus);
    }
    
    /**
     * Check if phone numbers match (exact or partial)
     */
    private boolean phoneNumberMatches(String contactPhone, String searchPhone) {
        return PhoneNumberUtils.areSameNumber(contactPhone, searchPhone);
    }
    
    /**
     * Load local contacts cache for fallback
     */
    private void loadLocalContactsCache() {
        try {
            Cursor cursor = contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                new String[]{
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER
                },
                null,
                null,
                null
            );
            
            if (cursor != null) {
                try {
                    while (cursor.moveToNext()) {
                        int nameIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME);
                        int phoneIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER);
                        if (nameIdx < 0 || phoneIdx < 0) continue;
                        String name = cursor.getString(nameIdx);
                        String phone = cursor.getString(phoneIdx);

                        String normalized = PhoneNumberUtils.normalizePhoneNumber(phone);
                        if (normalized != null && name != null) {
                            localContactsCache.put(normalized, new LocalContact(name, phone));
                        }
                    }
                    Log.d(TAG, "Loaded " + localContactsCache.size() + " local contacts");
                } finally {
                    cursor.close();
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to load local contacts cache", e);
        }
    }
    
    /**
     * Refresh local contacts cache
     */
    public void refreshCache() {
        localContactsCache.clear();
        loadLocalContactsCache();
    }
    
    /**
     * Get cache statistics
     */
    public CacheStats getCacheStats() {
        return new CacheStats(localContactsCache.size());
    }
    
    /**
     * Local contact data class
     */
    private static class LocalContact {
        public final String name;
        public final String phoneNumber;
        
        LocalContact(String name, String phoneNumber) {
            this.name = name;
            this.phoneNumber = phoneNumber;
        }
    }
    
    /**
     * Cache statistics
     */
    public static class CacheStats {
        public final int localContactsCount;
        
        CacheStats(int localContactsCount) {
            this.localContactsCount = localContactsCount;
        }
        
        @Override
        public String toString() {
            return "CacheStats{localContacts=" + localContactsCount + "}";
        }
    }
}
