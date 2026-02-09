package com.afriserve.smsmanager.data.contacts;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;
import android.util.Log;

import dagger.hilt.android.qualifiers.ApplicationContext;
import javax.inject.Inject;
import javax.inject.Singleton;
import com.afriserve.smsmanager.data.utils.PhoneNumberUtils;
import com.afriserve.smsmanager.data.cache.EnhancedLruCache;

import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import io.reactivex.rxjava3.subjects.BehaviorSubject;
import java.util.concurrent.ConcurrentHashMap;
import android.util.LruCache;
import androidx.annotation.Nullable;

/**
 * Contact resolver for mapping phone numbers to contact names
 * Provides efficient contact lookup with caching
 */
@Singleton
public class ContactResolver {
    
    private static final String TAG = "ContactResolver";
    private static final int CACHE_SIZE = 100; // Cache up to 100 contacts
    private static final long CACHE_TTL_MS = 5 * 60 * 1000; // 5 minutes TTL
    private static final long NOT_FOUND_TTL_MS = 30 * 1000; // 30 seconds for not found
    private static final int MAX_RETRY_ATTEMPTS = 2; // Retry transient errors
    
    private final Context context;
    private final EnhancedLruCache<String, ContactCacheEntry> nameCache;
    private final EnhancedLruCache<String, Uri> photoCache;
    private final ConcurrentHashMap<String, BehaviorSubject<String>> nameSubjects;
    private final ConcurrentHashMap<String, BehaviorSubject<Uri>> photoSubjects;
    private final ContentResolver contentResolver;
    
    @Inject
    public ContactResolver(@ApplicationContext Context context) {
        this.context = context;
        this.contentResolver = context.getContentResolver();
        
        // Initialize enhanced caches with TTL support
        this.nameCache = new EnhancedLruCache<>(CACHE_SIZE, CACHE_TTL_MS);
        this.photoCache = new EnhancedLruCache<>(CACHE_SIZE, CACHE_TTL_MS);
        this.nameSubjects = new ConcurrentHashMap<>();
        this.photoSubjects = new ConcurrentHashMap<>();
    }
    
    /**
     * Get contact name for phone number asynchronously
     * @param phoneNumber Phone number to resolve
     * @return Single emitting contact name or phone number if not found
     */
    public Single<String> getContactNameAsync(String phoneNumber) {
        return Single.fromCallable(() -> getContactName(phoneNumber))
                .subscribeOn(Schedulers.io());
    }
    
    /**
     * Get contact name for phone number synchronously with enhanced caching and retry
     * @param phoneNumber Phone number to resolve
     * @return Contact name or phone number if not found
     */
    public String getContactName(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.trim().isEmpty()) {
            return "Unknown";
        }
        
        // Normalize phone number for consistent lookup
        String normalizedNumber = PhoneNumberUtils.normalizePhoneNumber(phoneNumber);
        if (normalizedNumber == null) {
            return phoneNumber;
        }
        
        // Check enhanced cache first
        ContactCacheEntry cachedEntry = nameCache.get(normalizedNumber);
            if (cachedEntry != null) {
                return cachedEntry.name;
            }
        
        // Query with retry logic
        String contactName = queryContactNameWithRetry(normalizedNumber);
        
        if (contactName.equals(normalizedNumber)) {
            // Cache "not found" result with shorter TTL
            nameCache.putNotFound(normalizedNumber, NOT_FOUND_TTL_MS);
        } else {
            // Cache successful result with normal TTL
            nameCache.put(normalizedNumber, new ContactCacheEntry(contactName, System.currentTimeMillis()));
        }
        
