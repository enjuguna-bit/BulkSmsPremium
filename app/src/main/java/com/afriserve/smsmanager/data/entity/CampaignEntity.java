package com.afriserve.smsmanager.data.entity;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.ColumnInfo;
import androidx.room.Index;
import androidx.room.Ignore;

/**
 * Room entity for a bulk-SMS campaign.
 *
 * Changes v2:
 *  - Added type-safe STATUS_* constants — eliminates magic strings at every
 *    call-site and makes exhaustive switch/when analysis easier.
 *  - Added progressPercentage() helper so UI doesn't recompute it.
 *  - Added charCount / messagePartCount columns so cost estimation can be
 *    precomputed when the campaign is created rather than on every RecyclerView
 *    bind pass.
 *  - equals() and hashCode() updated to include the two new columns.
 */
@Entity(
    tableName = "campaign_entities",
    indices = {
        @Index(value = {"status"}),
        @Index(value = {"createdAt"})
        ,@Index(value = {"campaignUuid"}, unique = true)
        ,@Index(value = {"campaignDate"})
    }
)
public class CampaignEntity {

    // ── Type-safe status constants ────────────────────────────────────────────
    public static final String STATUS_DRAFT     = "DRAFT";
    public static final String STATUS_ACTIVE    = "ACTIVE";
    public static final String STATUS_PAUSED    = "PAUSED";
    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_CANCELLED = "CANCELLED";
    public static final String STATUS_CLOSED    = "CLOSED";

    // ── Columns ───────────────────────────────────────────────────────────────

    @PrimaryKey(autoGenerate = true)
    public long id;

    @ColumnInfo(name = "name")
    public String name;

    /** Stable external identity used in filenames and reports. */
    @ColumnInfo(name = "campaignUuid")
    public String campaignUuid;

    /** Nairobi business date (yyyy-MM-dd) for the campaign reporting window. */
    @ColumnInfo(name = "campaignDate")
    public String campaignDate;

    @ColumnInfo(name = "campaignType")
    public String campaignType;

    @ColumnInfo(name = "sourceFileName")
    public String sourceFileName;

    @ColumnInfo(name = "arrearsFilter")
    public String arrearsFilter;

    @ColumnInfo(name = "timezone")
    public String timezone;

    @ColumnInfo(name = "description")
    public String description;

    /** Exact message template used by this campaign, retained for reporting and follow-up. */
    @ColumnInfo(name = "messageTemplate")
    public String messageTemplate;

    @ColumnInfo(name = "status")
    public String status; // see STATUS_* constants above

    @ColumnInfo(name = "templateId")
    public Long templateId;

    @ColumnInfo(name = "recipientCount")
    public int recipientCount;

    @ColumnInfo(name = "sentCount")
    public int sentCount = 0;

    @ColumnInfo(name = "deliveredCount")
    public int deliveredCount = 0;

    @ColumnInfo(name = "failedCount")
    public int failedCount = 0;

    @ColumnInfo(name = "skippedCount")
    public int skippedCount = 0;

    /**
     * Character count of the message template at the time the campaign was created.
     * Stored to avoid re-parsing the template on every UI bind.
     */
    @ColumnInfo(name = "charCount")
    public int charCount = 0;

    /**
     * Number of SMS parts the message occupies (1 for ≤160 GSM-7 chars,
     * more for multipart or Unicode messages).
     * Stored alongside charCount to enable fast cost estimation in lists.
     */
    @ColumnInfo(name = "messagePartCount")
    public int messagePartCount = 1;

    @ColumnInfo(name = "scheduledAt")
    public Long scheduledAt;

    @ColumnInfo(name = "startedAt")
    public Long startedAt;

    @ColumnInfo(name = "completedAt")
    public Long completedAt;

    @ColumnInfo(name = "closedAt")
    public Long closedAt;

    @ColumnInfo(name = "lastSnapshotAt")
    public Long lastSnapshotAt;

    @ColumnInfo(name = "createdAt")
    public long createdAt;

    @ColumnInfo(name = "updatedAt")
    public long updatedAt;

    @ColumnInfo(name = "settings")
    public String settings; // JSON string for campaign settings

    // ── Constructors ──────────────────────────────────────────────────────────

    public CampaignEntity() {}

