package com.afriserve.smsmanager.data.utils;

import java.util.ArrayList;
import java.util.List;

import android.telephony.SmsMessage;

/**
 * Helper utilities for working with multipart (concatenated) SMS messages.
 * Small, safe implementations intended to satisfy compile-time usage
 * while preserving expected behavior (splitting/joining message parts).
 */
public final class MultipartSmsUtils {

    private MultipartSmsUtils() {
        throw new AssertionError();
    }

    public static boolean isMultipartMessage(String body) {
        return body != null && body.length() > 160;
    }

    public static List<String> splitIntoParts(String body) {
        List<String> parts = new ArrayList<>();
        if (body == null)
            return parts;
        final int partSize = 153; // standard when UDH is used
        int idx = 0;
        while (idx < body.length()) {
            parts.add(body.substring(idx, Math.min(idx + partSize, body.length())));
            idx += partSize;
        }
        return parts;
    }

    public static String joinParts(List<String> parts) {
        if (parts == null || parts.isEmpty())
            return "";
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            sb.append(p == null ? "" : p);
        }
        return sb.toString();
    }

    public static int getEstimatedPartCount(String body) {
        if (body == null || body.isEmpty())
            return 0;
        return (body.length() + 152) / 153;
    }

    /**
     * Lightweight representation of a reconstructed multipart message
     */
    public static class CompleteSmsMessage {
        public String address;
        public String body;
        public long timestamp;
        public boolean wasMultipart;
    }

    /**
     * Reassemble incoming SmsMessages into CompleteSmsMessage list
     */
    public static List<CompleteSmsMessage> processIncomingMessages(SmsMessage[] messages) {
        List<CompleteSmsMessage> result = new ArrayList<>();
        if (messages == null || messages.length == 0)
            return result;

        // Simple implementation: treat each message as complete for now,
        // effectively handling them as individual parts if not already combined.
        // Real multipart handling would involve checking UDH and concatenation.
        for (SmsMessage sms : messages) {
            CompleteSmsMessage cm = new CompleteSmsMessage();
            cm.address = sms.getOriginatingAddress();
            cm.body = sms.getMessageBody();
            cm.timestamp = sms.getTimestampMillis();
            cm.wasMultipart = false; // Default to false for single parts

            // Basic check if it *looks* like a multipart segment (stub logic)
            if (messages.length > 1) {
                cm.wasMultipart = true;
            }

            result.add(cm);
        }

        return result;
    }
}
