package com.afriserve.smsmanager.data.templates;

import java.util.UUID;

/**
 * SMS Template model
 */
public class SmsTemplate {
    private String id;
    private String name;
    private String content;
    private String description;
    private long createdAt;
    private long lastModified;
    private int usageCount;
    private String category;
    private boolean isSystemTemplate;

    public SmsTemplate() {
        this.id = UUID.randomUUID().toString();
        this.createdAt = System.currentTimeMillis();
        this.lastModified = System.currentTimeMillis();
        this.usageCount = 0;
        this.isSystemTemplate = false;
    }

    public SmsTemplate(String name, String content) {
        this();
        this.name = name;
        this.content = content;
    }

    public SmsTemplate(String name, String content, String description) {
        this(name, content);
        this.description = description;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public long getLastModified() {
        return lastModified;
    }

    public void setLastModified(long lastModified) {
        this.lastModified = lastModified;
    }

    public int getUsageCount() {
        return usageCount;
    }

    public void setUsageCount(int usageCount) {
        this.usageCount = usageCount;
    }

    public void incrementUsageCount() {
        this.usageCount++;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public boolean isSystemTemplate() {
        return isSystemTemplate;
    }

    public void setSystemTemplate(boolean systemTemplate) {
        isSystemTemplate = systemTemplate;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SmsTemplate that = (SmsTemplate) o;
        return id != null ? id.equals(that.id) : that.id == null;
    }

    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : 0;
    }

    @Override
    public String toString() {
        return "SmsTemplate{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", content='" + content + '\'' +
                ", usageCount=" + usageCount +
                '}';
    }
}
