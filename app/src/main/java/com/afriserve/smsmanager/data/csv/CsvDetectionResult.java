package com.afriserve.smsmanager.data.csv;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;

/**
 * Data class for CSV detection results
 */
public class CsvDetectionResult implements Parcelable {
    private List<CsvHeader> headers;
    private Integer detectedPhoneColumnIndex;
    private String detectedPhoneColumnName;
    private boolean hasMultiplePhoneColumns;
    private List<String> potentialPhoneColumns;
    private int totalColumns;
    private String summary;

    public CsvDetectionResult() {
        // Default constructor
    }

    public CsvDetectionResult(List<CsvHeader> headers, Integer detectedPhoneColumnIndex,
            String detectedPhoneColumnName, boolean hasMultiplePhoneColumns,
            List<String> potentialPhoneColumns, int totalColumns, String summary) {
        this.headers = headers;
        this.detectedPhoneColumnIndex = detectedPhoneColumnIndex;
        this.detectedPhoneColumnName = detectedPhoneColumnName;
        this.hasMultiplePhoneColumns = hasMultiplePhoneColumns;
        this.potentialPhoneColumns = potentialPhoneColumns;
        this.totalColumns = totalColumns;
        this.summary = summary;
    }

    protected CsvDetectionResult(Parcel in) {
        headers = in.createTypedArrayList(CsvHeader.CREATOR);
        if (in.readByte() == 0) {
            detectedPhoneColumnIndex = null;
        } else {
            detectedPhoneColumnIndex = in.readInt();
        }
        detectedPhoneColumnName = in.readString();
        hasMultiplePhoneColumns = in.readByte() != 0;
        potentialPhoneColumns = in.createStringArrayList();
        totalColumns = in.readInt();
        summary = in.readString();
    }

    public static final Creator<CsvDetectionResult> CREATOR = new Creator<CsvDetectionResult>() {
        @Override
        public CsvDetectionResult createFromParcel(Parcel in) {
            return new CsvDetectionResult(in);
        }

        @Override
        public CsvDetectionResult[] newArray(int size) {
            return new CsvDetectionResult[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeTypedList(headers);
        if (detectedPhoneColumnIndex == null) {
            dest.writeByte((byte) 0);
        } else {
            dest.writeByte((byte) 1);
            dest.writeInt(detectedPhoneColumnIndex);
        }
        dest.writeString(detectedPhoneColumnName);
        dest.writeByte((byte) (hasMultiplePhoneColumns ? 1 : 0));
        dest.writeStringList(potentialPhoneColumns);
        dest.writeInt(totalColumns);
        dest.writeString(summary);
    }

    public List<CsvHeader> getHeaders() {
        return headers;
    }

    public void setHeaders(List<CsvHeader> headers) {
        this.headers = headers;
    }

    public Integer getDetectedPhoneColumnIndex() {
        return detectedPhoneColumnIndex;
    }

    public void setDetectedPhoneColumnIndex(Integer detectedPhoneColumnIndex) {
        this.detectedPhoneColumnIndex = detectedPhoneColumnIndex;
    }

    public String getDetectedPhoneColumnName() {
        return detectedPhoneColumnName;
    }

    public void setDetectedPhoneColumnName(String detectedPhoneColumnName) {
        this.detectedPhoneColumnName = detectedPhoneColumnName;
    }

    public boolean isHasMultiplePhoneColumns() {
        return hasMultiplePhoneColumns;
    }

    public void setHasMultiplePhoneColumns(boolean hasMultiplePhoneColumns) {
        this.hasMultiplePhoneColumns = hasMultiplePhoneColumns;
    }

    public List<String> getPotentialPhoneColumns() {
        return potentialPhoneColumns;
    }

    public void setPotentialPhoneColumns(List<String> potentialPhoneColumns) {
        this.potentialPhoneColumns = potentialPhoneColumns;
    }

    public int getTotalColumns() {
        return totalColumns;
    }

    public void setTotalColumns(int totalColumns) {
        this.totalColumns = totalColumns;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public List<String> getPlaceholders() {
        if (headers == null)
            return Arrays.asList();
        List<String> placeholders = new ArrayList<>();
        for (CsvHeader header : headers) {
            placeholders.add(header.getPlaceholder());
        }
        return placeholders;
    }

    public List<String> getHeaderNames() {
        if (headers == null)
            return Arrays.asList();
        List<String> names = new ArrayList<>();
        for (CsvHeader header : headers) {
            names.add(header.getName());
        }
        return names;
    }
}
