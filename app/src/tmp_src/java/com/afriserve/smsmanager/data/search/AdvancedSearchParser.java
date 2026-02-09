package com.afriserve.smsmanager.data.search;

import android.util.Log;

import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.List;
import java.util.ArrayList;
import java.util.Calendar;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.TimeZone;

import com.afriserve.smsmanager.data.entity.SmsEntity;
import com.afriserve.smsmanager.data.contacts.ContactResolver;
import com.afriserve.smsmanager.data.utils.PhoneNumberUtils;

import javax.inject.Inject;
import javax.inject.Singleton;

import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * Advanced search parser with support for search operators and contact integration
 * Supports operators like "from:123", "to:456", "date:today", "unread", "sent", etc.
 */
@Singleton
public class AdvancedSearchParser {
    
    private static final String TAG = "AdvancedSearchParser";
    
    // Search operator patterns
    private static final Pattern FROM_PATTERN = Pattern.compile("(?i)from:(\\+?[\\d\\s\\-\\(\\)]+|\"[^\"]+\")");
    private static final Pattern TO_PATTERN = Pattern.compile("(?i)to:(\\+?[\\d\\s\\-\\(\\)]+|\"[^\"]+\")");
    private static final Pattern DATE_PATTERN = Pattern.compile("(?i)date:(today|yesterday|week|month|year|\\d{4}-\\d{2}-\\d{2})");
    private static final Pattern STATUS_PATTERN = Pattern.compile("(?i)(unread|read|sent|received|draft|failed)");
    private static final Pattern CONTACT_PATTERN = Pattern.compile("(?i)contact:\"([^\"]+)\"");
    private static final Pattern HAS_ATTACHMENT_PATTERN = Pattern.compile("(?i)has:attachment");
    private static final Pattern MESSAGE_TYPE_PATTERN = Pattern.compile("(?i)type:(sms|mms)");
    
    private final ContactResolver contactResolver;
    private final SimpleDateFormat dateFormat;
    private final SimpleDateFormat yearFormat;
    
    @Inject
    public AdvancedSearchParser(ContactResolver contactResolver) {
        this.contactResolver = contactResolver;
        this.dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        this.dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
        this.yearFormat = new SimpleDateFormat("yyyy", Locale.getDefault());
        this.yearFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
        
        Log.d(TAG, "AdvancedSearchParser initialized");
    }
    
    /**
     * Parse search query with advanced operators
     */
    public Single<SearchCriteria> parseQuery(String query) {
        return Single.fromCallable(() -> {
            SearchCriteria criteria = new SearchCriteria();
            
            if (query == null || query.trim().isEmpty()) {
                return criteria;
            }
            
            String remainingQuery = query.trim();
            
            // Parse FROM operator
            Matcher fromMatcher = FROM_PATTERN.matcher(remainingQuery);
            while (fromMatcher.find()) {
                String phoneValue = fromMatcher.group(1).replace("\"", "").trim();
                String normalizedPhone = PhoneNumberUtils.normalizePhoneNumber(phoneValue);
                if (normalizedPhone != null) {
                    criteria.fromPhoneNumbers.add(normalizedPhone);
                }
                remainingQuery = remainingQuery.replace(fromMatcher.group(), "").trim();
            }
            
            // Parse TO operator
            Matcher toMatcher = TO_PATTERN.matcher(remainingQuery);
            while (toMatcher.find()) {
                String phoneValue = toMatcher.group(1).replace("\"", "").trim();
                String normalizedPhone = PhoneNumberUtils.normalizePhoneNumber(phoneValue);
                if (normalizedPhone != null) {
                    criteria.toPhoneNumbers.add(normalizedPhone);
                }
                remainingQuery = remainingQuery.replace(toMatcher.group(), "").trim();
            }
            
            // Parse DATE operator
            Matcher dateMatcher = DATE_PATTERN.matcher(remainingQuery);
            while (dateMatcher.find()) {
                String dateValue = dateMatcher.group(1);
                DateRange dateRange = parseDateValue(dateValue);
                if (dateRange != null) {
                    criteria.dateRange = dateRange;
                }
                remainingQuery = remainingQuery.replace(dateMatcher.group(), "").trim();
            }
            
            // Parse STATUS operators
            Matcher statusMatcher = STATUS_PATTERN.matcher(remainingQuery);
            while (statusMatcher.find()) {
                String statusValue = statusMatcher.group(1).toLowerCase();
                switch (statusValue) {
                    case "unread":
                        criteria.isUnread = true;
                        break;
                    case "read":
                        criteria.isUnread = false;
                        break;
                    case "sent":
                        criteria.messageTypes.add("SENT");
                        break;
                    case "received":
                        criteria.messageTypes.add("DELIVERED");
                        criteria.messageTypes.add("PENDING");
                        break;
                    case "draft":
                        criteria.messageTypes.add("DRAFT");
                        break;
                    case "failed":
                        criteria.messageTypes.add("FAILED");
                        break;
                }
                remainingQuery = remainingQuery.replace(statusMatcher.group(), "").trim();
            }
            
            // Parse CONTACT operator
            Matcher contactMatcher = CONTACT_PATTERN.matcher(remainingQuery);
            while (contactMatcher.find()) {
                String contactName = contactMatcher.group(1);
                criteria.contactNames.add(contactName);
                remainingQuery = remainingQuery.replace(contactMatcher.group(), "").trim();
            }
            
            // Parse HAS operator
            if (HAS_ATTACHMENT_PATTERN.matcher(remainingQuery).find()) {
                criteria.hasAttachment = true;
                remainingQuery = remainingQuery.replaceAll(HAS_ATTACHMENT_PATTERN.pattern(), "").trim();
            }
            
            // Parse TYPE operator
            Matcher typeMatcher = MESSAGE_TYPE_PATTERN.matcher(remainingQuery);
            while (typeMatcher.find()) {
                String typeValue = typeMatcher.group(1).toLowerCase();
                criteria.messageTypes.add(typeValue.toUpperCase());
                remainingQuery = remainingQuery.replace(typeMatcher.group(), "").trim();
            }
            
            // Remaining text is the general search query
            criteria.textQuery = remainingQuery.trim();
            
            // Resolve contact names to phone numbers
            if (!criteria.contactNames.isEmpty()) {
                resolveContactNames(criteria);
            }
            
            Log.d(TAG, "Parsed search query: " + criteria);
            return criteria;
        }).subscribeOn(Schedulers.io());
    }
    
