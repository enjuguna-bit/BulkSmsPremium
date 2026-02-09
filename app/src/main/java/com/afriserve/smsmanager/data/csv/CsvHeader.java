package com.afriserve.smsmanager.data.csv;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.Arrays;
import java.util.List;

/**
 * Represents a CSV column header with detection metadata
 */
public class CsvHeader implements Parcelable {
    private int columnIndex;
    private String name;
    private String placeholder;

    public CsvHeader(int columnIndex, String name) {
        this.columnIndex = columnIndex;
        this.name = name;
        this.placeholder = "{" + name.toLowerCase().replace(" ", "") + "}";
    }

    protected CsvHeader(Parcel in) {
        columnIndex = in.readInt();
        name = in.readString();
        placeholder = in.readString();
    }

    public static final Creator<CsvHeader> CREATOR = new Creator<CsvHeader>() {
        @Override
        public CsvHeader createFromParcel(Parcel in) {
            return new CsvHeader(in);
        }

        @Override
        public CsvHeader[] newArray(int size) {
            return new CsvHeader[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(columnIndex);
        dest.writeString(name);
        dest.writeString(placeholder);
    }

    public int getColumnIndex() {
        return columnIndex;
    }

    public void setColumnIndex(int columnIndex) {
        this.columnIndex = columnIndex;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
        this.placeholder = "{" + name.toLowerCase().replace(" ", "") + "}";
    }

    public String getPlaceholder() {
        return placeholder;
    }

    /**
     * Common phone column name patterns
     */
    private static final List<String> PHONE_PATTERNS = Arrays.asList(
            "phone", "mobile", "contact", "telephone", "tel",
            "cell", "cellphone", "number", "phonenumber", "phone_number",
            "mobilephone", "mobile_number", "contact_number");

    /**
     * Check if this header looks like a phone column
     */
    public static boolean isPhoneColumn(String name) {
        String normalized = name.toLowerCase().trim();
        for (String pattern : PHONE_PATTERNS) {
            if (normalized.contains(pattern)) {
                return true;
            }
        }
        return false;
    }
}
