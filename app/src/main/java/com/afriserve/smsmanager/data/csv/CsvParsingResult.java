package com.afriserve.smsmanager.data.csv;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.List;

/**
 * Result of CSV parsing with recipients and metadata
 */
public class CsvParsingResult implements Parcelable {
    private List<CsvRecipient> recipients;
    private List<CsvRecipient> validRecipients;
    private List<String> invalidRecipients; // Simplified to just store reasons
    private List<CsvHeader> headers;
    private CsvDetectionResult detectionResult;
    private List<String> parseErrors;

    public CsvParsingResult() {
        // Default constructor
    }

    public CsvParsingResult(List<CsvRecipient> recipients, List<CsvRecipient> validRecipients,
            List<String> invalidRecipients, List<CsvHeader> headers,
            CsvDetectionResult detectionResult, List<String> parseErrors) {
        this.recipients = recipients;
        this.validRecipients = validRecipients;
        this.invalidRecipients = invalidRecipients;
        this.headers = headers;
        this.detectionResult = detectionResult;
        this.parseErrors = parseErrors;
    }

    protected CsvParsingResult(Parcel in) {
        recipients = in.createTypedArrayList(CsvRecipient.CREATOR);
        validRecipients = in.createTypedArrayList(CsvRecipient.CREATOR);
        invalidRecipients = in.createStringArrayList();
        headers = in.createTypedArrayList(CsvHeader.CREATOR);
        detectionResult = in.readParcelable(CsvDetectionResult.class.getClassLoader());
        parseErrors = in.createStringArrayList();
    }

    public static final Creator<CsvParsingResult> CREATOR = new Creator<CsvParsingResult>() {
        @Override
        public CsvParsingResult createFromParcel(Parcel in) {
            return new CsvParsingResult(in);
        }

        @Override
        public CsvParsingResult[] newArray(int size) {
            return new CsvParsingResult[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeTypedList(recipients);
        dest.writeTypedList(validRecipients);
        dest.writeStringList(invalidRecipients);
        dest.writeTypedList(headers);
        dest.writeParcelable(detectionResult, flags);
        dest.writeStringList(parseErrors);
    }

    public List<CsvRecipient> getRecipients() {
        return recipients;
    }

    public void setRecipients(List<CsvRecipient> recipients) {
        this.recipients = recipients;
    }

    public List<CsvRecipient> getValidRecipients() {
        return validRecipients;
    }

    public void setValidRecipients(List<CsvRecipient> validRecipients) {
        this.validRecipients = validRecipients;
    }

    public List<String> getInvalidRecipients() {
        return invalidRecipients;
    }

    public void setInvalidRecipients(List<String> invalidRecipients) {
        this.invalidRecipients = invalidRecipients;
    }

    public List<CsvHeader> getHeaders() {
        return headers;
    }

    public void setHeaders(List<CsvHeader> headers) {
        this.headers = headers;
    }

    public CsvDetectionResult getDetectionResult() {
        return detectionResult;
    }

    public void setDetectionResult(CsvDetectionResult detectionResult) {
        this.detectionResult = detectionResult;
    }

    public List<String> getParseErrors() {
        return parseErrors;
    }

    public void setParseErrors(List<String> parseErrors) {
        this.parseErrors = parseErrors;
    }

    // Computed properties
    public int getValidCount() {
        return validRecipients != null ? validRecipients.size() : 0;
    }

    public int getInvalidCount() {
        return invalidRecipients != null ? invalidRecipients.size() : 0;
    }

    public int getTotalCount() {
        return recipients != null ? recipients.size() : 0;
    }

    public int getColumnCount() {
        return headers != null ? headers.size() : 0;
    }

    public String getPhoneColumnName() {
        return detectionResult != null ? detectionResult.getDetectedPhoneColumnName() : null;
    }
}
