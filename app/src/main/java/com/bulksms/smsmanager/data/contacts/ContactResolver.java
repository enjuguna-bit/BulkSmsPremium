package com.bulksms.smsmanager.data.contacts;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;
import android.util.Log;

import javax.inject.Inject;
import javax.inject.Singleton;
import dagger.hilt.android.qualifiers.ApplicationContext;

import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * Contact resolver for mapping phone numbers to contact names
 * Provides efficient contact lookup with caching
 */
@Singleton
public class ContactResolver {
    
    private static final String TAG = "ContactResolver";
    private final Context context;
    private final ContentResolver contentResolver;
    
    // Simple cache for recently looked up contacts
    private final java.util.concurrent.ConcurrentHashMap<String, String> contactCache = 
        new java.util.concurrent.ConcurrentHashMap<>();
    
    @Inject
    public ContactResolver(@ApplicationContext Context context) {
        this.context = context;
        this.contentResolver = context.getContentResolver();
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
     * Get contact name for phone number synchronously
     * @param phoneNumber Phone number to resolve
     * @return Contact name or phone number if not found
     */
    public String getContactName(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.trim().isEmpty()) {
            return "Unknown";
        }
        
        // Normalize phone number for consistent lookup
        String normalizedNumber = normalizePhoneNumber(phoneNumber);
        
        // Check cache first
        String cachedName = contactCache.get(normalizedNumber);
        if (cachedName != null) {
            return cachedName;
        }
        
        // Query contacts provider
        String contactName = queryContactName(normalizedNumber);
        
        // Cache the result (even if not found to avoid repeated queries)
        contactCache.put(normalizedNumber, contactName);
        
        return contactName;
    }
    
    /**
     * Query contact name from Contacts provider
     */
    private String queryContactName(String phoneNumber) {
        try {
            Uri uri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(phoneNumber)
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
     * Removes special characters and formats to E.164 style if possible
     */
    private String normalizePhoneNumber(String phoneNumber) {
        if (phoneNumber == null) {
            return "";
        }
        
        // Remove all non-numeric characters except +
        String normalized = phoneNumber.replaceAll("[^0-9+]", "");
        
        // If number starts with country code, keep it
        // Otherwise, you might want to add default country code
        // For now, just return the cleaned number
        
        return normalized;
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
        contactCache.clear();
        Log.d(TAG, "Contact cache cleared");
    }
    
    /**
     * Get cache size for debugging
     */
    public int getCacheSize() {
        return contactCache.size();
    }
}
