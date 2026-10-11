package com.afriserve.smsmanager.data.entity;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.ColumnInfo;
import androidx.room.Index;
import androidx.room.Ignore;

/**
 * Entity for scheduled SMS campaigns.
 * Supports one-time and recurring campaigns.
 *
 * Changes v2:
 *  - Added type-safe STATUS_* constants to eliminate magic strings.
 *  - Fixed NPE in calculateNextExecutionTime(): lastExecutionTime is a Long
 *    (nullable) — previously compared with == 0 which would throw when null.
 *  - Fixed NullPointerException risk in scheduleNextExecution() when
 *    recurrencePattern is null.
 *  - addToExecutionHistory() now produces proper JSON-like records.
 *  - markAsFailed() now persists the reason into execution history.
 */
@Entity(
    tableName = "scheduled_campaigns",
    indices = {
        @Index(value = {"campaignId"}),
        @Index(value = {"scheduledTime"}),
        @Index(value = {"status"}),
        @Index(value = {"isActive"})
    }
)
public class ScheduledCampaignEntity {

    // ── Type-safe status constants ────────────────────────────────────────────
    public static final String STATUS_SCHEDULED  = "SCHEDULED";
    public static final String STATUS_EXECUTING  = "EXECUTING";
    public static final String STATUS_COMPLETED  = "COMPLETED";
    public static final String STATUS_FAILED     = "FAILED";
    public static final String STATUS_CANCELLED  = "CANCELLED";

    @PrimaryKey(autoGenerate = true)
    public long id;

    @ColumnInfo(name = "campaignId")
    public long campaignId;

    @ColumnInfo(name = "scheduledTime")
    public long scheduledTime;

    @ColumnInfo(name = "timezone")
    public String timezone; // IANA timezone identifier

    @ColumnInfo(name = "status")
    public String status; // see STATUS_* constants above

    @ColumnInfo(name = "isActive")
    public boolean isActive = true;

    @ColumnInfo(name = "isRecurring")
    public boolean isRecurring = false;

    @ColumnInfo(name = "recurrencePattern")
    public String recurrencePattern; // daily, weekly, monthly, yearly, custom

    @ColumnInfo(name = "recurrenceInterval")
    public int recurrenceInterval = 1; // Every N days/weeks/months

    @ColumnInfo(name = "recurrenceDays")
    public String recurrenceDays; // For weekly: "1,2,3" (Mon,Tue,Wed)

    @ColumnInfo(name = "recurrenceTime")
    public String recurrenceTime; // HH:MM format

    @ColumnInfo(name = "maxOccurrences")
    public Integer maxOccurrences; // null for infinite

    @ColumnInfo(name = "currentOccurrences")
    public int currentOccurrences = 0;

    @ColumnInfo(name = "nextExecutionTime")
    public Long nextExecutionTime;

    @ColumnInfo(name = "lastExecutionTime")
    public Long lastExecutionTime; // nullable — no previous execution yet

    @ColumnInfo(name = "executionHistory")
    public String executionHistory; // JSON array of execution records

    @ColumnInfo(name = "settings")
    public String settings; // JSON string for additional settings

    @ColumnInfo(name = "createdAt")
    public long createdAt;

    @ColumnInfo(name = "updatedAt")
    public long updatedAt;

