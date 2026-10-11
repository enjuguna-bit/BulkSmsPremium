package com.afriserve.smsmanager.data.export;

/** Joined campaign/SMS row used by global history search and follow-up reports. */
public class CampaignSmsRecord {
    public long smsId;
    public long campaignId;
    public String customerName;
    public String phoneNumber;
    public Double amount;
    public String officerName;
    public Integer arrearsDays;
    public Integer sourceRowIndex;
    public String campaignName;
    public String campaignDate;
    public String message;
    public String smsStatus;
    public String errorMessage;
    public Long sentAt;
    public Long deliveredAt;
}
