package com.afriserve.smsmanager.models;

/**
 * SMS Model for dashboard and analytics
 */
public class SmsModel {
    private String id;
    private String address;
    private String body;
    private String date;
    private long timestamp;
    private int type;
    private boolean isRead;
    private String threadId;

    public SmsModel() {
        // Default constructor
    }

    public SmsModel(String id, String address, String body, String date, long timestamp, 
                   int type, boolean isRead, int threadId) {
        this.id = id;
        this.address = address;
        this.body = body;
        this.date = date;
        this.timestamp = timestamp;
        this.type = type;
        this.isRead = isRead;
        this.threadId = String.valueOf(threadId);
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    
    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }
    
    public String getDate() { return date; }
    public void setDate(String date) { this.date = date; }
    
    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
    
    public int getType() { return type; }
    public void setType(int type) { this.type = type; }
    
    public boolean isRead() { return isRead; }
    public void setRead(boolean read) { isRead = read; }
    
    public String getThreadId() { return threadId; }
    public void setThreadId(String threadId) { this.threadId = threadId; }
}