package com.afriserve.smsmanager.data.compliance;

import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.afriserve.smsmanager.data.dao.OptOutDao;
import com.afriserve.smsmanager.data.entity.OptOutEntity;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import javax.inject.Inject;
import javax.inject.Singleton;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * Compliance manager for DND/NDNC registry and opt-out management
 * Ensures SMS marketing compliance with regulations
 */
@Singleton
public class ComplianceManager {
    
    private static final String TAG = "ComplianceManager";
    
    // Phone number patterns for validation
    private static final Pattern INTERNATIONAL_PATTERN = Pattern.compile("^\\+[1-9]\\d{1,14}$");
    private static final Pattern US_PATTERN = Pattern.compile("^\\d{10}$");
    private static final Pattern UK_PATTERN = Pattern.compile("^\\d{10,11}$");
    private static final Pattern INDIA_PATTERN = Pattern.compile("^\\d{10}$");
    
    // DND registry cache (in production would be synced with official registries)
    private final Set<String> dndRegistry = new HashSet<>();
    private final Set<String> ndncRegistry = new HashSet<>();
    
    // Internal opt-out management
    private final OptOutDao optOutDao;
    
    // Compliance statistics
    private final MutableLiveData<ComplianceStats> _complianceStats = new MutableLiveData<>();
    public final LiveData<ComplianceStats> complianceStats = _complianceStats;
    
    @Inject
    public ComplianceManager(OptOutDao optOutDao) {
        this.optOutDao = optOutDao;
        initializeDndRegistries();
        loadOptOutList();
    }
    
    /**
     * Check if a phone number is compliant for sending
     */
    public Single<ComplianceResult> checkCompliance(String phoneNumber, String campaignType) {
        return Single.fromCallable(() -> {
            ComplianceResult result = new ComplianceResult();
            
            // Validate phone number format
            if (!isValidPhoneNumber(phoneNumber)) {
                result.setCompliant(false);
                result.setReason("Invalid phone number format");
                result.setViolationType(ComplianceViolation.INVALID_FORMAT);
                return result;
            }
            
            String normalizedNumber = normalizePhoneNumber(phoneNumber);
            
            // Check DND registry
            if (isInDndRegistry(normalizedNumber)) {
                result.setCompliant(false);
                result.setReason("Number is in DND registry");
                result.setViolationType(ComplianceViolation.DND_VIOLATION);
                return result;
            }
            
            // Check NDNC registry (India specific)
            if (isInNdncRegistry(normalizedNumber)) {
                result.setCompliant(false);
                result.setReason("Number is in NDNC registry");
                result.setViolationType(ComplianceViolation.NDNC_VIOLATION);
                return result;
            }
            
            // Check internal opt-out list
            if (isOptedOut(normalizedNumber)) {
                result.setCompliant(false);
                result.setReason("Number has opted out");
                result.setViolationType(ComplianceViolation.OPTED_OUT);
                return result;
            }
            
            // Check time-based restrictions
            if (!isAllowedSendTime(campaignType)) {
                result.setCompliant(false);
                result.setReason("Sending not allowed at this time");
                result.setViolationType(ComplianceViolation.TIME_RESTRICTION);
                return result;
            }
            
            result.setCompliant(true);
            return result;
            
        }).subscribeOn(Schedulers.io());
    }
    
    /**
     * Add number to opt-out list
     */
    public Completable addToOptOut(String phoneNumber, String reason) {
        return Completable.fromAction(() -> {
            try {
                String normalizedNumber = normalizePhoneNumber(phoneNumber);
                
                OptOutEntity optOut = new OptOutEntity();
                optOut.phoneNumber = normalizedNumber;
                optOut.reason = reason;
                optOut.optOutTime = System.currentTimeMillis();
                optOut.source = "USER_REQUEST";
                
                optOutDao.insertOptOut(optOut).blockingAwait();
                
                Log.d(TAG, "Added number to opt-out list: " + normalizedNumber);
                
            } catch (Exception e) {
                Log.e(TAG, "Failed to add to opt-out list", e);
                throw new RuntimeException(e);
            }
        }).subscribeOn(Schedulers.io());
    }
    
    /**
     * Remove number from opt-out list (admin function)
     */
    public Completable removeFromOptOut(String phoneNumber) {
        return Completable.fromAction(() -> {
            try {
                String normalizedNumber = normalizePhoneNumber(phoneNumber);
                optOutDao.deleteOptOutByPhone(normalizedNumber).blockingAwait();
                
                Log.d(TAG, "Removed number from opt-out list: " + normalizedNumber);
                
            } catch (Exception e) {
                Log.e(TAG, "Failed to remove from opt-out list", e);
                throw new RuntimeException(e);
            }
        }).subscribeOn(Schedulers.io());
    }
    
    /**
     * Validate phone number format
     */
    public boolean isValidPhoneNumber(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.trim().isEmpty()) {
            return false;
        }
        
        String normalized = phoneNumber.replaceAll("[^0-9+]", "");
        
        // Check international format
        if (normalized.startsWith("+")) {
            return INTERNATIONAL_PATTERN.matcher(normalized).matches();
        }
        
