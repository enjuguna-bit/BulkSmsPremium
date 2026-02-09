package com.afriserve.smsmanager.models;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public class CsvRowModel {
    private List<String> headers;
    private List<String> values;
    private String previewMessage;
    private Map<String, String> dataMap;

    // Phone number patterns for validation
    private static final Pattern PHONE_PATTERN = Pattern.compile(
        "^(\\+\\d{1,3}[- ]?)?\\(?\\d{3}\\)?[- ]?\\d{3}[- ]?\\d{4}$"
    );

    private static final Pattern INTERNATIONAL_PHONE_PATTERN = Pattern.compile(
        "^\\+\\d{10,15}$"
    );

    public CsvRowModel(List<String> headers, List<String> values) {
        this.headers = new ArrayList<>(headers);
        this.values = new ArrayList<>(values);
        this.dataMap = new HashMap<>();
        
        // Build data map for easy access
        for (int i = 0; i < headers.size() && i < values.size(); i++) {
            dataMap.put(headers.get(i), values.get(i));
        }
    }

    public List<String> getHeaders() {
        return new ArrayList<>(headers);
    }

    public List<String> getValues() {
        return new ArrayList<>(values);
    }

    public Map<String, String> getDataMap() {
        return new HashMap<>(dataMap);
    }

    public String getValue(String header) {
        return dataMap.get(header);
    }

    public String getPhoneNumber() {
        // Try to find phone number in common phone columns
        String[] phoneColumns = {"phone", "mobile", "cell", "contact", "number", "tel", "phone_number", "mobile_number"};
        
        for (String column : phoneColumns) {
            String value = getValue(column);
            if (value != null && isValidPhoneNumber(value)) {
                return cleanPhoneNumber(value);
            }
        }
        
        // If no standard phone column found, try to find any valid phone number in the data
        for (String value : values) {
            if (value != null && isValidPhoneNumber(value)) {
                return cleanPhoneNumber(value);
            }
        }
        
        return null;
    }

    public boolean hasValidPhoneNumber() {
        return getPhoneNumber() != null;
    }

    private boolean isValidPhoneNumber(String phone) {
        if (phone == null || phone.trim().isEmpty()) {
            return false;
        }
        
        String cleaned = phone.replaceAll("[^0-9+]", "");
        
        // Check if it's a valid international number
        if (INTERNATIONAL_PHONE_PATTERN.matcher(cleaned).matches()) {
            return true;
        }
        
        // Check if it's a valid local number (with country code)
        if (PHONE_PATTERN.matcher(phone).matches()) {
            return true;
        }
        
        // Check if it's a simple 10-digit number (assume US format)
        if (cleaned.matches("^\\d{10}$")) {
            return true;
        }
        
        return false;
    }

    private String cleanPhoneNumber(String phone) {
        if (phone == null) {
            return null;
        }
        
        String cleaned = phone.replaceAll("[^0-9+]", "");
        
        // If it's a 10-digit number, add +1 (US country code)
        if (cleaned.matches("^\\d{10}$")) {
            return "+1" + cleaned;
        }
        
        // If it starts with country code but no +, add +
        if (cleaned.matches("^1\\d{10}$")) {
            return "+" + cleaned;
        }
        
        return cleaned;
    }

    public String getPreviewMessage() {
        return previewMessage;
    }

    public void setPreviewMessage(String previewMessage) {
        this.previewMessage = previewMessage;
    }

    public String getDisplayInfo() {
        StringBuilder info = new StringBuilder();
        
        // Add phone number if available
        String phone = getPhoneNumber();
        if (phone != null) {
            info.append("Phone: ").append(phone).append("\n");
        }
        
        // Add first few data fields
        int count = 0;
        for (Map.Entry<String, String> entry : dataMap.entrySet()) {
            if (count >= 3) break; // Limit to first 3 fields
            if (!entry.getValue().isEmpty() && !entry.getKey().toLowerCase().contains("phone")) {
                info.append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
                count++;
            }
        }
        
        return info.toString().trim();
    }

    @Override
    public String toString() {
        return "CsvRowModel{" +
                "headers=" + headers +
                ", values=" + values +
                ", hasValidPhone=" + hasValidPhoneNumber() +
                '}';
    }
}
