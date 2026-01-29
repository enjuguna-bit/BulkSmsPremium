package com.bulksms.smsmanager.data.parser;

import android.util.Log;
import java.util.HashMap;
import java.util.Map;

/**
 * Customer and Loan record parsers
 * Handles parsing of customer rows and loan records from Excel/CSV data
 */
public class RecordParser {
    private static final String TAG = "RecordParser";
    
    /**
     * Parse customer row from Excel/CSV data
     * Supports flexible column naming and data extraction
     */
    public static CustomerRecord parseCustomerRow(Map<String, String> row) {
        if (row == null) {
            return new CustomerRecord();
        }
        
        CustomerRecord customer = new CustomerRecord();
        
        // Extract name with flexible column matching
        customer.name = extractValue(row, "name", "Name", "FullNames", "FullName", "CustomerName", "ClientName");
        
        // Extract and normalize phone number
        String rawPhone = extractValue(row, "phone", "Phone", "PhoneNumber", "Mobile", "MobilePhone", "Contact");
        customer.phone = PhoneNormalizer.normalizePhone(rawPhone);
        
        // Extract amount with flexible column matching
        String rawAmount = extractValue(row, "amount", "Amount", "Balance", "Payment", "Cost", "Arrears");
        customer.amount = parseAmount(rawAmount);
        
        // Store raw data for reference
        customer.raw = new HashMap<>(row);
        
        Log.d(TAG, "Parsed customer: " + customer.toString());
        return customer;
    }
    
    /**
     * Parse loan record from Excel/CSV data
     * Supports flexible column naming and loan-specific fields
     */
    public static LoanRecord parseLoanRecord(Map<String, String> row) {
        if (row == null) {
            return new LoanRecord();
        }
        
        LoanRecord loan = new LoanRecord();
        
        // Extract account information
        loan.account = extractValue(row, "account", "Account", "AccountNumber", "AccountNo", "LoanAccount");
        
        // Extract customer name
        loan.name = extractValue(row, "name", "Name", "FullNames", "FullName", "CustomerName", "ClientName");
        
        // Extract and normalize phone number
        String rawPhone = extractValue(row, "phone", "Phone", "PhoneNumber", "Mobile", "MobilePhone", "Contact");
        loan.phone = PhoneNormalizer.normalizePhone(rawPhone);
        
        // Extract arrears amount
        String rawArrears = extractValue(row, "arrears", "Arrears", "ArrearsAmount", "Balance", "Outstanding");
        loan.arrears = parseAmount(rawArrears);
        
        // Extract last seen date
        String rawLastSeen = extractValue(row, "lastSeen", "LastSeen", "LastSeenDate", "LastPayment", "LastActivity");
        loan.lastSeen = DateParser.safeDate(rawLastSeen);
        
        // Store raw data for reference
        loan.raw = new HashMap<>(row);
        
        Log.d(TAG, "Parsed loan record: " + loan.toString());
        return loan;
    }
    
    /**
     * Extract value from row using multiple possible column names
     * Returns the first non-empty value found
     */
    private static String extractValue(Map<String, String> row, String... possibleKeys) {
        for (String key : possibleKeys) {
            String value = row.get(key);
            if (value != null && !value.trim().isEmpty()) {
                return value.trim();
            }
        }
        return "";
    }
    
    /**
     * Parse amount from string with currency symbols and formatting
     */
    private static double parseAmount(String amountStr) {
        if (amountStr == null || amountStr.trim().isEmpty()) {
            return 0.0;
        }
        
        // Clean the amount string
        String cleaned = amountStr.trim()
            .replaceAll("(?i)(Ksh|KES|ksh|kes|\\$|£|€)", "") // Remove currency symbols
            .replaceAll(",", "") // Remove thousand separators
            .trim();
        
        try {
            return Double.parseDouble(cleaned);
        } catch (NumberFormatException e) {
            Log.w(TAG, "Failed to parse amount: " + amountStr);
            return 0.0;
        }
    }
    
    /**
     * Validate customer record
     */
    public static boolean isValidCustomerRecord(CustomerRecord customer) {
        if (customer == null) return false;
        
        // Phone number is required for SMS sending
        if (customer.phone == null || customer.phone.isEmpty()) {
            return false;
        }
        
        // Validate phone number format
        if (!PhoneNormalizer.isValidForBulkSms(customer.phone)) {
            return false;
        }
        
        return true;
    }
    