    public ScheduledCampaignEntity() {
        long now = System.currentTimeMillis();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @Ignore
    public ScheduledCampaignEntity(long campaignId, long scheduledTime, String timezone) {
        this();
        this.campaignId = campaignId;
        this.scheduledTime = scheduledTime;
        this.timezone = timezone;
        this.status = STATUS_SCHEDULED;
        this.nextExecutionTime = scheduledTime;
    }

    // ── Status helpers ────────────────────────────────────────────────────────

    public boolean isScheduled()  { return STATUS_SCHEDULED.equals(status); }
    public boolean isExecuting()  { return STATUS_EXECUTING.equals(status); }
    public boolean isCompleted()  { return STATUS_COMPLETED.equals(status); }
    public boolean isFailed()     { return STATUS_FAILED.equals(status); }
    public boolean isCancelled()  { return STATUS_CANCELLED.equals(status); }

    public boolean shouldExecute() {
        return isActive && isScheduled()
                && nextExecutionTime != null
                && nextExecutionTime <= System.currentTimeMillis();
    }

    // ── Lifecycle transitions ─────────────────────────────────────────────────

    public void markAsExecuting() {
        this.status = STATUS_EXECUTING;
        this.lastExecutionTime = System.currentTimeMillis();
        this.currentOccurrences++;
        updateTimestamp();
    }

    public void markAsCompleted() {
        this.status = STATUS_COMPLETED;
        addToExecutionHistory(STATUS_COMPLETED, null);
        updateTimestamp();

        if (isRecurring) {
            scheduleNextExecution();
        } else {
            this.isActive = false;
        }
    }

    public void markAsFailed(String reason) {
        this.status = STATUS_FAILED;
        addToExecutionHistory(STATUS_FAILED, reason != null ? reason : "unknown");
        updateTimestamp();
    }

    public void cancel() {
        this.status = STATUS_CANCELLED;
        this.isActive = false;
        addToExecutionHistory(STATUS_CANCELLED, null);
        updateTimestamp();
    }

    // ── Recurrence logic ──────────────────────────────────────────────────────

    private void scheduleNextExecution() {
        if (!isRecurring
                || recurrencePattern == null
                || recurrencePattern.isEmpty()
                || (maxOccurrences != null && currentOccurrences >= maxOccurrences)) {
            this.isActive = false;
            return;
        }

        long nextTime = calculateNextExecutionTime();
        if (nextTime > 0) {
            this.nextExecutionTime = nextTime;
            this.status = STATUS_SCHEDULED;
        } else {
            this.isActive = false;
        }
    }

    /**
     * Calculates the next execution timestamp from {@link #lastExecutionTime}.
     *
     * Fix v2: lastExecutionTime is a nullable Long. The old code compared it to
     * {@code == 0} which would throw a NullPointerException when it was null
     * (i.e., on the very first execution). We now use a null-safe check and fall
     * back to scheduledTime as the base when no prior execution exists.
     *
     * @return epoch-millis of the next execution, or 0 if the pattern is invalid.
     */
    private long calculateNextExecutionTime() {
        // Use the last execution time as the base; fall back to scheduledTime when
        // this is the very first recurrence (lastExecutionTime == null or 0).
        long baseTime = (lastExecutionTime != null && lastExecutionTime > 0)
                ? lastExecutionTime
                : scheduledTime;

        switch (recurrencePattern.toLowerCase()) {
            case "daily":
                return baseTime + (recurrenceInterval * 24L * 60L * 60L * 1000L);

            case "weekly":
                return baseTime + (recurrenceInterval * 7L * 24L * 60L * 60L * 1000L);

            case "monthly":
                return addMonths(baseTime, recurrenceInterval);

            case "yearly":
                return addYears(baseTime, recurrenceInterval);

            default:
                return 0; // Unknown pattern — deactivate
        }
    }

    private long addMonths(long time, int months) {
        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.setTimeInMillis(time);
        cal.add(java.util.Calendar.MONTH, months);
        return cal.getTimeInMillis();
    }

    private long addYears(long time, int years) {
        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.setTimeInMillis(time);
        cal.add(java.util.Calendar.YEAR, years);
        return cal.getTimeInMillis();
    }

    /**
     * Appends a structured execution record to {@link #executionHistory}.
     * Format: a simple JSON-like list of objects, kept as a String column.
     */
    private void addToExecutionHistory(String result, String details) {
        long now = System.currentTimeMillis();
        String record = details != null
                ? "{\"ts\":" + now + ",\"result\":\"" + result + "\",\"details\":\"" + details + "\"}"
                : "{\"ts\":" + now + ",\"result\":\"" + result + "\"}";

        if (executionHistory == null || executionHistory.isEmpty()) {
            executionHistory = "[" + record + "]";
        } else {
            // Insert before closing bracket.
            executionHistory = executionHistory.substring(0, executionHistory.length() - 1)
                    + "," + record + "]";
        }
    }

    private void updateTimestamp() {
        this.updatedAt = System.currentTimeMillis();
    }

    // ── equals / hashCode ─────────────────────────────────────────────────────

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ScheduledCampaignEntity that = (ScheduledCampaignEntity) o;
        if (id != that.id) return false;
        if (campaignId != that.campaignId) return false;
        if (scheduledTime != that.scheduledTime) return false;
        if (isActive != that.isActive) return false;
        if (isRecurring != that.isRecurring) return false;
        if (recurrenceInterval != that.recurrenceInterval) return false;
        if (currentOccurrences != that.currentOccurrences) return false;
        if (createdAt != that.createdAt) return false;
        if (updatedAt != that.updatedAt) return false;
        if (timezone != null ? !timezone.equals(that.timezone) : that.timezone != null) return false;
        if (status != null ? !status.equals(that.status) : that.status != null) return false;
        if (recurrencePattern != null ? !recurrencePattern.equals(that.recurrencePattern) : that.recurrencePattern != null) return false;
        if (nextExecutionTime != null ? !nextExecutionTime.equals(that.nextExecutionTime) : that.nextExecutionTime != null) return false;
        return lastExecutionTime != null ? lastExecutionTime.equals(that.lastExecutionTime) : that.lastExecutionTime == null;
    }

    @Override
    public int hashCode() {
        int result = (int) (id ^ (id >>> 32));
        result = 31 * result + (int) (campaignId ^ (campaignId >>> 32));
        result = 31 * result + (int) (scheduledTime ^ (scheduledTime >>> 32));
        result = 31 * result + (timezone != null ? timezone.hashCode() : 0);
        result = 31 * result + (status != null ? status.hashCode() : 0);
        result = 31 * result + (isActive ? 1 : 0);
        result = 31 * result + (isRecurring ? 1 : 0);
        result = 31 * result + (recurrencePattern != null ? recurrencePattern.hashCode() : 0);
        result = 31 * result + recurrenceInterval;
        result = 31 * result + currentOccurrences;
        result = 31 * result + (maxOccurrences != null ? maxOccurrences.hashCode() : 0);
        result = 31 * result + (nextExecutionTime != null ? nextExecutionTime.hashCode() : 0);
        result = 31 * result + (lastExecutionTime != null ? lastExecutionTime.hashCode() : 0);
        result = 31 * result + (int) (createdAt ^ (createdAt >>> 32));
        result = 31 * result + (int) (updatedAt ^ (updatedAt >>> 32));
        return result;
    }
}
