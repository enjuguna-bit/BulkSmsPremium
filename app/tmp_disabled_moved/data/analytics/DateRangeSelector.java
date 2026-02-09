package com.bulksms.smsmanager.data.analytics;

import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import javax.inject.Inject;
import javax.inject.Singleton;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * Date range selector for dashboard analytics
 * Provides flexible date range selection with predefined and custom ranges
 */
@Singleton
public class DateRangeSelector {
    
    private static final String TAG = "DateRangeSelector";
    
    // Current selection
    private final MutableLiveData<DateRange> _selectedRange = new MutableLiveData<>();
    public final LiveData<DateRange> selectedRange = _selectedRange;
    
    // Available ranges
    private final MutableLiveData<List<DateRange>> _availableRanges = new MutableLiveData<>();
    public final LiveData<List<DateRange>> availableRanges = _availableRanges;
    
    @Inject
    public DateRangeSelector() {
        initializeRanges();
        selectDefaultRange();
        
        Log.d(TAG, "Date range selector initialized");
    }
    
    /**
     * Select a predefined date range
     */
    public Completable selectRange(PredefinedRange range) {
        return Completable.fromAction(() -> {
            try {
                DateRange dateRange = createPredefinedRange(range);
                _selectedRange.postValue(dateRange);
                
                Log.d(TAG, "Selected range: " + range);
                
            } catch (Exception e) {
                Log.e(TAG, "Failed to select range", e);
            }
        }).subscribeOn(Schedulers.io());
    }
    
    /**
     * Select a custom date range
     */
    public Completable selectCustomRange(long startDate, long endDate) {
        return Completable.fromAction(() -> {
            try {
                if (startDate >= endDate) {
                    throw new IllegalArgumentException("Start date must be before end date");
                }
                
                DateRange dateRange = new DateRange(
                    "CUSTOM",
                    startDate,
                    endDate,
                    formatDate(startDate),
                    formatDate(endDate),
                    calculateDuration(startDate, endDate)
                );
                
                _selectedRange.postValue(dateRange);
                
                Log.d(TAG, "Selected custom range: CUSTOM");
                
            } catch (Exception e) {
                Log.e(TAG, "Failed to select custom range", e);
                throw new RuntimeException(e);
            }
        }).subscribeOn(Schedulers.io());
    }
    
    /**
     * Get current selected range
     */
    public LiveData<DateRange> getCurrentRange() {
        return _selectedRange;
    }
    
    /**
     * Get available ranges
     */
    public LiveData<List<DateRange>> getAvailableRanges() {
        return availableRanges;
    }
    
    /**
     * Extend current range by specified amount
     */
    public Completable extendRange(int days) {
        return Completable.fromAction(() -> {
            try {
                DateRange current = _selectedRange.getValue();
                if (current == null) {
                    throw new IllegalStateException("No range selected");
                }
                
                Calendar cal = Calendar.getInstance();
                cal.setTimeInMillis(current.endDate);
                cal.add(Calendar.DAY_OF_MONTH, days);
                
                long newEndDate = cal.getTimeInMillis();
                
                DateRange extendedRange = new DateRange(
                    current.type,
                    current.startDate,
                    newEndDate,
                    current.startLabel,
                    formatDate(newEndDate),
                    calculateDuration(current.startDate, newEndDate)
                );
                
                _selectedRange.postValue(extendedRange);
                
                Log.d(TAG, "Extended range by " + days + " days");
                
            } catch (Exception e) {
                Log.e(TAG, "Failed to extend range", e);
                throw new RuntimeException(e);
            }
        }).subscribeOn(Schedulers.io());
    }
    
