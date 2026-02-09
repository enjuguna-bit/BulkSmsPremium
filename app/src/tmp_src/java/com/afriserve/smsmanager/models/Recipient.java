package com.afriserve.smsmanager.models;

import androidx.annotation.Nullable;
import java.util.Map;

/**
 * Recipient model for bulk SMS
 */
public class Recipient {
    private final String name;
    private final String phone;
    private final Double amount;
    private final boolean isProcessed;
    private final Map<String, String> fields;

    public Recipient(String name, String phone, Double amount, boolean isProcessed, 
                    Map<String, String> fields) {
        this.name = name;
        this.phone = phone;
        this.amount = amount;
        this.isProcessed = isProcessed;
        this.fields = fields;
    }

    @Nullable
    public String getName() { return name; }
    
    public String getPhone() { return phone; }
    
    @Nullable
    public Double getAmount() { return amount; }
    
    public boolean isProcessed() { return isProcessed; }
    
    @Nullable
    public Map<String, String> getFields() { return fields; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        
        Recipient recipient = (Recipient) o;
        return phone != null ? phone.equals(recipient.phone) : recipient.phone == null;
    }

    @Override
    public int hashCode() {
        return phone != null ? phone.hashCode() : 0;
    }
}
