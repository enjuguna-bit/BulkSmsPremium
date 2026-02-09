package com.afriserve.smsmanager.models;

/**
 * SMS Model for dashboard and analytics
 */
public class SmsModel {
    private final String id;
    private final String address;
    private final String body;
    private final String date;
    private final long timestamp;
    private final int type;
    private final boolean isRead;
    private final int threadId;

    public SmsModel(String id, String address, String body, String date, long timestamp, 
                   int type, boolean isRead, int threadId) {
        this.id = id;
        this.address = address;
        this.body = body;
        this.date = date;
        this.timestamp = timestamp;
        this.type = type;
        this.isRead = isRead;
        this.threadId = threadId;
    }

    public String getId() { return id; }
    public String getAddress() { return address; }
    public String getBody() { return body; }
    public String getDate() { return date; }
    public long getTimestamp() { return timestamp; }
    public int getType() { return type; }
    public boolean isRead() { return isRead; }
    public int getThreadId() { return threadId; }
}