    /**
     * Shift range by specified amount
     */
    public Completable shiftRange(int days) {
        return Completable.fromAction(() -> {
            try {
                DateRange current = _selectedRange.getValue();
                if (current == null) {
                    throw new IllegalStateException("No range selected");
                }
                
                long duration = current.endDate - current.startDate;
                
                Calendar cal = Calendar.getInstance();
                cal.setTimeInMillis(current.startDate);
                cal.add(Calendar.DAY_OF_MONTH, days);
                long newStartDate = cal.getTimeInMillis();
                
                DateRange shiftedRange = new DateRange(
                    current.type,
                    newStartDate,
                    newStartDate + duration,
                    formatDate(newStartDate),
                    formatDate(newStartDate + duration),
                    duration
                );
                
                _selectedRange.postValue(shiftedRange);
                
                Log.d(TAG, "Shifted range by " + days + " days");
                
            } catch (Exception e) {
                Log.e(TAG, "Failed to shift range", e);
                throw new RuntimeException(e);
            }
        }).subscribeOn(Schedulers.io());
    }
    
    /**
     * Check if date is within current range
     */
    public boolean isDateInRange(long date) {
        DateRange range = _selectedRange.getValue();
        if (range == null) return false;
        
        return date >= range.startDate && date <= range.endDate;
    }
    
    /**
     * Get range statistics
     */
    public RangeStatistics getRangeStatistics() {
        DateRange range = _selectedRange.getValue();
        if (range == null) {
            return new RangeStatistics(0, 0, 0, "N/A");
        }
        
        long duration = range.endDate - range.startDate;
        long days = duration / (24 * 60 * 60 * 1000);
        long weeks = days / 7;
        long months = days / 30;
        
        String periodType;
        if (days < 7) {
            periodType = "DAYS";
        } else if (days < 30) {
            periodType = "WEEKS";
        } else if (days < 365) {
            periodType = "MONTHS";
        } else {
            periodType = "YEARS";
        }
        
        return new RangeStatistics((int)days, (int)weeks, (int)months, periodType);
    }
    
    // Private methods
    
    private void initializeRanges() {
        List<DateRange> ranges = new ArrayList<>();
        
        // Add predefined ranges
        for (PredefinedRange predefined : PredefinedRange.values()) {
            ranges.add(createPredefinedRange(predefined));
        }
        
        _availableRanges.postValue(ranges);
    }
    
    private void selectDefaultRange() {
        // Default to last 7 days
        selectRange(PredefinedRange.LAST_7_DAYS)
            .subscribe(
                () -> Log.d(TAG, "Default range selected"),
                error -> Log.e(TAG, "Failed to select default range", error)
            );
    }
    
    private DateRange createPredefinedRange(PredefinedRange range) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeZone(TimeZone.getTimeZone("UTC"));
        
