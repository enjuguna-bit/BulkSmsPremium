package com.bulksms.smsmanager.data.parser;

import android.util.Log;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Mobile Money Transaction Parser (SMS → Transaction)
 * Parses SMS messages to extract transaction information
 */
public class TransactionParser {
    private static final String TAG = "TransactionParser";
    
    // Transaction type detection patterns
    private static final Pattern CREDIT_PATTERN = Pattern.compile(
        "\\b(received|deposit|payment from|credited|inflow)\\b", 
        Pattern.CASE_INSENSITIVE
    );
    
    private static final Pattern DEBIT_PATTERN = Pattern.compile(
        "\\b(sent|paid to|withdraw|buy|airtime|purchase|debited|outflow)\\b", 
        Pattern.CASE_INSENSITIVE
    );
    
    // Amount extraction patterns
    private static final Pattern AMOUNT_PATTERN = Pattern.compile(
        "(?:KES|KSH|Ksh|ksh)?\\s*([\\d,]+(?:\\.\\d{1,2})?)"
    );
    
    // Phone number extraction pattern
    private static final Pattern PHONE_PATTERN = Pattern.compile(
        "\\+?\\d{7,13}"
    );
    
    // Customer name extraction patterns
    private static final Pattern NAME_FROM_PATTERN = Pattern.compile(
        "from\\s+([A-Za-z\\s]+?)(?=[\\s.,]|$)", 
        Pattern.CASE_INSENSITIVE
    );
    
    private static final Pattern NAME_TO_PATTERN = Pattern.compile(
        "to\\s+([A-Za-z\\s]+?)(?=[\\s.,]|$)", 
        Pattern.CASE_INSENSITIVE
    );
    
    private static final Pattern NAME_BY_PATTERN = Pattern.compile(
        "by\\s+([A-Za-z\\s]+?)(?=[\\s.,]|$)", 
        Pattern.CASE_INSENSITIVE
    );
    
    // Date extraction patterns
    private static final Pattern DATE_PATTERN = Pattern.compile(
        "(\\d{4}-\\d{2}-\\d{2}|\\d{1,2}/\\d{1,2}/\\d{2,4}|\\d{1,2}\\s+\\w+\\s+\\d{4})"
    );
    
    /**
     * Parse mobile money transaction from SMS message
     */
    public static Transaction parseMobileMoneyTransaction(String message) {
        try {
            if (message == null) {
                return null;
            }
            
            String cleanMsg = message.replace("\u00A0", " ").trim();
            
            // Detect transaction type
            boolean isCredit = CREDIT_PATTERN.matcher(cleanMsg).find();
            boolean isDebit = DEBIT_PATTERN.matcher(cleanMsg).find();
            
            ParsedPaymentType type;
            if (isCredit) {
                type = ParsedPaymentType.INCOMING;
            } else if (isDebit) {
                type = ParsedPaymentType.OUTGOING;
            } else {
                type = ParsedPaymentType.UNKNOWN;
            }
            
            // Extract amount
            double amount = 0;
            Matcher amountMatcher = AMOUNT_PATTERN.matcher(cleanMsg);
            if (amountMatcher.find()) {
                String amountStr = amountMatcher.group(1).replace(",", "");
                try {
                    amount = Double.parseDouble(amountStr);
                } catch (NumberFormatException e) {
                    Log.w(TAG, "Failed to parse amount: " + amountStr);
                }
            }
            
            // Extract phone number
            String phoneNumber = "";
            Matcher phoneMatcher = PHONE_PATTERN.matcher(cleanMsg);
            if (phoneMatcher.find()) {
                String phone = phoneMatcher.group();
                phoneNumber = PhoneNormalizer.normalizePhone(phone);
            }
            
            // Extract customer name
            String customerName = "Unknown";
            Matcher nameMatcher = NAME_FROM_PATTERN.matcher(cleanMsg);
            if (!nameMatcher.find()) {
                nameMatcher = NAME_TO_PATTERN.matcher(cleanMsg);
            }
            if (!nameMatcher.find()) {
                nameMatcher = NAME_BY_PATTERN.matcher(cleanMsg);
            }
            if (nameMatcher.find()) {
                customerName = nameMatcher.group(1).trim();
            }
            
            // Extract date
            String dateStr = "";
            Matcher dateMatcher = DATE_PATTERN.matcher(cleanMsg);
            if (dateMatcher.find()) {
                dateStr = DateParser.safeDate(dateMatcher.group(0));
            } else {
                dateStr = DateParser.safeDate(new java.util.Date().toString());
            }
            
            // Reject totally invalid SMS
            if (phoneNumber.isEmpty() && amount == 0) {
                return null;
            }
            
            Transaction transaction = new Transaction();
            transaction.id = (phoneNumber.isEmpty() ? "TX" : phoneNumber) + "-" + System.currentTimeMillis();
            transaction.customerName = customerName;
            transaction.phoneNumber = phoneNumber;
            transaction.amount = amount;
            transaction.date = dateStr;
            transaction.type = type;
            transaction.rawMessage = message;
            
            Log.d(TAG, "Parsed transaction: " + transaction.toString());
            return transaction;
            
        } catch (Exception e) {
            Log.e(TAG, "SMS parse error", e);
            return null;
        }
    }
    
