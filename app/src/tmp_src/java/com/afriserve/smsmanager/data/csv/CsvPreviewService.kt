package com.afriserve.smsmanager.data.csv

import android.util.Log

/**
 * Preview and validation service for CSV data
 */
class CsvPreviewService {
    companion object {
        private const val TAG = "CsvPreviewService"
        private const val PREVIEW_COUNT = 3
        
        /**
         * Generate message preview for first N recipients with data substitution
         */
        fun generateMessagePreviews(
            template: String,
            recipients: List<CsvRecipient>,
            count: Int = PREVIEW_COUNT
        ): List<String> {
            val previews = mutableListOf<String>()
            
            recipients.take(count).forEach { recipient ->
                var previewMessage = template
                
                // Replace all placeholders with actual values
                recipient.data.forEach { (key, value) ->
                    val placeholder = "{${key.lowercase().replace(" ", "")}}"
                    previewMessage = previewMessage.replace(placeholder, value, ignoreCase = true)
                }
                
                // Add phone number to preview
                val preview = "$previewMessage\n\n[To: ${recipient.phoneNumber}]"
                previews.add(preview)
            }
            
            return previews
        }
        
        /**
         * Validate phone numbers and return summary
         */
        fun validatePhoneNumbers(recipients: List<CsvRecipient>): PhoneValidationResult {
            val validNumbers = mutableListOf<String>()
            val invalidNumbers = mutableListOf<Pair<String, String>>()
            
            recipients.forEach { recipient ->
                if (isValidPhoneNumber(recipient.phoneNumber)) {
                    validNumbers.add(recipient.phoneNumber)
                } else {
                    invalidNumbers.add(recipient.phoneNumber to "Invalid format")
                }
            }
            
            return PhoneValidationResult(
                totalCount = recipients.size,
                validCount = validNumbers.size,
                invalidCount = invalidNumbers.size,
                validPhoneNumbers = validNumbers,
                invalidPhoneNumbers = invalidNumbers
            )
        }
        
        /**
         * Create summary message for upload result
         */
        fun createSummary(
            parsingResult: CsvParsingResult,
            fileName: String
        ): String {
            return buildString {
                appendLine("📊 CSV Upload Summary")
                appendLine("━".repeat(40))
                appendLine("File: $fileName")
                appendLine()
                appendLine("Recipients Detected: ${parsingResult.totalCount}")
                appendLine("✓ Valid Recipients: ${parsingResult.validCount}")
                appendLine("✗ Invalid Recipients: ${parsingResult.invalidCount}")
                appendLine()
                appendLine("Columns Available: ${parsingResult.columnCount}")
                if (parsingResult.detectionResult != null) {
                    appendLine("Phone Column: ${parsingResult.detectionResult.detectedPhoneColumnName ?: "Not detected"}")
                }
                appendLine()
                if (parsingResult.detectionResult?.potentialPhoneColumns?.size ?: 0 > 1) {
                    appendLine("⚠️  Multiple phone columns detected:")
                    parsingResult.detectionResult?.potentialPhoneColumns?.forEach {
                        appendLine("  • $it")
                    }
                }
                if (parsingResult.parseErrors.isNotEmpty()) {
                    appendLine()
                    appendLine("⚠️  Parsing errors:")
                    parsingResult.parseErrors.forEach {
                        appendLine("  • $it")
                    }
                }
            }
        }
        
        /**
         * Simple phone number validation
         */
        private fun isValidPhoneNumber(phoneNumber: String): Boolean {
            if (phoneNumber.isBlank()) return false
            
            val digitsOnly = phoneNumber
                .replace("+", "")
                .replace(" ", "")
                .replace("-", "")
                .replace("(", "")
                .replace(")", "")
                .replace(".", "")
            
            return digitsOnly.length in 10..15 && digitsOnly.all { it.isDigit() }
        }
    }
}

/**
 * Result of phone number validation
 */
data class PhoneValidationResult(
    val totalCount: Int,
    val validCount: Int,
    val invalidCount: Int,
    val validPhoneNumbers: List<String>,
    val invalidPhoneNumbers: List<Pair<String, String>>
) {
    val validityPercentage: Int
        get() = if (totalCount > 0) (validCount * 100) / totalCount else 0
}
