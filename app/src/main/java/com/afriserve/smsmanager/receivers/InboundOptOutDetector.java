package com.afriserve.smsmanager.receivers;

import android.content.Context;
import android.util.Log;

import com.afriserve.smsmanager.AppDatabase;
import com.afriserve.smsmanager.data.dao.OptOutDao;
import com.afriserve.smsmanager.data.entity.OptOutEntity;
import com.afriserve.smsmanager.data.utils.PhoneNumberUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Detects opt-out keywords in inbound SMS bodies and automatically records
 * the sender in {@link OptOutDao} so that future bulk sends skip them.
 *
 * <p>Required by the Kenyan Data Protection Act 2019 and Google Play's
 * Sensitive Permissions policy for apps that send marketing messages.
 *
 * <h3>Keyword matching</h3>
 * Case-insensitive. Message body is trimmed before matching. Keywords that
 * match at word boundaries prevent false positives in conversational messages
 * (e.g. "I want to STOP at the shop" is intentionally NOT treated as opt-out
 * because "STOP" must be the entire first word of the trimmed body, or the
 * body must contain a multi-word phrase listed in MULTI_WORD_KEYWORDS).
 */
public final class InboundOptOutDetector {

    private static final String TAG = "InboundOptOutDetector";

    /** Single-word bodies that mean "stop sending me messages". */
    private static final List<String> SINGLE_WORD_KEYWORDS = Arrays.asList(
            "stop", "unsubscribe", "quit", "end", "cancel", "deactivate", "optout"
    );

    /**
     * Longer phrases that also signal opt-out intent.
     * Checked with {@code contains()} so they can appear anywhere in the body.
     */
    private static final List<String> MULTI_WORD_KEYWORDS = Arrays.asList(
            "opt out", "opt-out", "stop messages", "unsubscribe me",
            "please remove", "remove me", "stop sms", "toa", "simama"   // Swahili
    );

    private InboundOptOutDetector() { /* utility class */ }

    /**
     * Checks whether {@code body} contains an opt-out signal.
     *
     * @param body raw inbound SMS text (may be null)
     * @return {@code true} if the message requests opt-out
     */
    public static boolean isOptOutMessage(String body) {
        if (body == null || body.isEmpty()) return false;
        String lower = body.trim().toLowerCase(Locale.ENGLISH);

        // 1. First word of body matches a known single-word keyword exactly
        String[] words = lower.split("\\s+", 2);
        if (words.length > 0 && SINGLE_WORD_KEYWORDS.contains(words[0])) {
            return true;
        }

        // 2. Body contains a multi-word phrase
        for (String phrase : MULTI_WORD_KEYWORDS) {
            if (lower.contains(phrase)) {
                return true;
            }
        }

        return false;
    }

    /**
     * If {@code body} is an opt-out message, records {@code senderAddress} in
     * {@link OptOutDao} on the calling background thread.
     *
     * <p>Must be called from a background thread (e.g. the receiver executor).
     *
     * @param context       application context
     * @param senderAddress raw sender address as received from the network
     * @param body          message body
     * @return {@code true} if an opt-out was recorded
     */
    public static boolean handleIfOptOut(Context context, String senderAddress, String body) {
        if (!isOptOutMessage(body)) return false;

        String normalized = PhoneNumberUtils.normalizePhoneNumber(senderAddress);
        if (normalized == null || normalized.isEmpty()) {
            normalized = senderAddress;
        }

        try {
            OptOutDao optOutDao = AppDatabase.getInstance(context).optOutDao();

            // Avoid duplicate entries: check whether already opted out.
            boolean alreadyOptedOut;
            try {
                optOutDao.getOptOutByPhone(normalized).blockingGet();
                alreadyOptedOut = true;
            } catch (Exception notFound) {
                alreadyOptedOut = false;
            }

            if (!alreadyOptedOut) {
                OptOutEntity entity = new OptOutEntity();
                entity.phoneNumber = normalized;
                entity.reason      = "INBOUND_STOP_KEYWORD";
                entity.optOutTime  = System.currentTimeMillis();
                entity.source      = "INBOUND_SMS";
                entity.isActive    = true;

                optOutDao.insertOptOut(entity).blockingAwait();
                Log.i(TAG, "Auto opt-out recorded for inbound STOP from: " + normalized);
            }
            return true;

        } catch (Exception e) {
            Log.e(TAG, "Failed to record inbound opt-out for " + normalized, e);
            return false;
        }
    }
}