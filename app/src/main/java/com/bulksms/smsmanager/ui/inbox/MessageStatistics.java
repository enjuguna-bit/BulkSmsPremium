package com.bulksms.smsmanager.ui.inbox;

public class MessageStatistics {
    public final int total;
    public final int unread;
    public final int inbox;
    public final int sent;
    
    public MessageStatistics(int total, int unread, int inbox, int sent) {
        this.total = total;
        this.unread = unread;
        this.inbox = inbox;
        this.sent = sent;
    }
}
