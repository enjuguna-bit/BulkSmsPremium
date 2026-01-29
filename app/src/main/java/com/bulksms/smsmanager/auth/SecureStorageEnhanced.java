package com.bulksms.smsmanager.auth;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;

import androidx.annotation.NonNull;
import dagger.hilt.android.qualifiers.ApplicationContext;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import javax.inject.Inject;

import java.io.IOException;
import java.security.GeneralSecurityException;

/**
 * Enhanced secure storage for sensitive data
 */
public class SecureStorageEnhanced {
    private static final String PREFS_NAME = "secure_storage";
    private SharedPreferences encryptedPrefs;
    
    @Inject
    public SecureStorageEnhanced(@ApplicationContext @NonNull Context context) {
        try {
            MasterKey masterKey = new MasterKey.Builder(context)
                .setKeyGenParameterSpec(
                    new KeyGenParameterSpec.Builder(
                        MasterKey.DEFAULT_MASTER_KEY_ALIAS,
                        KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT
                    )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
                )
                .build();
                
            encryptedPrefs = EncryptedSharedPreferences.create(
                context,
                PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );
        } catch (GeneralSecurityException | IOException e) {
            // Fallback to regular SharedPreferences if encryption fails
            encryptedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        }
    }
    
    public void storeString(@NonNull String key, @NonNull String value) {
        encryptedPrefs.edit().putString(key, value).apply();
    }
    
    public String getString(@NonNull String key, @NonNull String defaultValue) {
        return encryptedPrefs.getString(key, defaultValue);
    }
    
    public void storeBoolean(@NonNull String key, boolean value) {
        encryptedPrefs.edit().putBoolean(key, value).apply();
    }
    
    public boolean getBoolean(@NonNull String key, boolean defaultValue) {
        return encryptedPrefs.getBoolean(key, defaultValue);
    }
    
    public void storeLong(@NonNull String key, long value) {
        encryptedPrefs.edit().putLong(key, value).apply();
    }
    
    public long getLong(@NonNull String key, long defaultValue) {
        return encryptedPrefs.getLong(key, defaultValue);
    }
    
    public void remove(@NonNull String key) {
        encryptedPrefs.edit().remove(key).apply();
    }
    
    public void clear() {
        encryptedPrefs.edit().clear().apply();
    }
}
