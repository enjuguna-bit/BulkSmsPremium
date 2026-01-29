package com.bulksms.smsmanager.data.parser;

import android.util.Log;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.regex.Pattern;

/**
 * Safe date parser utility
 * Handles various date formats and provides safe parsing with fallback
 */
public class DateParser {
    private static final String TAG = "DateParser";
    
    // Supported date formats
    private static final String[] DATE_FORMATS = {
        "yyyy-MM-dd",           // 2023-12-31
        "dd/MM/yyyy",           // 31/12/2023
        "dd/MM/yy",             // 31/12/23
        "MM/dd/yyyy",           // 12/31/2023
        "MM/dd/yy",             // 12/31/23
        "yyyy/MM/dd",           // 2023/12/31
        "dd-MM-yyyy",           // 31-12-2023
        "dd-MM-yy",             // 31-12-23
        "MM-dd-yyyy",           // 12-31-2023
        "MM-dd-yy",             // 12-31-23
        "dd MMM yyyy",          // 31 Dec 2023
        "dd MMMM yyyy",         // 31 December 2023
        "MMM dd yyyy",          // Dec 31 2023
        "MMMM dd yyyy",         // December 31 2023
        "yyyy-MM-dd HH:mm:ss",  // 2023-12-31 23:59:59
        "dd/MM/yyyy HH:mm:ss",  // 31/12/2023 23:59:59
        "dd/MM/yy HH:mm:ss",    // 31/12/23 23:59:59
        "HH:mm:ss dd/MM/yyyy",  // 23:59:59 31/12/2023
        "EEE, dd MMM yyyy",     // Mon, 31 Dec 2023
        "EEEE, dd MMM yyyy",    // Monday, 31 Dec 2023
        "yyyy-MM-dd'T'HH:mm:ss", // ISO format: 2023-12-31T23:59:59
        "yyyy-MM-dd'T'HH:mm:ssZ", // ISO with timezone: 2023-12-31T23:59:59Z
        "yyyy-MM-dd'T'HH:mm:ss.SSS", // ISO with milliseconds: 2023-12-31T23:59:59.000
        "yyyy-MM-dd'T'HH:mm:ss.SSSZ" // ISO with milliseconds and timezone: 2023-12-31T23:59:59.000Z
    };
    
    // Date pattern for validation
    private static final Pattern DATE_PATTERN = Pattern.compile(
        "(\\d{4}-\\d{2}-\\d{2}|\\d{1,2}/\\d{1,2}/\\d{2,4}|\\d{1,2}\\s+\\w+\\s+\\d{4})"
    );
    
    /**
     * Safe date parser that tries multiple formats
     * Returns current date as fallback if parsing fails
     */
    public static String safeDate(Object value) {
        if (value == null) {
            return getCurrentIsoDate();
        }
        
        String dateStr = value.toString().trim();
        if (dateStr.isEmpty()) {
            return getCurrentIsoDate();
        }
        
        // Try parsing with each supported format
        for (String format : DATE_FORMATS) {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat(format, Locale.getDefault());
                sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
                Date date = sdf.parse(dateStr);
                if (date != null) {
                    return dateToIsoString(date);
                }
            } catch (ParseException e) {
                // Continue to next format
            }
        }
        
        // If all formats fail, try to extract date from string using regex
        try {
            java.util.regex.Matcher matcher = DATE_PATTERN.matcher(dateStr);
            if (matcher.find()) {
                String extractedDate = matcher.group(1);
                return safeDate(extractedDate);
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to extract date from string: " + dateStr, e);
        }
        
        // Final fallback - return current date
        Log.w(TAG, "Failed to parse date: " + dateStr + ", using current date");
        return getCurrentIsoDate();
    }
    
    /**
     * Parse date to Date object with fallback
     */
    public static Date safeParseDate(Object value) {
        String isoDate = safeDate(value);
        try {
            return isoStringToDate(isoDate);
        } catch (Exception e) {
            return new Date();
        }
    }
    