        switch (range) {
            case TODAY:
                cal.set(Calendar.HOUR_OF_DAY, 0);
                cal.set(Calendar.MINUTE, 0);
                cal.set(Calendar.SECOND, 0);
                cal.set(Calendar.MILLISECOND, 0);
                long startOfDay = cal.getTimeInMillis();
                cal.add(Calendar.DAY_OF_MONTH, 1);
                cal.add(Calendar.MILLISECOND, -1);
                long endOfDay = cal.getTimeInMillis();
                
                return new DateRange(
                    range.name(),
                    startOfDay,
                    endOfDay,
                    "Today",
                    "Today",
                    endOfDay - startOfDay
                );
                
            case YESTERDAY:
                cal.add(Calendar.DAY_OF_MONTH, -1);
                cal.set(Calendar.HOUR_OF_DAY, 0);
                cal.set(Calendar.MINUTE, 0);
                cal.set(Calendar.SECOND, 0);
                cal.set(Calendar.MILLISECOND, 0);
                long startOfYesterday = cal.getTimeInMillis();
                cal.add(Calendar.DAY_OF_MONTH, 1);
                cal.add(Calendar.MILLISECOND, -1);
                long endOfYesterday = cal.getTimeInMillis();
                
                return new DateRange(
                    range.name(),
                    startOfYesterday,
                    endOfYesterday,
                    "Yesterday",
                    "Yesterday",
                    endOfYesterday - startOfYesterday
                );
                
            case LAST_7_DAYS:
                cal.set(Calendar.HOUR_OF_DAY, 23);
                cal.set(Calendar.MINUTE, 59);
                cal.set(Calendar.SECOND, 59);
                cal.set(Calendar.MILLISECOND, 999);
                long endOf7Days = cal.getTimeInMillis();
                cal.add(Calendar.DAY_OF_MONTH, -6);
                cal.set(Calendar.HOUR_OF_DAY, 0);
                cal.set(Calendar.MINUTE, 0);
                cal.set(Calendar.SECOND, 0);
                cal.set(Calendar.MILLISECOND, 0);
                long startOf7Days = cal.getTimeInMillis();
                
                return new DateRange(
                    range.name(),
                    startOf7Days,
                    endOf7Days,
                    formatDate(startOf7Days),
                    formatDate(endOf7Days),
                    endOf7Days - startOf7Days
                );
                
            case LAST_30_DAYS:
                cal.set(Calendar.HOUR_OF_DAY, 23);
                cal.set(Calendar.MINUTE, 59);
                cal.set(Calendar.SECOND, 59);
                cal.set(Calendar.MILLISECOND, 999);
                long endOf30Days = cal.getTimeInMillis();
                cal.add(Calendar.DAY_OF_MONTH, -29);
                cal.set(Calendar.HOUR_OF_DAY, 0);
                cal.set(Calendar.MINUTE, 0);
                cal.set(Calendar.SECOND, 0);
                cal.set(Calendar.MILLISECOND, 0);
                long startOf30Days = cal.getTimeInMillis();
                
                return new DateRange(
                    range.name(),
                    startOf30Days,
                    endOf30Days,
                    formatDate(startOf30Days),
                    formatDate(endOf30Days),
                    endOf30Days - startOf30Days
                );
                
            case THIS_WEEK:
                cal.set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY);
                cal.set(Calendar.HOUR_OF_DAY, 0);
                cal.set(Calendar.MINUTE, 0);
                cal.set(Calendar.SECOND, 0);
                cal.set(Calendar.MILLISECOND, 0);
                long startOfWeek = cal.getTimeInMillis();
                cal.add(Calendar.DAY_OF_WEEK, Calendar.SATURDAY);
                cal.set(Calendar.HOUR_OF_DAY, 23);
                cal.set(Calendar.MINUTE, 59);
                cal.set(Calendar.SECOND, 59);
                cal.set(Calendar.MILLISECOND, 999);
                long endOfWeek = cal.getTimeInMillis();
                
                return new DateRange(
                    range.name(),
                    startOfWeek,
                    endOfWeek,
                    formatDate(startOfWeek),
                    formatDate(endOfWeek),
                    endOfWeek - startOfWeek
                );
                
            case THIS_MONTH:
                cal.set(Calendar.DAY_OF_MONTH, 1);
                cal.set(Calendar.HOUR_OF_DAY, 0);
                cal.set(Calendar.MINUTE, 0);
                cal.set(Calendar.SECOND, 0);
                cal.set(Calendar.MILLISECOND, 0);
                long startOfMonth = cal.getTimeInMillis();
                cal.add(Calendar.MONTH, 1);
                cal.add(Calendar.DAY_OF_MONTH, -1);
                cal.set(Calendar.HOUR_OF_DAY, 23);
                cal.set(Calendar.MINUTE, 59);
                cal.set(Calendar.SECOND, 59);
                cal.set(Calendar.MILLISECOND, 999);
                long endOfMonth = cal.getTimeInMillis();
                
                return new DateRange(
                    range.name(),
                    startOfMonth,
                    endOfMonth,
                    formatDate(startOfMonth),
                    formatDate(endOfMonth),
                    endOfMonth - startOfMonth
                );
                
            case LAST_3_MONTHS:
                cal.set(Calendar.HOUR_OF_DAY, 23);
                cal.set(Calendar.MINUTE, 59);
                cal.set(Calendar.SECOND, 59);
                cal.set(Calendar.MILLISECOND, 999);
                long endOf3Months = cal.getTimeInMillis();
                cal.add(Calendar.MONTH, -3);
                cal.set(Calendar.HOUR_OF_DAY, 0);
                cal.set(Calendar.MINUTE, 0);
                cal.set(Calendar.SECOND, 0);
                cal.set(Calendar.MILLISECOND, 0);
                long startOf3Months = cal.getTimeInMillis();
                