    /**
     * Parse date value into date range
     */
    private DateRange parseDateValue(String dateValue) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeZone(TimeZone.getTimeZone("UTC"));
        
        switch (dateValue.toLowerCase()) {
            case "today":
                cal.set(Calendar.HOUR_OF_DAY, 0);
                cal.set(Calendar.MINUTE, 0);
                cal.set(Calendar.SECOND, 0);
                cal.set(Calendar.MILLISECOND, 0);
                long todayStart = cal.getTimeInMillis();
                cal.add(Calendar.DAY_OF_YEAR, 1);
                long todayEnd = cal.getTimeInMillis();
                return new DateRange(todayStart, todayEnd);
                
            case "yesterday":
                cal.add(Calendar.DAY_OF_YEAR, -1);
                cal.set(Calendar.HOUR_OF_DAY, 0);
                cal.set(Calendar.MINUTE, 0);
                cal.set(Calendar.SECOND, 0);
                cal.set(Calendar.MILLISECOND, 0);
                long yesterdayStart = cal.getTimeInMillis();
                cal.add(Calendar.DAY_OF_YEAR, 1);
                long yesterdayEnd = cal.getTimeInMillis();
                return new DateRange(yesterdayStart, yesterdayEnd);
                
            case "week":
                cal.set(Calendar.DAY_OF_WEEK, cal.getFirstDayOfWeek());
                cal.set(Calendar.HOUR_OF_DAY, 0);
                cal.set(Calendar.MINUTE, 0);
                cal.set(Calendar.SECOND, 0);
                cal.set(Calendar.MILLISECOND, 0);
                long weekStart = cal.getTimeInMillis();
                cal.add(Calendar.DAY_OF_YEAR, 7);
                long weekEnd = cal.getTimeInMillis();
                return new DateRange(weekStart, weekEnd);
                
            case "month":
                cal.set(Calendar.DAY_OF_MONTH, 1);
                cal.set(Calendar.HOUR_OF_DAY, 0);
                cal.set(Calendar.MINUTE, 0);
                cal.set(Calendar.SECOND, 0);
                cal.set(Calendar.MILLISECOND, 0);
                long monthStart = cal.getTimeInMillis();
                cal.add(Calendar.MONTH, 1);
                long monthEnd = cal.getTimeInMillis();
                return new DateRange(monthStart, monthEnd);
                
            case "year":
                cal.set(Calendar.DAY_OF_YEAR, 1);
                cal.set(Calendar.HOUR_OF_DAY, 0);
                cal.set(Calendar.MINUTE, 0);
                cal.set(Calendar.SECOND, 0);
                cal.set(Calendar.MILLISECOND, 0);
                long yearStart = cal.getTimeInMillis();
                cal.add(Calendar.YEAR, 1);
                long yearEnd = cal.getTimeInMillis();
                return new DateRange(yearStart, yearEnd);
                
            default:
                // Try to parse as YYYY-MM-DD
                try {
                    java.util.Date date = dateFormat.parse(dateValue);
                    if (date != null) {
                        cal.setTime(date);
                        cal.set(Calendar.HOUR_OF_DAY, 0);
                        cal.set(Calendar.MINUTE, 0);
                        cal.set(Calendar.SECOND, 0);
                        cal.set(Calendar.MILLISECOND, 0);
                        long dayStart = cal.getTimeInMillis();
                        cal.add(Calendar.DAY_OF_YEAR, 1);
                        long dayEnd = cal.getTimeInMillis();
                        return new DateRange(dayStart, dayEnd);
                    }
                } catch (Exception e) {
                    Log.w(TAG, "Failed to parse date: " + dateValue, e);
                }
                return null;
        }
    }
    
    /**
     * Resolve contact names to phone numbers
     */
    private void resolveContactNames(SearchCriteria criteria) {
        // This is a simplified implementation
        // In a real app, you might want to cache contact lookups or use a more sophisticated method
        for (String contactName : criteria.contactNames) {
            try {
                // For now, we'll add the contact name to the text query
                // In a full implementation, you'd query the contacts provider
                // and add matching phone numbers to the criteria
                if (criteria.textQuery.isEmpty()) {
                    criteria.textQuery = contactName;
                } else {
                    criteria.textQuery += " " + contactName;
                }
            } catch (Exception e) {
                Log.w(TAG, "Failed to resolve contact name: " + contactName, e);
            }
        }
    }
    
    /**
     * Check if SMS message matches search criteria
     */
    public boolean matchesCriteria(SmsEntity message, SearchCriteria criteria) {
        // Check text query
        if (!criteria.textQuery.isEmpty()) {
            String searchText = criteria.textQuery.toLowerCase();
            String messageText = (message.getBody() + " " + message.getAddress()).toLowerCase();
            if (!messageText.contains(searchText)) {
                return false;
            }
        }
        
        // Check from phone numbers
        if (!criteria.fromPhoneNumbers.isEmpty()) {
            String messagePhone = PhoneNumberUtils.normalizePhoneNumber(message.getAddress());
            boolean matchesFrom = false;
            for (String phone : criteria.fromPhoneNumbers) {
                if (PhoneNumberUtils.areSameNumber(messagePhone, phone)) {
                    matchesFrom = true;
                    break;
                }
            }
            if (!matchesFrom) {
                return false;
            }
        }
        
        // Check to phone numbers (for sent messages)
        if (!criteria.toPhoneNumbers.isEmpty()) {
            String messagePhone = PhoneNumberUtils.normalizePhoneNumber(message.getAddress());
            boolean matchesTo = false;
            for (String phone : criteria.toPhoneNumbers) {
                if (PhoneNumberUtils.areSameNumber(messagePhone, phone)) {
                    matchesTo = true;
                    break;
                }
            }
            if (!matchesTo) {
                return false;
            }
        }
        
        // Check date range
        if (criteria.dateRange != null) {
            long messageTime = message.getDate();
            if (messageTime < criteria.dateRange.startTime || messageTime >= criteria.dateRange.endTime) {
                return false;
            }
        }
        
        // Check read status
        if (criteria.isUnread != null) {
            if (message.isUnread() != criteria.isUnread) {
                return false;
            }
        }
        
        // Check message types
        if (!criteria.messageTypes.isEmpty()) {
            String messageType = message.getType();
            if (!criteria.messageTypes.contains(messageType)) {
                return false;
            }
        }
        
        // Check attachment (simplified - would need MMS support for full implementation)
        if (criteria.hasAttachment != null && criteria.hasAttachment) {
            // For SMS, this would always be false
            // In a full implementation, you'd check MMS message parts
            return false;
        }
        
        return true;
    }
    
    /**
     * Search criteria data class
     */
    public static class SearchCriteria {
        public String textQuery = "";
        public List<String> fromPhoneNumbers = new ArrayList<>();
        public List<String> toPhoneNumbers = new ArrayList<>();
        public List<String> contactNames = new ArrayList<>();
        public List<String> messageTypes = new ArrayList<>();
        public DateRange dateRange;
        public Boolean isUnread;
        public Boolean hasAttachment;
        
        @Override
        public String toString() {
            return String.format("SearchCriteria{text='%s', from=%s, to=%s, contacts=%s, types=%s, date=%s, unread=%s, attachment=%s}",
                textQuery, fromPhoneNumbers, toPhoneNumbers, contactNames, messageTypes, 
                dateRange, isUnread, hasAttachment);
        }
    }
    
    /**
     * Date range for search filtering
     */
    public static class DateRange {
        public final long startTime;
        public final long endTime;
        
        DateRange(long startTime, long endTime) {
            this.startTime = startTime;
            this.endTime = endTime;
        }
        
        @Override
        public String toString() {
            return String.format("[%d - %d]", startTime, endTime);
        }
    }
}