    /**
     * Transaction data class
     */
    public static class Transaction {
        public String id;
        public String customerName;
        public String phoneNumber;
        public double amount;
        public String date;
        public ParsedPaymentType type;
        public String rawMessage;
        
        @Override
        public String toString() {
            return "Transaction{" +
                   "id='" + id + '\'' +
                   ", customerName='" + customerName + '\'' +
                   ", phoneNumber='" + phoneNumber + '\'' +
                   ", amount=" + amount +
                   ", date='" + date + '\'' +
                   ", type=" + type +
                   '}';
        }
    }
    
    /**
     * Payment type enum
     */
    public enum ParsedPaymentType {
        INCOMING,
        OUTGOING,
        UNKNOWN
    }
    
    /**
     * Validate transaction data
     */
    public static boolean isValidTransaction(Transaction transaction) {
        if (transaction == null) return false;
        
        // Must have either phone number or amount
        if (transaction.phoneNumber.isEmpty() && transaction.amount == 0) {
            return false;
        }
        
        // Phone number should be valid if present
        if (!transaction.phoneNumber.isEmpty()) {
            return PhoneNormalizer.isValidForBulkSms(transaction.phoneNumber);
        }
        
        return true;
    }
    
    /**
     * Get transaction summary string
     */
    public static String getTransactionSummary(Transaction transaction) {
        if (transaction == null) return "Invalid Transaction";
        
        StringBuilder summary = new StringBuilder();
        
        // Transaction type
        switch (transaction.type) {
            case INCOMING:
                summary.append("Received ");
                break;
            case OUTGOING:
                summary.append("Sent ");
                break;
            default:
                summary.append("Transaction ");
                break;
        }
        
        // Amount
        if (transaction.amount > 0) {
            summary.append("KES ").append(String.format("%,.2f", transaction.amount));
        }
        
        // Customer name
        if (!transaction.customerName.equals("Unknown")) {
            summary.append(" from ").append(transaction.customerName);
        }
        
        // Phone number
        if (!transaction.phoneNumber.isEmpty()) {
            summary.append(" (").append(PhoneNormalizer.formatForDisplay(transaction.phoneNumber)).append(")");
        }
        
        return summary.toString();
    }
    
    /**
     * Extract transaction keywords for search
     */
    public static String[] extractKeywords(Transaction transaction) {
        if (transaction == null) return new String[0];
        
        String message = transaction.rawMessage.toLowerCase();
        java.util.List<String> keywords = new java.util.ArrayList<>();
        
        // Add transaction type keywords
        if (transaction.type == ParsedPaymentType.INCOMING) {
            keywords.add("received");
            keywords.add("deposit");
            keywords.add("credited");
        } else if (transaction.type == ParsedPaymentType.OUTGOING) {
            keywords.add("sent");
            keywords.add("paid");
            keywords.add("withdraw");
            keywords.add("debited");
        }
        
        // Add amount keywords
        if (transaction.amount > 0) {
            keywords.add(String.valueOf((int) transaction.amount));
        }
        
        // Add customer name keywords
        if (!transaction.customerName.equals("Unknown")) {
            String[] nameParts = transaction.customerName.split("\\s+");
            for (String part : nameParts) {
                if (part.length() > 2) {
                    keywords.add(part.toLowerCase());
                }
            }
        }
        
        // Add phone number keywords
        if (!transaction.phoneNumber.isEmpty()) {
            keywords.add(transaction.phoneNumber.replace("+", ""));
            keywords.add(transaction.phoneNumber.replace("+254", "0"));
        }
        
        return keywords.toArray(new String[0]);
    }
}