        return contactName;
    }
    
    /**
     * Query contact name with retry logic for transient errors
     */
    private String queryContactNameWithRetry(String phoneNumber) {
        Exception lastException = null;
        
        for (int attempt = 1; attempt <= MAX_RETRY_ATTEMPTS; attempt++) {
            try {
                String result = queryContactName(phoneNumber);
                if (attempt > 1) {
                    Log.d(TAG, "Contact query succeeded on attempt " + attempt + " for " + phoneNumber);
                }
                return result;
            } catch (Exception e) {
                lastException = e;
                
                // Check if this is a transient error worth retrying
                if (isTransientError(e) && attempt < MAX_RETRY_ATTEMPTS) {
                    Log.w(TAG, "Transient error on attempt " + attempt + " for " + phoneNumber + ": " + e.getMessage());
                    
                    // Exponential backoff
                    try {
                        Thread.sleep(100 * attempt); // 100ms, 200ms, 400ms...
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                } else {
                    Log.e(TAG, "Non-transient error or max retries exceeded for " + phoneNumber, e);
                    break;
                }
            }
        }
        
        // All attempts failed, return phone number
        Log.w(TAG, "All contact query attempts failed for " + phoneNumber, lastException);
        return phoneNumber;
    }
    
    /**
     * Check if error is transient and worth retrying
     */
    private boolean isTransientError(Exception e) {
        String message = e.getMessage();
        if (message == null) return false;
        
        String lowerMessage = message.toLowerCase();
        return lowerMessage.contains("timeout") ||
               lowerMessage.contains("network") ||
               lowerMessage.contains("temporary") ||
               lowerMessage.contains("busy") ||
               lowerMessage.contains("try again") ||
               e instanceof android.database.SQLException;
    }
    /**
     * Query contact name from Contacts provider
     */
    private String queryContactName(String phoneNumber) {
        try {
            String normalizedNumber = normalizePhoneNumber(phoneNumber);
            if (normalizedNumber == null || normalizedNumber.trim().isEmpty()) {
                Log.w(TAG, "Invalid phone number for contact lookup: " + phoneNumber);
                return phoneNumber;
            }
            
            Uri uri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(normalizedNumber)
            );
            
            String[] projection = {
                ContactsContract.PhoneLookup.DISPLAY_NAME,
                ContactsContract.PhoneLookup._ID
            };
            
            Cursor cursor = contentResolver.query(
                uri,
                projection,
                null,
                null,
                null
            );
            
            if (cursor != null) {
                try {
                    if (cursor.moveToFirst()) {
                        int nameColumn = cursor.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME);
                        if (nameColumn >= 0) {
                            String name = cursor.getString(nameColumn);
                            Log.d(TAG, "Found contact: " + name + " for number: " + phoneNumber);
                            return name;
                        }
                    }
                } finally {
                    cursor.close();
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to query contact for number: " + phoneNumber, e);
        }
        
        // Return phone number if no contact found
        return phoneNumber;
    }
    
    /**
     * Normalize phone number for consistent lookup
     * Uses centralized PhoneNumberUtils for consistency
     */
    private String normalizePhoneNumber(String phoneNumber) {
        return PhoneNumberUtils.normalizePhoneNumber(phoneNumber);
    }
    
    /**
     * Get contact photo URI for phone number
     * @param phoneNumber Phone number to resolve
     * @return URI of contact photo or null if not found
     */
    public Single<Uri> getContactPhotoUriAsync(String phoneNumber) {
        return Single.fromCallable(() -> getContactPhotoUri(phoneNumber))
                .subscribeOn(Schedulers.io());
    }
    
    public Uri getContactPhotoUri(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.trim().isEmpty()) {
            return null;
        }
        
        try {
            String normalizedNumber = normalizePhoneNumber(phoneNumber);
            if (normalizedNumber == null || normalizedNumber.trim().isEmpty()) {
                Log.w(TAG, "Invalid phone number for contact photo lookup: " + phoneNumber);
                return null;
            }
            
            Uri uri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(normalizedNumber)
            );
            
            String[] projection = {
                ContactsContract.PhoneLookup._ID,
                ContactsContract.PhoneLookup.PHOTO_URI
            };
            
            Cursor cursor = contentResolver.query(
                uri,
                projection,
                null,
                null,
                null
            );
            
            if (cursor != null) {
                try {
                    if (cursor.moveToFirst()) {
                        int photoColumn = cursor.getColumnIndex(ContactsContract.PhoneLookup.PHOTO_URI);
                        if (photoColumn >= 0) {
                            String photoUri = cursor.getString(photoColumn);
                            if (photoUri != null) {
                                return Uri.parse(photoUri);
                            }
                        }
                    }
                } finally {
                    cursor.close();
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to query contact photo for number: " + phoneNumber, e);
        }
        
        return null;
    }
    
    /**
     * Clear contact cache (useful for testing or when contacts are updated)
     */
    public void clearCache() {
        nameCache.clear();
        photoCache.clear();
        nameSubjects.clear();
        photoSubjects.clear();
        Log.d(TAG, "Contact cache cleared");
    }
    
    /**
     * Get cache size for debugging
     */
    public int getCacheSize() {
        return nameCache.size();
    }
    
    /**
     * Check if cache entry is expired
     */
    private boolean isCacheExpired(String key) {
        return false; // TTL handled by EnhancedLruCache
    }
    
    /**
     * Cache entry with timestamp for TTL support
     */
    private static class ContactCacheEntry {
        public final String name;
        public final long timestamp;
        
        ContactCacheEntry(String name, long timestamp) {
            this.name = name;
            this.timestamp = timestamp;
        }
    }
    
    /**
     * Get cache statistics for debugging
     */
    public String getCacheStats() {
        return String.format("NameCache: %d/%d entries, PhotoCache: %d/%d entries", 
            nameCache.size(), CACHE_SIZE, 
            photoCache.size(), CACHE_SIZE);
    }
}
