package com.afriserve.smsmanager.ui.analytics;

import java.util.Map;
import java.util.HashMap;

/**
 * Analytics data model
 */
public class AnalyticsData {
    private String planName;
    private String planTier;
    private int dailyUsage;
    private int dailyLimit;
    private int dailyPercentage;
    private int monthlyUsage;
    private int monthlyLimit;
    private int monthlyPercentage;
    private int campaignsUsed;
    private int campaignsLimit;
    private int templatesUsed;
    private int templatesLimit;
    private Map<String, Object> userProperties;
    private String upgradeSuggestion;
    private long lastUpdated;

    public AnalyticsData() {
        this.userProperties = new HashMap<>();
        this.lastUpdated = System.currentTimeMillis();
    }

    public String getPlanName() {
        return planName;
    }

    public void setPlanName(String planName) {
        this.planName = planName;
    }

    public String getPlanTier() {
        return planTier;
    }

    public void setPlanTier(String planTier) {
        this.planTier = planTier;
    }

    public int getDailyUsage() {
        return dailyUsage;
    }

    public void setDailyUsage(int dailyUsage) {
        this.dailyUsage = dailyUsage;
    }

    public int getDailyLimit() {
        return dailyLimit;
    }

    public void setDailyLimit(int dailyLimit) {
        this.dailyLimit = dailyLimit;
    }

    public int getDailyPercentage() {
        return dailyPercentage;
    }

    public void setDailyPercentage(int dailyPercentage) {
        this.dailyPercentage = dailyPercentage;
    }

    public int getMonthlyUsage() {
        return monthlyUsage;
    }

    public void setMonthlyUsage(int monthlyUsage) {
        this.monthlyUsage = monthlyUsage;
    }

    public int getMonthlyLimit() {
        return monthlyLimit;
    }

    public void setMonthlyLimit(int monthlyLimit) {
        this.monthlyLimit = monthlyLimit;
    }

    public int getMonthlyPercentage() {
        return monthlyPercentage;
    }

    public void setMonthlyPercentage(int monthlyPercentage) {
        this.monthlyPercentage = monthlyPercentage;
    }

    public int getCampaignsUsed() {
        return campaignsUsed;
    }

    public void setCampaignsUsed(int campaignsUsed) {
        this.campaignsUsed = campaignsUsed;
    }

    public int getCampaignsLimit() {
        return campaignsLimit;
    }

    public void setCampaignsLimit(int campaignsLimit) {
        this.campaignsLimit = campaignsLimit;
    }

    public int getTemplatesUsed() {
        return templatesUsed;
    }

    public void setTemplatesUsed(int templatesUsed) {
        this.templatesUsed = templatesUsed;
    }

    public int getTemplatesLimit() {
        return templatesLimit;
    }

    public void setTemplatesLimit(int templatesLimit) {
        this.templatesLimit = templatesLimit;
    }

    public Map<String, Object> getUserProperties() {
        return userProperties;
    }

    public void setUserProperties(Map<String, Object> userProperties) {
        this.userProperties = userProperties;
    }

    public String getUpgradeSuggestion() {
        return upgradeSuggestion;
    }

    public void setUpgradeSuggestion(String upgradeSuggestion) {
        this.upgradeSuggestion = upgradeSuggestion;
    }

    public long getLastUpdated() {
        return lastUpdated;
    }

    public void setLastUpdated(long lastUpdated) {
        this.lastUpdated = lastUpdated;
    }
}
