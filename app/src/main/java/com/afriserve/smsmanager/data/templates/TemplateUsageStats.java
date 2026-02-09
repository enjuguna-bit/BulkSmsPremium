package com.afriserve.smsmanager.data.templates;

import java.util.HashMap;
import java.util.Map;

/**
 * Template usage statistics
 */
public class TemplateUsageStats {
    private Map<String, Integer> templateUsageCount;
    private Map<String, Long> lastUsedTime;
    private int totalUsage;
    private long lastUpdated;

    public TemplateUsageStats() {
        this.templateUsageCount = new HashMap<>();
        this.lastUsedTime = new HashMap<>();
        this.totalUsage = 0;
        this.lastUpdated = System.currentTimeMillis();
    }

    public void recordUsage(String templateId, int recipientCount) {
        // Update usage count
        int currentCount = templateUsageCount.getOrDefault(templateId, 0);
        templateUsageCount.put(templateId, currentCount + recipientCount);
        
        // Update last used time
        lastUsedTime.put(templateId, System.currentTimeMillis());
        
        // Update total usage
        totalUsage += recipientCount;
        lastUpdated = System.currentTimeMillis();
    }

    public int getUsageCount(String templateId) {
        return templateUsageCount.getOrDefault(templateId, 0);
    }

    public long getLastUsedTime(String templateId) {
        return lastUsedTime.getOrDefault(templateId, 0L);
    }

    public int getTotalUsage() {
        return totalUsage;
    }

    public Map<String, Integer> getAllUsageCounts() {
        return new HashMap<>(templateUsageCount);
    }

    public Map<String, Long> getAllLastUsedTimes() {
        return new HashMap<>(lastUsedTime);
    }

    public long getLastUpdated() {
        return lastUpdated;
    }

    public void setLastUpdated(long lastUpdated) {
        this.lastUpdated = lastUpdated;
    }

    public void clearStats() {
        templateUsageCount.clear();
        lastUsedTime.clear();
        totalUsage = 0;
        lastUpdated = System.currentTimeMillis();
    }
}