    /**
     * Validate loan record
     */
    public static boolean isValidLoanRecord(LoanRecord loan) {
        if (loan == null) return false;
        
        // Account number or phone number should be present
        if ((loan.account == null || loan.account.isEmpty()) && 
            (loan.phone == null || loan.phone.isEmpty())) {
            return false;
        }
        
        // If phone is present, validate it
        if (loan.phone != null && !loan.phone.isEmpty()) {
            if (!PhoneNormalizer.isValidForBulkSms(loan.phone)) {
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * Convert customer record to recipient
     */
    public static com.bulksms.smsmanager.models.Recipient customerToRecipient(CustomerRecord customer) {
        if (customer == null) {
            return null;
        }
        
        Map<String, String> fields = new HashMap<>();
        if (customer.raw != null) {
            fields.putAll(customer.raw);
        }
        
        return new com.bulksms.smsmanager.models.Recipient(
            customer.name,
            customer.phone,
            customer.amount,
            false,
            fields
        );
    }
    
    /**
     * Convert loan record to recipient
     */
    public static com.bulksms.smsmanager.models.Recipient loanToRecipient(LoanRecord loan) {
        if (loan == null) {
            return null;
        }
        
        Map<String, String> fields = new HashMap<>();
        if (loan.raw != null) {
            fields.putAll(loan.raw);
        }
        
        // Add loan-specific fields
        if (loan.account != null && !loan.account.isEmpty()) {
            fields.put("account", loan.account);
        }
        if (loan.lastSeen != null && !loan.lastSeen.isEmpty()) {
            fields.put("lastSeen", loan.lastSeen);
        }
        
        return new com.bulksms.smsmanager.models.Recipient(
            loan.name,
            loan.phone,
            loan.arrears,
            false,
            fields
        );
    }
    
    /**
     * Get validation errors for customer record
     */
    public static String getCustomerValidationErrors(CustomerRecord customer) {
        if (customer == null) {
            return "Customer record is null";
        }
        
        StringBuilder errors = new StringBuilder();
        
        if (customer.phone == null || customer.phone.isEmpty()) {
            errors.append("Phone number is required. ");
        } else if (!PhoneNormalizer.isValidForBulkSms(customer.phone)) {
            errors.append("Invalid phone number format. ");
        }
        
        if (customer.name == null || customer.name.trim().isEmpty()) {
            errors.append("Customer name is recommended. ");
        }
        
        return errors.toString().trim();
    }
    
    /**
     * Get validation errors for loan record
     */
    public static String getLoanValidationErrors(LoanRecord loan) {
        if (loan == null) {
            return "Loan record is null";
        }
        
        StringBuilder errors = new StringBuilder();
        
        if ((loan.account == null || loan.account.isEmpty()) && 
            (loan.phone == null || loan.phone.isEmpty())) {
            errors.append("Either account number or phone number is required. ");
        }
        
        if (loan.phone != null && !loan.phone.isEmpty()) {
            if (!PhoneNormalizer.isValidForBulkSms(loan.phone)) {
                errors.append("Invalid phone number format. ");
            }
        }
        
        if (loan.name == null || loan.name.trim().isEmpty()) {
            errors.append("Customer name is recommended. ");
        }
        
        if (loan.arrears <= 0) {
            errors.append("Arrears amount should be positive. ");
        }
        
        return errors.toString().trim();
    }
    
    /**
     * Customer record data class
     */
    public static class CustomerRecord {
        public String name = "";
        public String phone = "";
        public double amount = 0.0;
        public Map<String, String> raw = new HashMap<>();
        
        @Override
        public String toString() {
            return "CustomerRecord{" +
                   "name='" + name + '\'' +
                   ", phone='" + phone + '\'' +
                   ", amount=" + amount +
                   '}';
        }
    }
    
    /**
     * Loan record data class
     */
    public static class LoanRecord {
        public String account = "";
        public String name = "";
        public String phone = "";
        public double arrears = 0.0;
        public String lastSeen = "";
        public Map<String, String> raw = new HashMap<>();
        
        @Override
        public String toString() {
            return "LoanRecord{" +
                   "account='" + account + '\'' +
                   ", name='" + name + '\'' +
                   ", phone='" + phone + '\'' +
                   ", arrears=" + arrears +
                   ", lastSeen='" + lastSeen + '\'' +
                   '}';
        }
    }
    
    /**
     * Batch validation results
     */
    public static class BatchValidationResult {
        public int totalRecords = 0;
        public int validRecords = 0;
        public int invalidRecords = 0;
        public java.util.List<String> errors = new java.util.ArrayList<>();
        
        public void addError(String error) {
            errors.add(error);
        }
        
        public double getValidPercentage() {
            return totalRecords > 0 ? (double) validRecords / totalRecords * 100 : 0;
        }
        
        @Override
        public String toString() {
            return "BatchValidationResult{" +
                   "totalRecords=" + totalRecords +
                   ", validRecords=" + validRecords +
                   ", invalidRecords=" + invalidRecords +
                   ", validPercentage=" + String.format("%.1f", getValidPercentage()) + "%" +
                   '}';
        }
    }
    
    /**
     * Validate batch of customer records
     */
    public static BatchValidationResult validateCustomerBatch(java.util.List<CustomerRecord> customers) {
        BatchValidationResult result = new BatchValidationResult();
        result.totalRecords = customers.size();
        
        for (int i = 0; i < customers.size(); i++) {
            CustomerRecord customer = customers.get(i);
            if (isValidCustomerRecord(customer)) {
                result.validRecords++;
            } else {
                result.invalidRecords++;
                String errors = getCustomerValidationErrors(customer);
                result.addError("Row " + (i + 1) + ": " + errors);
            }
        }
        
        return result;
    }
    
    /**
     * Validate batch of loan records
     */
    public static BatchValidationResult validateLoanBatch(java.util.List<LoanRecord> loans) {
        BatchValidationResult result = new BatchValidationResult();
        result.totalRecords = loans.size();
        
        for (int i = 0; i < loans.size(); i++) {
            LoanRecord loan = loans.get(i);
            if (isValidLoanRecord(loan)) {
                result.validRecords++;
            } else {
                result.invalidRecords++;
                String errors = getLoanValidationErrors(loan);
                result.addError("Row " + (i + 1) + ": " + errors);
            }
        }
        
        return result;
    }
}
