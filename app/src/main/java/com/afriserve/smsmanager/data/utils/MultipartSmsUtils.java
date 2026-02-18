package com.afriserve.smsmanager.data.utils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

    private static final long CACHE_EXPIRY_MS = 2 * 60 * 1000L;

    private static final Map<String, ConcatGroup> multipartCache = new HashMap<>();

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

    private static final class ConcatInfo {
        final int ref;
        final int total;
        final int seq;

        ConcatInfo(int ref, int total, int seq) {
            this.ref = ref;
            this.total = total;
            this.seq = seq;
        }
    }

    private static final class ConcatPart {
        final int seq;
        final String body;
        final long timestamp;
        final long receivedAt;

        ConcatPart(int seq, String body, long timestamp, long receivedAt) {
            this.seq = seq;
            this.body = body;
            this.timestamp = timestamp;
            this.receivedAt = receivedAt;
        }
    }

    private static final class ConcatGroup {
        final String address;
        final int totalParts;
        final Map<Integer, ConcatPart> parts = new HashMap<>();
        long firstReceivedAt;
        long firstTimestamp;

        ConcatGroup(String address, int totalParts, long receivedAt, long timestamp) {
            this.address = address;
            this.totalParts = totalParts;
            this.firstReceivedAt = receivedAt;
            this.firstTimestamp = timestamp;
        }
    }

    /**
     * Reassemble incoming SmsMessages into CompleteSmsMessage list
     */
    public static List<CompleteSmsMessage> processIncomingMessages(SmsMessage[] messages) {
        List<CompleteSmsMessage> result = new ArrayList<>();
        if (messages == null || messages.length == 0) {
            return result;
        }

        long now = System.currentTimeMillis();
        List<SmsMessage> nonConcat = new ArrayList<>();

        synchronized (multipartCache) {
            for (SmsMessage sms : messages) {
                if (sms == null) {
                    continue;
                }
                ConcatInfo info = extractConcatInfo(sms);
                if (info != null && info.total > 1) {
                    cacheConcatPart(sms, info, now);
                } else {
                    nonConcat.add(sms);
                }
            }

            flushCompletedConcatGroups(result, now);
            flushExpiredConcatGroups(result, now);
        }

        // Fallback grouping for non-concatenated messages in this broadcast.
        result.addAll(groupBySender(nonConcat));

        return result;
    }

    private static List<CompleteSmsMessage> groupBySender(List<SmsMessage> messages) {
        List<CompleteSmsMessage> result = new ArrayList<>();
        if (messages == null || messages.isEmpty()) {
            return result;
        }

        Map<String, List<SmsMessage>> bySender = new LinkedHashMap<>();
        for (SmsMessage sms : messages) {
            if (sms == null) {
                continue;
            }
            String address = sms.getDisplayOriginatingAddress();
            if (address == null || address.trim().isEmpty()) {
                address = sms.getOriginatingAddress();
            }
            if (address == null) {
                address = "";
            }
            bySender.computeIfAbsent(address, k -> new ArrayList<>()).add(sms);
        }

        for (Map.Entry<String, List<SmsMessage>> entry : bySender.entrySet()) {
            List<SmsMessage> parts = entry.getValue();
            if (parts.isEmpty()) {
                continue;
            }
            if (parts.size() == 1) {
                SmsMessage sms = parts.get(0);
                CompleteSmsMessage cm = new CompleteSmsMessage();
                cm.address = entry.getKey();
                cm.body = sms.getMessageBody();
                cm.timestamp = sms.getTimestampMillis();
                cm.wasMultipart = false;
                result.add(cm);
                continue;
            }

            StringBuilder body = new StringBuilder();
            for (SmsMessage sms : parts) {
                String part = sms.getMessageBody();
                if (part != null) {
                    body.append(part);
                }
            }

            CompleteSmsMessage cm = new CompleteSmsMessage();
            cm.address = entry.getKey();
            cm.body = body.toString();
            cm.timestamp = parts.get(0).getTimestampMillis();
            cm.wasMultipart = true;
            result.add(cm);
        }

        return result;
    }

    private static void cacheConcatPart(SmsMessage sms, ConcatInfo info, long now) {
        String address = sms.getDisplayOriginatingAddress();
        if (address == null || address.trim().isEmpty()) {
            address = sms.getOriginatingAddress();
        }
        if (address == null) {
            address = "";
        }
        String key = address + "|" + info.ref + "|" + info.total;

        ConcatGroup group = multipartCache.get(key);
        if (group == null) {
            group = new ConcatGroup(address, info.total, now, sms.getTimestampMillis());
            multipartCache.put(key, group);
        }

        group.firstReceivedAt = Math.min(group.firstReceivedAt, now);
        if (group.firstTimestamp == 0) {
            group.firstTimestamp = sms.getTimestampMillis();
        }

        String body = sms.getMessageBody();
        if (body == null) {
            body = "";
        }
        group.parts.put(info.seq, new ConcatPart(info.seq, body, sms.getTimestampMillis(), now));
    }

    private static void flushCompletedConcatGroups(List<CompleteSmsMessage> out, long now) {
        List<String> completedKeys = new ArrayList<>();
        for (Map.Entry<String, ConcatGroup> entry : multipartCache.entrySet()) {
            ConcatGroup group = entry.getValue();
            if (group.parts.size() >= group.totalParts) {
                out.add(concatGroup(group, true));
                completedKeys.add(entry.getKey());
            }
        }
        for (String key : completedKeys) {
            multipartCache.remove(key);
        }
    }

    private static void flushExpiredConcatGroups(List<CompleteSmsMessage> out, long now) {
        List<String> expiredKeys = new ArrayList<>();
        for (Map.Entry<String, ConcatGroup> entry : multipartCache.entrySet()) {
            ConcatGroup group = entry.getValue();
            if (now - group.firstReceivedAt >= CACHE_EXPIRY_MS) {
                out.add(concatGroup(group, group.parts.size() > 1));
                expiredKeys.add(entry.getKey());
            }
        }
        for (String key : expiredKeys) {
            multipartCache.remove(key);
        }
    }

    private static CompleteSmsMessage concatGroup(ConcatGroup group, boolean wasMultipart) {
        StringBuilder body = new StringBuilder();
        for (int i = 1; i <= group.totalParts; i++) {
            ConcatPart part = group.parts.get(i);
            if (part != null && part.body != null) {
                body.append(part.body);
            }
        }
        CompleteSmsMessage cm = new CompleteSmsMessage();
        cm.address = group.address;
        cm.body = body.toString();
        cm.timestamp = group.firstTimestamp;
        cm.wasMultipart = wasMultipart;
        return cm;
    }

    private static ConcatInfo extractConcatInfo(SmsMessage sms) {
        try {
            byte[] pdu = sms.getPdu();
            if (pdu == null || pdu.length < 2) {
                return null;
            }

            int index = 0;
            int smscLen = pdu[index] & 0xFF;
            index += 1 + smscLen;
            if (index >= pdu.length) {
                return null;
            }

            int firstOctet = pdu[index] & 0xFF;
            boolean udhi = (firstOctet & 0x40) != 0;
            if (!udhi) {
                return null;
            }

            index++; // first octet

            // Address length + type + address bytes (SMS-DELIVER)
            if (index >= pdu.length) {
                return null;
            }
            int addrLen = pdu[index] & 0xFF;
            index++;
            if (index >= pdu.length) {
                return null;
            }
            index++; // address type
            int addrBytes = (addrLen + 1) / 2;
            index += addrBytes;

            // PID + DCS + SCTS
            index += 1 + 1 + 7;
            if (index >= pdu.length) {
                return null;
            }

            int userDataLen = pdu[index] & 0xFF;
            index++;
            if (index >= pdu.length || userDataLen <= 0) {
                return null;
            }

            int udhLen = pdu[index] & 0xFF;
            index++;
            int udhEnd = index + udhLen;
            while (index + 1 < udhEnd && index + 1 < pdu.length) {
                int iei = pdu[index] & 0xFF;
                index++;
                int ielen = pdu[index] & 0xFF;
                index++;

                if (index + ielen > pdu.length) {
                    return null;
                }

                if (iei == 0x00 && ielen >= 3) {
                    int ref = pdu[index] & 0xFF;
                    int total = pdu[index + 1] & 0xFF;
                    int seq = pdu[index + 2] & 0xFF;
                    return new ConcatInfo(ref, total, seq);
                } else if (iei == 0x08 && ielen >= 4) {
                    int ref = ((pdu[index] & 0xFF) << 8) | (pdu[index + 1] & 0xFF);
                    int total = pdu[index + 2] & 0xFF;
                    int seq = pdu[index + 3] & 0xFF;
                    return new ConcatInfo(ref, total, seq);
                }

                index += ielen;
            }
        } catch (Exception ignored) {
            // Fallback to non-concat handling
        }
        return null;
    }
}