    @Ignore
    public CampaignEntity(String name, String description, String status) {
        this.name = name;
        this.description = description;
        this.status = status;
        this.createdAt = System.currentTimeMillis();
        this.updatedAt = System.currentTimeMillis();
    }

    // ── Status helpers ────────────────────────────────────────────────────────

    public boolean isDraft()     { return STATUS_DRAFT.equals(status); }
    public boolean isActive()    { return STATUS_ACTIVE.equals(status); }
    public boolean isPaused()    { return STATUS_PAUSED.equals(status); }
    public boolean isCompleted() { return STATUS_COMPLETED.equals(status); }
    public boolean isCancelled() { return STATUS_CANCELLED.equals(status); }
    public boolean isClosed() { return STATUS_CLOSED.equals(status); }

    // ── Computed metrics ──────────────────────────────────────────────────────

    /**
     * Delivery rate as a percentage of sent messages that were delivered.
     * Returns 0.0 when no messages have been sent yet.
     */
    public double getDeliveryRate() {
        if (sentCount == 0) return 0.0;
        return (double) deliveredCount / sentCount * 100.0;
    }

    /**
     * Success rate as a percentage of all recipients that received the message.
     * Returns 0.0 when recipient count is zero.
     */
    public double getSuccessRate() {
        if (recipientCount == 0) return 0.0;
        return (double) deliveredCount / recipientCount * 100.0;
    }

    /**
     * Sending progress as a percentage of processed (sent + failed + skipped)
     * messages relative to the total recipient count.
     * Useful for progress bars; always in [0, 100].
     */
    public int progressPercentage() {
        if (recipientCount <= 0) return 0;
        int processed = sentCount + failedCount + skippedCount;
        return Math.min(100, (int) (processed * 100L / recipientCount));
    }

    /**
     * Number of messages that have not yet been processed.
     */
    public int remainingCount() {
        return Math.max(0, recipientCount - (sentCount + failedCount + skippedCount));
    }

    public void updateTimestamp() {
        this.updatedAt = System.currentTimeMillis();
    }

    // ── equals / hashCode ─────────────────────────────────────────────────────

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CampaignEntity that = (CampaignEntity) o;
        if (id != that.id) return false;
        if (recipientCount != that.recipientCount) return false;
        if (sentCount != that.sentCount) return false;
        if (deliveredCount != that.deliveredCount) return false;
        if (failedCount != that.failedCount) return false;
        if (charCount != that.charCount) return false;
        if (messagePartCount != that.messagePartCount) return false;
        if (createdAt != that.createdAt) return false;
        if (updatedAt != that.updatedAt) return false;
        if (name != null ? !name.equals(that.name) : that.name != null) return false;
        if (description != null ? !description.equals(that.description) : that.description != null) return false;
        if (status != null ? !status.equals(that.status) : that.status != null) return false;
        if (templateId != null ? !templateId.equals(that.templateId) : that.templateId != null) return false;
        if (scheduledAt != null ? !scheduledAt.equals(that.scheduledAt) : that.scheduledAt != null) return false;
        if (startedAt != null ? !startedAt.equals(that.startedAt) : that.startedAt != null) return false;
        if (completedAt != null ? !completedAt.equals(that.completedAt) : that.completedAt != null) return false;
        return settings != null ? settings.equals(that.settings) : that.settings == null;
    }

    @Override
    public int hashCode() {
        int result = (int) (id ^ (id >>> 32));
        result = 31 * result + (name != null ? name.hashCode() : 0);
        result = 31 * result + (description != null ? description.hashCode() : 0);
        result = 31 * result + (status != null ? status.hashCode() : 0);
        result = 31 * result + (templateId != null ? templateId.hashCode() : 0);
        result = 31 * result + recipientCount;
        result = 31 * result + sentCount;
        result = 31 * result + deliveredCount;
        result = 31 * result + failedCount;
        result = 31 * result + charCount;
        result = 31 * result + messagePartCount;
        result = 31 * result + (scheduledAt != null ? scheduledAt.hashCode() : 0);
        result = 31 * result + (startedAt != null ? startedAt.hashCode() : 0);
        result = 31 * result + (completedAt != null ? completedAt.hashCode() : 0);
        result = 31 * result + (int) (createdAt ^ (createdAt >>> 32));
        result = 31 * result + (int) (updatedAt ^ (updatedAt >>> 32));
        result = 31 * result + (settings != null ? settings.hashCode() : 0);
        return result;
    }
}