        // Check country-specific formats
        return US_PATTERN.matcher(normalized).matches() ||
               UK_PATTERN.matcher(normalized).matches() ||
               INDIA_PATTERN.matcher(normalized).matches();
    }
    
    /**
     * Normalize phone number to standard format
     */
    public String normalizePhoneNumber(String phoneNumber) {
        if (phoneNumber == null) return "";
        
        // Remove all non-numeric characters except +
        String normalized = phoneNumber.replaceAll("[^0-9+]", "");
        
        // Convert to international format if needed
        if (!normalized.startsWith("+")) {
            // Assume US number if no country code
            if (normalized.length() == 10) {
                normalized = "+1" + normalized;
            } else if (normalized.length() == 11 && normalized.startsWith("1")) {
                normalized = "+" + normalized;
            }
        }
        
        return normalized;
    }
    
    /**
     * Check if sending is allowed at current time
     */
    private boolean isAllowedSendTime(String campaignType) {
        int hour = java.time.LocalTime.now().getHour();
        
        // General marketing messages: 9 AM - 9 PM
        if ("MARKETING".equals(campaignType)) {
            return hour >= 9 && hour < 21;
        }
        
        // Transactional messages: 8 AM - 10 PM  
        if ("TRANSACTIONAL".equals(campaignType)) {
            return hour >= 8 && hour < 22;
        }
        
        // Emergency messages: any time
        if ("EMERGENCY".equals(campaignType)) {
            return true;
        }
        
        // Default to marketing restrictions
        return hour >= 9 && hour < 21;
    }
    
    /**
     * Check if number is in DND registry
     */
    private boolean isInDndRegistry(String normalizedNumber) {
        return dndRegistry.contains(normalizedNumber);
    }
    
    /**
     * Check if number is in NDNC registry (India)
     */
    private boolean isInNdncRegistry(String normalizedNumber) {
        return ndncRegistry.contains(normalizedNumber);
    }
    
    /**
     * Check if number has opted out internally
     */
    private boolean isOptedOut(String normalizedNumber) {
        try {
            OptOutEntity optOut = optOutDao.getOptOutByPhone(normalizedNumber).blockingGet();
            return optOut != null;
        } catch (Exception e) {
            Log.w(TAG, "Failed to check opt-out status", e);
            return false;
        }
    }
    
    /**
     * Initialize DND registries
     * In production, this would sync with official registries
     */
    private void initializeDndRegistries() {
        // Sample DND numbers for demonstration
        // In production, this would be loaded from official sources
        dndRegistry.add("+12025551234");
        dndRegistry.add("+12025555678");
        dndRegistry.add("+12025559012");
        
        // Sample NDNC numbers for India
        ndncRegistry.add("+919876543210");
        ndncRegistry.add("+919876543211");
        ndncRegistry.add("+919876543212");
        
        Log.d(TAG, "DND registries initialized with " + dndRegistry.size() + " DND and " + 
              ndncRegistry.size() + " NDNC numbers");
    }
    
    /**
     * Load internal opt-out list
     */
    private void loadOptOutList() {
        try {
            int optOutCount = optOutDao.getOptOutCount().blockingGet();
            Log.d(TAG, "Loaded " + optOutCount + " opted-out numbers");
            
            updateComplianceStats();
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to load opt-out list", e);
        }
    }
    
    /**
     * Update compliance statistics
     */
    private void updateComplianceStats() {
        try {
            int dndCount = dndRegistry.size();
            int ndncCount = ndncRegistry.size();
            int optOutCount = optOutDao.getOptOutCount().blockingGet();
            
            ComplianceStats stats = new ComplianceStats(dndCount, ndncCount, optOutCount);
            _complianceStats.postValue(stats);
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to update compliance stats", e);
        }
    }
    
    /**
     * Get compliance statistics
     */
    public Single<ComplianceStats> getComplianceStatistics() {
        return Single.fromCallable(() -> {
            int dndCount = dndRegistry.size();
            int ndncCount = ndncRegistry.size();
            int optOutCount = optOutDao.getOptOutCount().blockingGet();
            
            return new ComplianceStats(dndCount, ndncCount, optOutCount);
        }).subscribeOn(Schedulers.io());
    }
    
    /**
     * Compliance result data class
     */
    public static class ComplianceResult {
        private boolean compliant;
        private String reason;
        private ComplianceViolation violationType;
        
        public ComplianceResult() {
            this.compliant = true;
        }
        
        // Getters and setters
        public boolean isCompliant() { return compliant; }
        public void setCompliant(boolean compliant) { this.compliant = compliant; }
        
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
        
        public ComplianceViolation getViolationType() { return violationType; }
        public void setViolationType(ComplianceViolation violationType) { this.violationType = violationType; }
    }
    
    /**
     * Compliance violation types
     */
    public enum ComplianceViolation {
        INVALID_FORMAT,
        DND_VIOLATION,
        NDNC_VIOLATION,
        OPTED_OUT,
        TIME_RESTRICTION,
        CONTENT_VIOLATION
    }
    
    /**
     * Compliance statistics
     */
    public static class ComplianceStats {
        public final int dndCount;
        public final int ndncCount;
        public final int optOutCount;
        public final int totalBlocked;
        
        public ComplianceStats(int dndCount, int ndncCount, int optOutCount) {
            this.dndCount = dndCount;
            this.ndncCount = ndncCount;
            this.optOutCount = optOutCount;
            this.totalBlocked = dndCount + ndncCount + optOutCount;
        }
        
        @Override
        public String toString() {
            return "ComplianceStats{" +
                    "dndCount=" + dndCount +
                    ", ndncCount=" + ndncCount +
                    ", optOutCount=" + optOutCount +
                    ", totalBlocked=" + totalBlocked +
                    '}';
        }
    }
}
