package com.bulksms.smsmanager.billing;

/**
 * Result class for billing operations
 */
public abstract class BillingResult {
    
    private BillingResult() {}
    
    public static class Success extends BillingResult {
        public final String message;
        
        public Success(String message) {
            this.message = message;
        }
    }
    
    public static class Error extends BillingResult {
        public final String message;
        
        public Error(String message) {
            this.message = message;
        }
    }
}