package com.afriserve.smsmanager.ui.contacts;

import android.app.Application;
import android.database.Cursor;
import android.provider.ContactsContract;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.afriserve.smsmanager.ui.contacts.ContactSearchDialog.ContactInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;

/**
 * ViewModel for contact search functionality
 */
@HiltViewModel
public class ContactSearchViewModel extends AndroidViewModel {
    private static final String TAG = "ContactSearchViewModel";
    
    private final MutableLiveData<List<ContactInfo>> contacts = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>(false);
    private final MutableLiveData<String> error = new MutableLiveData<>();
    
    private List<ContactInfo> allContacts = new ArrayList<>();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    
    @Inject
    public ContactSearchViewModel(@NonNull Application application) {
        super(application);
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
     * Load all contacts from device
     */
    public void loadContacts() {
        isLoading.postValue(true);
        error.postValue(null);

        executor.execute(() -> {
            try {
                List<ContactInfo> contactList = new ArrayList<>();

                // Get phone contacts
                String[] projection = {
                    ContactsContract.CommonDataKinds.Phone._ID,
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER,
                    ContactsContract.CommonDataKinds.Phone.PHOTO_URI
                };

                Cursor phoneCursor = getApplication().getContentResolver().query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    projection,
                    null,
                    null,
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
                );

                if (phoneCursor != null) {
                    try {
                        int idIndex = phoneCursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone._ID);
                        int nameIndex = phoneCursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME);
                        int numberIndex = phoneCursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER);
                        int photoIndex = phoneCursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.PHOTO_URI);

                        while (phoneCursor.moveToNext()) {
                            String id = phoneCursor.getString(idIndex);
                            String name = phoneCursor.getString(nameIndex);
                            String phoneNumber = phoneCursor.getString(numberIndex);
                            String photoUri = photoCursorSafe(phoneCursor, photoIndex);

                        if (name != null && phoneNumber != null) {
                            ContactInfo contact = new ContactInfo(id, name, phoneNumber, null, photoUri);
                            contactList.add(contact);
                        }
                        }
                    } finally {
                        phoneCursor.close();
                    }
                }

                allContacts = contactList;
                contacts.postValue(contactList);
                Log.d(TAG, "Loaded " + contactList.size() + " contacts");
            } catch (Exception e) {
                Log.e(TAG, "Error loading contacts", e);
                error.postValue("Failed to load contacts: " + e.getMessage());
            } finally {
                isLoading.postValue(false);
            }
        });
    }

    private String photoCursorSafe(Cursor cursor, int index) {
        if (index < 0) return null;
        try {
            return cursor.getString(index);
        } catch (Exception ignored) {
            return null;
        }
    }
    
    /**
     * Search contacts by query
     */
    public void searchContacts(String query) {
        if (query == null || query.trim().isEmpty()) {
            contacts.postValue(allContacts);
            return;
        }
        
        String lowerQuery = query.toLowerCase().trim();
        List<ContactInfo> filteredContacts = new ArrayList<>();
        
        for (ContactInfo contact : allContacts) {
            boolean matches = contact.name.toLowerCase().contains(lowerQuery) ||
                           contact.phoneNumber.contains(query);
            
            if (matches) {
                filteredContacts.add(contact);
            }
        }
        
        contacts.postValue(filteredContacts);
    }
    
    /**
     * Refresh contacts
     */
    public void refreshContacts() {
        loadContacts();
    }
    
    /**
     * Clear contacts
     */
    public void clearContacts() {
        allContacts.clear();
        contacts.postValue(new ArrayList<>());
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        executor.shutdown();
    }
}
