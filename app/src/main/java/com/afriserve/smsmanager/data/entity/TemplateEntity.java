package com.afriserve.smsmanager.data.entity;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.ColumnInfo;
import androidx.room.Index;
import androidx.room.Ignore;

/**
 * Room entity for an SMS message template.
 *
 * Changes v2:
 *  - Added {@link #charCount} column: precomputed character count so
 *    RecyclerView adapters don't call content.length() on every bind.
 *  - Added {@link #isArchived} column: soft-delete flag so templates can be
 *    hidden without losing usage history. All list queries should add
 *    "WHERE isArchived = 0" to exclude archived entries.
 *  - Added {@link #smsPartCount()} computed helper consistent with GSM-7 rules.
 *  - Added {@link #archive()} / {@link #unarchive()} convenience methods.
 *  - equals() and hashCode() updated for new columns.
 */
@Entity(
    tableName = "template_entities",
    indices = {
        @Index(value = {"category"}),
        @Index(value = {"isFavorite"}),
        @Index(value = {"usageCount"}),
        @Index(value = {"isArchived"})   // speeds up the common "WHERE isArchived = 0" filter
    }
)
public class TemplateEntity {

    // ── GSM-7 part boundary constants ─────────────────────────────────────────
    /** Max characters in a single GSM-7 SMS (no UDH header). */
    private static final int GSM7_SINGLE_PART_MAX   = 160;
    /** Max characters per part in a multipart GSM-7 message (with UDH header). */
    private static final int GSM7_MULTIPART_PART_MAX = 153;

    // ── Columns ───────────────────────────────────────────────────────────────

    @PrimaryKey(autoGenerate = true)
    public long id;

    @ColumnInfo(name = "name")
    public String name;

    @ColumnInfo(name = "content")
    public String content;

    @ColumnInfo(name = "category")
    public String category;

    @ColumnInfo(name = "description")
    public String description;

    @ColumnInfo(name = "isFavorite")
    public boolean isFavorite = false;

    @ColumnInfo(name = "usageCount")
    public int usageCount = 0;

    @ColumnInfo(name = "lastUsed")
    public Long lastUsed;

    @ColumnInfo(name = "variables")
    public String variables; // JSON string for template variables

    /**
     * Pre-computed character count of {@link #content}.
     * Updated automatically by {@link #setContentAndRefreshCharCount(String)}.
     * Defaults to 0; callers that set content directly should call
     * {@link #refreshCharCount()} afterwards.
     */
    @ColumnInfo(name = "charCount")
    public int charCount = 0;

    /**
     * Soft-delete flag. Archived templates are hidden from the main list but
     * retained for usage-history purposes.
     */
    @ColumnInfo(name = "isArchived")
    public boolean isArchived = false;

    @ColumnInfo(name = "createdAt")
    public long createdAt;

    @ColumnInfo(name = "updatedAt")
    public long updatedAt;

    // ── Constructors ──────────────────────────────────────────────────────────

    public TemplateEntity() {}

    @Ignore
    public TemplateEntity(String name, String content, String category) {
        this.name = name;
        this.content = content;
        this.category = category;
        this.charCount = content != null ? content.length() : 0;
        this.createdAt = System.currentTimeMillis();
        this.updatedAt = System.currentTimeMillis();
    }

    // ── Content helpers ───────────────────────────────────────────────────────

    /**
     * Sets the content and immediately refreshes the stored charCount.
     * Prefer this over direct field assignment when modifying an existing entity.
     */
    public void setContentAndRefreshCharCount(String newContent) {
        this.content = newContent;
        refreshCharCount();
        this.updatedAt = System.currentTimeMillis();
    }

    /**
     * Recomputes {@link #charCount} from the current {@link #content}.
     * Call after any direct assignment to the {@code content} field.
     */
    public void refreshCharCount() {
        this.charCount = (content != null) ? content.length() : 0;
    }

    /**
     * Returns the number of SMS parts this template occupies assuming GSM-7
     * encoding (plain ASCII). Unicode content may use more parts; this is a
     * lower-bound estimate appropriate for Kenyan A2P use cases.
     */
    public int smsPartCount() {
        int len = charCount > 0 ? charCount : (content != null ? content.length() : 0);
        if (len <= 0) return 1;
        if (len <= GSM7_SINGLE_PART_MAX) return 1;
        return (int) Math.ceil((double) len / GSM7_MULTIPART_PART_MAX);
    }

    // ── Usage helpers ─────────────────────────────────────────────────────────

    public void incrementUsage() {
        this.usageCount++;
        this.lastUsed = System.currentTimeMillis();
        this.updatedAt = System.currentTimeMillis();
    }

    public void updateTimestamp() {
        this.updatedAt = System.currentTimeMillis();
    }

    public boolean isRecentlyUsed() {
        if (lastUsed == null) return false;
        long oneWeekAgo = System.currentTimeMillis() - (7L * 24 * 60 * 60 * 1000);
        return lastUsed > oneWeekAgo;
    }

    // ── Archive helpers ───────────────────────────────────────────────────────

    /** Soft-deletes this template; it will no longer appear in default queries. */
    public void archive() {
        this.isArchived = true;
        this.updatedAt = System.currentTimeMillis();
    }

    /** Restores a previously archived template. */
    public void unarchive() {
        this.isArchived = false;
        this.updatedAt = System.currentTimeMillis();
    }

    // ── equals / hashCode ─────────────────────────────────────────────────────

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TemplateEntity that = (TemplateEntity) o;
        if (id != that.id) return false;
        if (isFavorite != that.isFavorite) return false;
        if (isArchived != that.isArchived) return false;
        if (usageCount != that.usageCount) return false;
        if (charCount != that.charCount) return false;
        if (createdAt != that.createdAt) return false;
        if (updatedAt != that.updatedAt) return false;
        if (name != null ? !name.equals(that.name) : that.name != null) return false;
        if (content != null ? !content.equals(that.content) : that.content != null) return false;
        if (category != null ? !category.equals(that.category) : that.category != null) return false;
        if (description != null ? !description.equals(that.description) : that.description != null) return false;
        if (variables != null ? !variables.equals(that.variables) : that.variables != null) return false;
        return lastUsed != null ? lastUsed.equals(that.lastUsed) : that.lastUsed == null;
    }

    @Override
    public int hashCode() {
        int result = (int) (id ^ (id >>> 32));
        result = 31 * result + (name != null ? name.hashCode() : 0);
        result = 31 * result + (content != null ? content.hashCode() : 0);
        result = 31 * result + (category != null ? category.hashCode() : 0);
        result = 31 * result + (description != null ? description.hashCode() : 0);
        result = 31 * result + (isFavorite ? 1 : 0);
        result = 31 * result + (isArchived ? 1 : 0);
        result = 31 * result + usageCount;
        result = 31 * result + charCount;
        result = 31 * result + (lastUsed != null ? lastUsed.hashCode() : 0);
        result = 31 * result + (variables != null ? variables.hashCode() : 0);
        result = 31 * result + (int) (createdAt ^ (createdAt >>> 32));
        result = 31 * result + (int) (updatedAt ^ (updatedAt >>> 32));
        return result;
    }
}
