package com.afriserve.smsmanager.data.csv;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.HashMap;
import java.util.Map;

/**
 * Represents a recipient extracted from CSV data
 */
public class CsvRecipient implements Parcelable {
    private String phoneNumber;
    private Map<String, String> data;

    public CsvRecipient(String phoneNumber, Map<String, String> data) {
        this.phoneNumber = phoneNumber;
        this.data = data;
    }

    protected CsvRecipient(Parcel in) {
        phoneNumber = in.readString();
        int dataSize = in.readInt();
        data = new HashMap<>(dataSize);
        for (int i = 0; i < dataSize; i++) {
            String key = in.readString();
            String value = in.readString();
            data.put(key, value);
        }
    }

    public static final Creator<CsvRecipient> CREATOR = new Creator<CsvRecipient>() {
        @Override
        public CsvRecipient createFromParcel(Parcel in) {
            return new CsvRecipient(in);
        }

        @Override
        public CsvRecipient[] newArray(int size) {
            return new CsvRecipient[size];
        }
    };

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public Map<String, String> getData() {
        return data;
    }

    public void setData(Map<String, String> data) {
        this.data = data;
    }

    public String getFieldValue(String placeholder) {
        // Remove braces: {firstName} -> firstName
        String fieldName = placeholder.replace("{", "").replace("}", "").toLowerCase();
        if (data == null)
            return null;

        for (Map.Entry<String, String> entry : data.entrySet()) {
            if (entry.getKey().toLowerCase().replace(" ", "").equals(fieldName)) {
                return entry.getValue();
            }
        }
        return null;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(phoneNumber);
        if (data != null) {
            dest.writeInt(data.size());
            for (Map.Entry<String, String> entry : data.entrySet()) {
                dest.writeString(entry.getKey());
                dest.writeString(entry.getValue());
            }
        } else {
            dest.writeInt(0);
        }
    }
}
