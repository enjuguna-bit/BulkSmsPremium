package com.afriserve.smsmanager.data.csv

import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlin.math.min

/**
 * CSV parser with support for multiple delimiters and format detection
 */
class CsvParser {
    companion object {
        private const val TAG = "CsvParser"
        private const val MAX_ROWS_TO_CHECK = 100
        
        /**
         * Detect the delimiter used in CSV file
         * Returns most likely delimiter: comma, semicolon, or tab
         */
        fun detectDelimiter(firstLine: String): Char {
            val commaCount = firstLine.count { it == ',' }
            val semicolonCount = firstLine.count { it == ';' }
            val tabCount = firstLine.count { it == '\t' }
            
            return when {
                semicolonCount > commaCount && semicolonCount > tabCount -> ';'
                tabCount > commaCount -> '\t'
                else -> ','
            }
        }
        
        /**
         * Parse CSV content and detect headers with phone column
         * @param content CSV file content as string
         * @param delimiter CSV delimiter (auto-detected if null)
         * @return CsvParsingResult with detection metadata
         */
        fun parseWithDetection(content: String, delimiter: Char? = null): CsvParsingResult {
            try {
                val lines = content.split("\n").filter { it.isNotBlank() }
                if (lines.isEmpty()) {
                    return CsvParsingResult(
                        parseErrors = listOf("CSV file is empty")
                    )
                }
                
                // Detect delimiter
                val actualDelimiter = delimiter ?: detectDelimiter(lines[0])
                Log.d(TAG, "Detected delimiter: '$actualDelimiter'")
                
                // Parse header row
                val headerLine = lines[0]
                val headerNames = parseRow(headerLine, actualDelimiter)
                if (headerNames.isEmpty()) {
                    return CsvParsingResult(
                        parseErrors = listOf("Could not parse CSV headers")
                    )
                }
                
                // Create CsvHeader objects
                val headers = headerNames.mapIndexed { index, name ->
                    CsvHeader(columnIndex = index, name = name.trim())
                }
                
                // Detect phone column
                val detectionResult = detectPhoneColumn(headers)
                
                // Parse data rows
                val recipients = mutableListOf<CsvRecipient>()
                val validRecipients = mutableListOf<CsvRecipient>()
                val invalidRecipients = mutableListOf<Pair<String, String>>()
                
                for (i in 1 until lines.size) {
                    val line = lines[i].trim()
                    if (line.isEmpty()) continue
                    
                    try {
                        val values = parseRow(line, actualDelimiter)
                        if (values.isEmpty()) continue
                        
                        // Extract phone number
                        val phoneNumber = if (detectionResult.detectedPhoneColumnIndex != null) {
                            val idx = detectionResult.detectedPhoneColumnIndex
                            if (idx < values.size) values[idx].trim() else ""
                        } else {
                            ""
                        }
                        
                        // Create data map
                        val dataMap = mutableMapOf<String, String>()
                        for (j in headerNames.indices) {
                            if (j < values.size) {
                                dataMap[headerNames[j].trim()] = values[j].trim()
                            }
                        }
                        
                        val recipient = CsvRecipient(phoneNumber = phoneNumber, data = dataMap)
                        recipients.add(recipient)
                        
                        // Validate phone number
                        if (isValidPhoneNumber(phoneNumber)) {
                            validRecipients.add(recipient)
                        } else {
                            invalidRecipients.add(phoneNumber to "Invalid phone number format")
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Error parsing line $i: ${e.message}")
                        invalidRecipients.add(line to e.message.orEmpty())
                    }
                }
                
                return CsvParsingResult(
                    recipients = recipients,
                    validRecipients = validRecipients,
                    invalidRecipients = invalidRecipients,
                    headers = headers,
                    detectionResult = detectionResult
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing CSV", e)
                return CsvParsingResult(
                    parseErrors = listOf("CSV parsing failed: ${e.message}")
                )
            }
        }
        
        /**
         * Parse a single CSV row respecting quoted fields
         */
        private fun parseRow(line: String, delimiter: Char): List<String> {
            val fields = mutableListOf<String>()
            var currentField = StringBuilder()
            var inQuotes = false
            var i = 0
            
            while (i < line.length) {
                val ch = line[i]
                
                when {
                    ch == '"' -> {
                        inQuotes = !inQuotes
                        i++
                    }
                    ch == delimiter && !inQuotes -> {
                        fields.add(currentField.toString())
                        currentField = StringBuilder()
                        i++
                    }
                    else -> {
                        currentField.append(ch)
                        i++
                    }
                }
            }
            
            fields.add(currentField.toString())
            return fields
        }
        
        /**
         * Detect which column likely contains phone numbers
         */
        private fun detectPhoneColumn(headers: List<CsvHeader>): CsvDetectionResult {
            val potentialPhoneColumns = mutableListOf<String>()
            var detectedIndex: Int? = null
            
            headers.forEach { header ->
                if (CsvHeader.isPhoneColumn(header.name)) {
                    potentialPhoneColumns.add(header.name)
                    if (detectedIndex == null) {
                        detectedIndex = header.columnIndex
                    }
                }
            }
            
            val summary = if (detectedIndex != null) {
                "Auto-detected phone column: '${headers[detectedIndex!!].name}'"
            } else {
                "No phone column detected - manual selection required"
            }
            
            return CsvDetectionResult(
                headers = headers,
                detectedPhoneColumnIndex = detectedIndex,
                detectedPhoneColumnName = detectedIndex?.let { headers[it].name },
                hasMultiplePhoneColumns = potentialPhoneColumns.size > 1,
                potentialPhoneColumns = potentialPhoneColumns,
                totalColumns = headers.size,
                summary = summary
            )
        }
        
        /**
         * Simple phone number validation
         * Accepts: 10-15 digits, with optional +, spaces, dashes, parentheses
         */
        private fun isValidPhoneNumber(phoneNumber: String): Boolean {
            if (phoneNumber.isBlank()) return false
            
            // Remove common formatting characters
            val digitsOnly = phoneNumber
                .replace("+", "")
                .replace(" ", "")
                .replace("-", "")
                .replace("(", "")
                .replace(")", "")
                .replace(".", "")
            
            // Must have 10-15 digits
            return digitsOnly.length in 10..15 && digitsOnly.all { it.isDigit() }
        }
    }
}
