package com.bulksms.smsmanager.data.utils;

import android.provider.Telephony;
import android.telephony.SmsMessage;
import android.util.Log;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Utility class for handling multipart SMS concatenation
 * Ensures that long messages are properly combined instead of being split
 */
public class MultipartSmsUtils {
    
    private static final String TAG = "MultipartSmsUtils";
    
    // Cache for incomplete multipart messages (thread-safe for concurrent access)
    private static final Map<String, List<SmsMessagePart>> multipartCache = new ConcurrentHashMap<>();
    
    // Maximum time to keep incomplete multipart messages in cache (5 minutes)
    private static final long CACHE_EXPIRY_MS = 5 * 60 * 1000;
    
    /**
     * Represents a part of a multipart SMS
     */
    private static class SmsMessagePart {
        final String address;
        final String body;
        final long timestamp;
        final int sequenceNumber;
        final int totalParts;
        final String messageIdentifier;
        final long receivedTime;
        
        SmsMessagePart(SmsMessage smsMessage) {
            this.address = smsMessage.getDisplayOriginatingAddress();
            this.body = smsMessage.getMessageBody();
            this.timestamp = smsMessage.getTimestampMillis();
            this.receivedTime = System.currentTimeMillis();
            
            // Simplified multipart detection - use timestamp and address as identifier
            this.sequenceNumber = 1; // Default sequence
            this.totalParts = 1; // Default to single part
            this.messageIdentifier = address + "_" + (timestamp / 1000); // Group by second
        }
        
        boolean isComplete() {
            return sequenceNumber == totalParts;
        }
    }
    
    /**
     * Process incoming SMS messages and concatenate multipart messages
     * Returns a list of complete messages ready for storage
     */
    public static List<CompleteSmsMessage> processIncomingMessages(SmsMessage[] messages) {
        List<CompleteSmsMessage> completeMessages = new ArrayList<>();
        
        if (messages == null || messages.length == 0) {
            return completeMessages;
        }
        
        // Group messages by sender first
        Map<String, List<SmsMessage>> messagesBySender = new HashMap<>();
        for (SmsMessage message : messages) {
            String address = message.getDisplayOriginatingAddress();
            if (address != null) {
                messagesBySender.computeIfAbsent(address, k -> new ArrayList<>()).add(message);
            }
        }
        
        // Process each sender's messages
        for (Map.Entry<String, List<SmsMessage>> entry : messagesBySender.entrySet()) {
            String sender = entry.getKey();
            List<SmsMessage> senderMessages = entry.getValue();
            
            if (senderMessages.size() == 1) {
                // Single message - add directly
                SmsMessage msg = senderMessages.get(0);
                completeMessages.add(new CompleteSmsMessage(
                    msg.getDisplayOriginatingAddress(),
                    msg.getMessageBody(),
                    msg.getTimestampMillis(),
                    false
                ));
            } else {
                // Multiple messages from same sender - check if they're parts of the same message
                CompleteSmsMessage concatenatedMessage = processMultipartMessage(senderMessages);
                if (concatenatedMessage != null) {
                    completeMessages.add(concatenatedMessage);
                } else {
                    // Not multipart, add as separate messages
                    for (SmsMessage msg : senderMessages) {
                        completeMessages.add(new CompleteSmsMessage(
                            msg.getDisplayOriginatingAddress(),
                            msg.getMessageBody(),
                            msg.getTimestampMillis(),
                            false
                        ));
                    }
                }
            }
        }
        
        // Clean up expired cache entries
        cleanupExpiredCache();
        
        return completeMessages;
    }
    
    /**
     * Process potential multipart messages from the same sender
     */
    private static CompleteSmsMessage processMultipartMessage(List<SmsMessage> messages) {
        if (messages.isEmpty()) {
            return null;
        }
        
        // Sort messages by timestamp
        messages.sort((a, b) -> Long.compare(a.getTimestampMillis(), b.getTimestampMillis()));
        
        // Check if messages are likely parts of the same multipart message
        // Heuristic: messages received within a short time window (2 seconds) from same sender
        long firstTimestamp = messages.get(0).getTimestampMillis();
        long lastTimestamp = messages.get(messages.size() - 1).getTimestampMillis();
        
        if (lastTimestamp - firstTimestamp > 2000) { // More than 2 seconds apart
            return null; // Likely separate messages
        }
        
        // Concatenate message bodies in order
        StringBuilder concatenatedBody = new StringBuilder();
        for (SmsMessage message : messages) {
            String body = message.getMessageBody();
            if (body != null) {
                concatenatedBody.append(body);
            }
        }
        
        // Use the timestamp of the first part
        long timestamp = messages.get(0).getTimestampMillis();
        String address = messages.get(0).getDisplayOriginatingAddress();
        
        Log.d(TAG, "Concatenated " + messages.size() + " message parts from " + address);
        
        return new CompleteSmsMessage(address, concatenatedBody.toString(), timestamp, true);
    }
    
    /**
     * Represents a complete SMS message (either single or concatenated multipart)
     */
    public static class CompleteSmsMessage {
        public final String address;
        public final String body;
        public final long timestamp;
        public final boolean wasMultipart;
        
        CompleteSmsMessage(String address, String body, long timestamp, boolean wasMultipart) {
            this.address = address;
            this.body = body;
            this.timestamp = timestamp;
            this.wasMultipart = wasMultipart;
        }
        
        @Override
        public String toString() {
            return "CompleteSmsMessage{" +
                    "address='" + address + '\'' +
                    ", body='" + (body != null ? (body.length() > 50 ? body.substring(0, 50) + "..." : body) : "") + '\'' +
                    ", timestamp=" + timestamp +
                    ", wasMultipart=" + wasMultipart +
                    '}';
        }
    }
    
    /**
     * Clean up expired entries from the multipart cache
     */
    private static void cleanupExpiredCache() {
        long currentTime = System.currentTimeMillis();
        multipartCache.entrySet().removeIf(entry -> {
            List<SmsMessagePart> parts = entry.getValue();
            if (parts.isEmpty()) {
                return true;
            }
            return currentTime - parts.get(0).receivedTime > CACHE_EXPIRY_MS;
        });
    }
    
    /**
     * Clear the multipart cache (useful for testing or memory management)
     */
    public static void clearCache() {
        multipartCache.clear();
        Log.d(TAG, "Multipart SMS cache cleared");
    }
    
    /**
     * Get cache statistics for debugging
     */
    public static String getCacheStats() {
        return "Multipart cache entries: " + multipartCache.size();
    }
}
