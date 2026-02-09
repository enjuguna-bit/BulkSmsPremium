package com.afriserve.smsmanager.data.csv;

import java.util.List;
import java.util.Arrays;

/**
 * Result of phone number validation
 */
public class PhoneValidationResult {
    private int totalCount;
    private int validCount;
    private int invalidCount;
    private List<String> validPhoneNumbers;
    private List<String> invalidPhoneNumbers;

    public PhoneValidationResult() {
        // Default constructor
    }

    public PhoneValidationResult(int totalCount, int validCount, int invalidCount, 
                                List<String> validPhoneNumbers, List<String> invalidPhoneNumbers) {
        this.totalCount = totalCount;
        this.validCount = validCount;
        this.invalidCount = invalidCount;
        this.validPhoneNumbers = validPhoneNumbers;
        this.invalidPhoneNumbers = invalidPhoneNumbers;
    }

    public int getTotalCount() {
        return totalCount;
    }

    public void setTotalCount(int totalCount) {
        this.totalCount = totalCount;
    }

    public int getValidCount() {
        return validCount;
    }

    public void setValidCount(int validCount) {
        this.validCount = validCount;
    }

    public int getInvalidCount() {
        return invalidCount;
    }

    public void setInvalidCount(int invalidCount) {
        this.invalidCount = invalidCount;
    }

    public List<String> getValidPhoneNumbers() {
        return validPhoneNumbers;
    }

    public void setValidPhoneNumbers(List<String> validPhoneNumbers) {
        this.validPhoneNumbers = validPhoneNumbers;
    }

    public List<String> getInvalidPhoneNumbers() {
        return invalidPhoneNumbers;
    }

    public void setInvalidPhoneNumbers(List<String> invalidPhoneNumbers) {
        this.invalidPhoneNumbers = invalidPhoneNumbers;
    }

    public int getValidityPercentage() {
        if (totalCount > 0) {
            return (validCount * 100) / totalCount;
        }
        return 0;
    }
}
