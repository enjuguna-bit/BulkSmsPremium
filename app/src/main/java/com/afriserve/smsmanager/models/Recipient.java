package com.afriserve.smsmanager.models;

import androidx.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

/**
 * Recipient model for bulk SMS
 */
public class Recipient {
    private final String name;
    private final String phone;
    private final Double amount;
    private final boolean isProcessed;
    private Map<String, String> fields;

    public Recipient(String name, String phone, Double amount, boolean isProcessed,
            Map<String, String> fields) {
        this.name = name;
        this.phone = phone;
        this.amount = amount;
        this.isProcessed = isProcessed;
        this.fields = fields != null ? new HashMap<>(fields) : new HashMap<>();
    }
    
    // Convenience constructor for manual recipient addition
    public Recipient(String name, String phone) {
        this.name = name;
        this.phone = phone;
        this.amount = null;
        this.isProcessed = false;
        this.fields = new HashMap<>();
    }

    @Nullable
    public String getName() {
        return name;
    }

    public String getPhone() {
        return phone;
    }
    
    /**
     * Alias for getPhone() for compatibility
     * @return the phone number
     */
    public String getPhoneNumber() {
        return phone;
    }

    @Nullable
    public Double getAmount() {
        return amount;
    }

    public boolean isProcessed() {
        return isProcessed;
    }

    @Nullable
    public Map<String, String> getFields() {
        return fields;
    }
    
    /**
     * Add a variable field to this recipient
     * @param key The variable name
     * @param value The variable value
     */
    public void addVariable(String key, String value) {
        if (fields == null) {
            fields = new HashMap<>();
        }
        fields.put(key, value);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;

        Recipient recipient = (Recipient) o;
        return phone != null ? phone.equals(recipient.phone) : recipient.phone == null;
    }

    @Override
    public int hashCode() {
        return phone != null ? phone.hashCode() : 0;
    }
}
