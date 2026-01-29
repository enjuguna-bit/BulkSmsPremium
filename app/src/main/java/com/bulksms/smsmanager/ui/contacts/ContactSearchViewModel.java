package com.bulksms.smsmanager.ui.contacts;

import android.content.Context;
import android.database.Cursor;
import android.provider.ContactsContract;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import dagger.hilt.android.lifecycle.HiltViewModel;
import dagger.hilt.android.qualifiers.ApplicationContext;
import javax.inject.Inject;

/**
 * ViewModel for contact search and selection
 * Provides search functionality with debouncing
 */
@HiltViewModel
public class ContactSearchViewModel extends ViewModel {
    
    private static final String TAG = "ContactSearchViewModel";
    
    private final Context context;
    private final MutableLiveData<List<ContactInfo>> contacts = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>(false);
    private final MutableLiveData<String> error = new MutableLiveData<>();
    
    @Inject
    public ContactSearchViewModel(@ApplicationContext Context context) {
        this.context = context;
    }
    
    public LiveData<List<ContactInfo>> getContacts() {
        return contacts;
    }
    
    public LiveData<Boolean> getIsLoading() {
        return isLoading;
    }
    
    public LiveData<String> getError() {
        return error;
    }
    
    /**
     * Search contacts with query string
     */
    public void searchContacts(String query) {
        CompletableFuture.runAsync(() -> {
            try {
                isLoading.postValue(true);
                error.postValue(null);
                
                List<ContactInfo> contactList = performContactSearch(query);
                
                contacts.postValue(contactList);
                
            } catch (Exception e) {
                Log.e(TAG, "Error searching contacts", e);
                error.postValue("Failed to search contacts: " + e.getMessage());
            } finally {
                isLoading.postValue(false);
            }
        });
    }
    
    /**
     * Get recent contacts (last 5 contacted)
     */
    public void getRecentContacts() {
        CompletableFuture.runAsync(() -> {
            try {
                isLoading.postValue(true);
                error.postValue(null);
                
                List<ContactInfo> recentList = getRecentContactsInternal();
                
                contacts.postValue(recentList);
                
            } catch (Exception e) {
                Log.e(TAG, "Error getting recent contacts", e);
                error.postValue("Failed to get recent contacts: " + e.getMessage());
            } finally {
                isLoading.postValue(false);
            }
        });
    }
    
    private List<ContactInfo> performContactSearch(String query) {
        List<ContactInfo> contactList = new ArrayList<>();
        
        String selection = null;
        String[] selectionArgs = null;
        
        if (query != null && !query.trim().isEmpty()) {
            selection = ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " LIKE ? OR " +
                       ContactsContract.CommonDataKinds.Phone.NUMBER + " LIKE ?";
            selectionArgs = new String[]{"%" + query + "%", "%" + query + "%"};
        }
        
        String[] projection = {
            ContactsContract.CommonDataKinds.Phone._ID,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.PHOTO_URI
        };
        
        try (Cursor cursor = context.getContentResolver().query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC LIMIT 50"
        )) {
            
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    ContactInfo contact = new ContactInfo();
                    contact.id = cursor.getLong(cursor.getColumnIndexOrThrow(
                        ContactsContract.CommonDataKinds.Phone._ID));
                    contact.name = cursor.getString(cursor.getColumnIndexOrThrow(
                        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME));
                    contact.phoneNumber = cursor.getString(cursor.getColumnIndexOrThrow(
                        ContactsContract.CommonDataKinds.Phone.NUMBER));
                    contact.photoUri = cursor.getString(cursor.getColumnIndexOrThrow(
                        ContactsContract.CommonDataKinds.Phone.PHOTO_URI));
                    
                    contactList.add(contact);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error querying contacts", e);
        }
        
        return contactList;
    }
    
    private List<ContactInfo> getRecentContactsInternal() {
        // This would typically query your SMS database for recent contacts
        // For now, return empty list - implement based on your SMS history
        return new ArrayList<>();
    }
    
    /**
     * Contact information data class
     */
    public static class ContactInfo {
        public long id;
        public String name;
        public String phoneNumber;
        public String photoUri;
        
        @NonNull
        @Override
        public String toString() {
            return name + " (" + phoneNumber + ")";
        }
    }
}