    /**
     * Format date to ISO string
     */
    public static String dateToIsoString(Date date) {
        if (date == null) {
            return getCurrentIsoDate();
        }
        
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault());
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        return sdf.format(date);
    }
    
    /**
     * Parse ISO string to Date
     */
    public static Date isoStringToDate(String isoString) {
        if (isoString == null || isoString.isEmpty()) {
            return new Date();
        }
        
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault());
            sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
            return sdf.parse(isoString);
        } catch (ParseException e) {
            // Try alternative ISO format
            try {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault());
                sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
                return sdf.parse(isoString);
            } catch (ParseException e2) {
                Log.w(TAG, "Failed to parse ISO date: " + isoString, e2);
                return new Date();
            }
        }
    }
    
    /**
     * Get current date as ISO string
     */
    public static String getCurrentIsoDate() {
        return dateToIsoString(new Date());
    }
    
    /**
     * Format date for display
     */
    public static String formatDateForDisplay(String isoDate) {
        return formatDateForDisplay(isoDate, "dd MMM yyyy HH:mm");
    }
    
    /**
     * Format date for display with custom format
     */
    public static String formatDateForDisplay(String isoDate, String format) {
        try {
            Date date = isoStringToDate(isoDate);
            SimpleDateFormat sdf = new SimpleDateFormat(format, Locale.getDefault());
            sdf.setTimeZone(TimeZone.getDefault());
            return sdf.format(date);
        } catch (Exception e) {
            Log.w(TAG, "Failed to format date for display: " + isoDate, e);
            return isoDate;
        }
    }
    
    /**
     * Get relative time string (e.g., "2 hours ago", "3 days ago")
     */
    public static String getRelativeTimeString(String isoDate) {
        try {
            Date date = isoStringToDate(isoDate);
            long now = System.currentTimeMillis();
            long diff = now - date.getTime();
            
            if (diff < 60000) { // Less than 1 minute
                return "Just now";
            } else if (diff < 3600000) { // Less than 1 hour
                int minutes = (int) (diff / 60000);
                return minutes + " minute" + (minutes == 1 ? "" : "s") + " ago";
            } else if (diff < 86400000) { // Less than 1 day
                int hours = (int) (diff / 3600000);
                return hours + " hour" + (hours == 1 ? "" : "s") + " ago";
            } else if (diff < 604800000) { // Less than 1 week
                int days = (int) (diff / 86400000);
                return days + " day" + (days == 1 ? "" : "s") + " ago";
            } else {
                // Show formatted date for older dates
                return formatDateForDisplay(isoDate, "dd MMM yyyy");
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to get relative time: " + isoDate, e);
            return formatDateForDisplay(isoDate);
        }
    }
    
    /**
     * Check if date is recent (within last 24 hours)
     */
    public static boolean isRecent(String isoDate) {
        try {
            Date date = isoStringToDate(isoDate);
            long now = System.currentTimeMillis();
            long diff = now - date.getTime();
            return diff < 86400000; // 24 hours
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Check if date is today
     */
    public static boolean isToday(String isoDate) {
        try {
            Date date = isoStringToDate(isoDate);
            Date today = new Date();
            
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            sdf.setTimeZone(TimeZone.getDefault());
            
            return sdf.format(date).equals(sdf.format(today));
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Get date only part (without time)
     */
    public static String getDateOnly(String isoDate) {
        try {
            Date date = isoStringToDate(isoDate);
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            sdf.setTimeZone(TimeZone.getDefault());
            return sdf.format(date);
        } catch (Exception e) {
            return isoDate;
        }
    }
    
    /**
     * Get time only part (without date)
     */
    public static String getTimeOnly(String isoDate) {
        try {
            Date date = isoStringToDate(isoDate);
            SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
            sdf.setTimeZone(TimeZone.getDefault());
            return sdf.format(date);
        } catch (Exception e) {
            return isoDate;
        }
    }
    
    /**
     * Add days to date
     */
    public static String addDays(String isoDate, int days) {
        try {
            Date date = isoStringToDate(isoDate);
            long newTime = date.getTime() + (days * 86400000L);
            return dateToIsoString(new Date(newTime));
        } catch (Exception e) {
            return isoDate;
        }
    }
    
    /**
     * Subtract days from date
     */
    public static String subtractDays(String isoDate, int days) {
        return addDays(isoDate, -days);
    }
    
    /**
     * Get days difference between two dates
     */
    public static int getDaysDifference(String isoDate1, String isoDate2) {
        try {
            Date date1 = isoStringToDate(isoDate1);
            Date date2 = isoStringToDate(isoDate2);
            long diff = date2.getTime() - date1.getTime();
            return (int) (diff / 86400000L);
        } catch (Exception e) {
            return 0;
        }
    }
    
    /**
     * Validate date string
     */
    public static boolean isValidDate(String dateStr) {
        if (dateStr == null || dateStr.trim().isEmpty()) {
            return false;
        }
        
        String parsed = safeDate(dateStr);
        return !parsed.equals(getCurrentIsoDate()) || dateStr.trim().toLowerCase().contains("now");
    }
    
    /**
     * Get date format pattern that matches the input
     */
    public static String detectDateFormat(String dateStr) {
        if (dateStr == null || dateStr.trim().isEmpty()) {
            return null;
        }
        
        for (String format : DATE_FORMATS) {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat(format, Locale.getDefault());
                sdf.setLenient(false);
                Date date = sdf.parse(dateStr);
                if (date != null) {
                    return format;
                }
            } catch (ParseException e) {
                // Continue to next format
            }
        }
        
        return null;
    }
    
    /**
     * Convert timestamp to ISO string
     */
    public static String timestampToIsoString(long timestamp) {
        return dateToIsoString(new Date(timestamp));
    }
    
    /**
     * Convert ISO string to timestamp
     */
    public static long isoStringToTimestamp(String isoString) {
        try {
            Date date = isoStringToDate(isoString);
            return date.getTime();
        } catch (Exception e) {
            return System.currentTimeMillis();
        }
    }
}
