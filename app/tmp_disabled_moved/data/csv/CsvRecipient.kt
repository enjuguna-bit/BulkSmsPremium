package com.bulksms.smsmanager.data.csv

/**
 * Represents a recipient extracted from CSV data
 */
data class CsvRecipient(
    val phoneNumber: String,
    val data: Map<String, String> = emptyMap()
) {
    fun getFieldValue(placeholder: String): String? {
        // Remove braces: {firstName} -> firstName
        val fieldName = placeholder.replace("{", "").replace("}", "").lowercase()
        return data.entries.find { it.key.lowercase().replace(" ", "") == fieldName }?.value
    }
}

/**
 * Result of CSV parsing with recipients and metadata
 */
data class CsvParsingResult(
    val recipients: List<CsvRecipient> = emptyList(),
    val validRecipients: List<CsvRecipient> = emptyList(),
    val invalidRecipients: List<Pair<String, String>> = emptyList(), // (data, reason)
    val headers: List<CsvHeader> = emptyList(),
    val detectionResult: CsvDetectionResult? = null,
    val parseErrors: List<String> = emptyList()
) {
    val validCount: Int get() = validRecipients.size
    val invalidCount: Int get() = invalidRecipients.size
    val totalCount: Int get() = recipients.size
    val columnCount: Int get() = headers.size
    val phoneColumnName: String? get() = detectionResult?.detectedPhoneColumnName
}