                return new DateRange(
                    range.name(),
                    startOf3Months,
                    endOf3Months,
                    formatDate(startOf3Months),
                    formatDate(endOf3Months),
                    endOf3Months - startOf3Months
                );
                
            case THIS_YEAR:
                cal.set(Calendar.DAY_OF_YEAR, 1);
                cal.set(Calendar.HOUR_OF_DAY, 0);
                cal.set(Calendar.MINUTE, 0);
                cal.set(Calendar.SECOND, 0);
                cal.set(Calendar.MILLISECOND, 0);
                long startOfYear = cal.getTimeInMillis();
                cal.add(Calendar.YEAR, 1);
                cal.add(Calendar.DAY_OF_YEAR, -1);
                cal.set(Calendar.HOUR_OF_DAY, 23);
                cal.set(Calendar.MINUTE, 59);
                cal.set(Calendar.SECOND, 59);
                cal.set(Calendar.MILLISECOND, 999);
                long endOfYear = cal.getTimeInMillis();
                
                return new DateRange(
                    range.name(),
                    startOfYear,
                    endOfYear,
                    formatDate(startOfYear),
                    formatDate(endOfYear),
                    endOfYear - startOfYear
                );
                
            default:
                // Default to last 7 days
                return createPredefinedRange(PredefinedRange.LAST_7_DAYS);
        }
    }
    
    private String formatDate(long timestamp) {
        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
        return sdf.format(new Date(timestamp));
    }
    
    private long calculateDuration(long startDate, long endDate) {
        return endDate - startDate;
    }
    
    // Data classes
    
    public enum PredefinedRange {
        TODAY,
        YESTERDAY,
        LAST_7_DAYS,
        LAST_30_DAYS,
        THIS_WEEK,
        THIS_MONTH,
        LAST_3_MONTHS,
        THIS_YEAR
    }
    
    public static class DateRange {
        public final String type;
        public final long startDate;
        public final long endDate;
        public final String startLabel;
        public final String endLabel;
        public final long duration;
        
        public DateRange(String type, long startDate, long endDate, String startLabel, 
                        String endLabel, long duration) {
            this.type = type;
            this.startDate = startDate;
            this.endDate = endDate;
            this.startLabel = startLabel;
            this.endLabel = endLabel;
            this.duration = duration;
        }
        
        public boolean isCustom() {
            return "CUSTOM".equals(type);
        }
        
        public boolean isToday() {
            return "TODAY".equals(type);
        }
        
        public boolean isYesterday() {
            return "YESTERDAY".equals(type);
        }
        
        public boolean isThisWeek() {
            return "THIS_WEEK".equals(type);
        }
        
        public boolean isThisMonth() {
            return "THIS_MONTH".equals(type);
        }
        
        public boolean isThisYear() {
            return "THIS_YEAR".equals(type);
        }
        
        public int getDays() {
            return (int) (duration / (24 * 60 * 60 * 1000));
        }
        
        public String getDisplayLabel() {
            if (isCustom()) {
                return startLabel + " - " + endLabel;
            } else {
                return type.replace("_", " ");
            }
        }
        
        @Override
        public String toString() {
            return "DateRange{" +
                    "type='" + type + '\'' +
                    ", startLabel='" + startLabel + '\'' +
                    ", endLabel='" + endLabel + '\'' +
                    ", days=" + getDays() +
                    '}';
        }
    }
    
    public static class RangeStatistics {
        public final int days;
        public final int weeks;
        public final int months;
        public final String periodType;
        
        public RangeStatistics(int days, int weeks, int months, String periodType) {
            this.days = days;
            this.weeks = weeks;
            this.months = months;
            this.periodType = periodType;
        }
        
        public boolean isShortTerm() {
            return days <= 7;
        }
        
        public boolean isMediumTerm() {
            return days > 7 && days <= 90;
        }
        
        public boolean isLongTerm() {
            return days > 90;
        }
        
        @Override
        public String toString() {
            return "RangeStatistics{" +
                    "days=" + days +
                    ", weeks=" + weeks +
                    ", months=" + months +
                    ", periodType='" + periodType + '\'' +
                    '}';
        }
    }
}
