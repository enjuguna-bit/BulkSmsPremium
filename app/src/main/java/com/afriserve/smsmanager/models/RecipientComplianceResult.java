package com.afriserve.smsmanager.models;

import com.afriserve.smsmanager.data.compliance.ComplianceManager;

/**
 * Result of compliance checking for a specific recipient
 */
public class RecipientComplianceResult {
    private final Recipient recipient;
    private final ComplianceManager.ComplianceResult complianceResult;
    
    public RecipientComplianceResult(Recipient recipient, ComplianceManager.ComplianceResult complianceResult) {
        this.recipient = recipient;
        this.complianceResult = complianceResult;
    }
    
    public Recipient getRecipient() {
        return recipient;
    }
    
    public ComplianceManager.ComplianceResult getComplianceResult() {
        return complianceResult;
    }
}
