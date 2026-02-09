package com.afriserve.smsmanager.data.csv

/**
 * Represents a CSV column header with detection metadata
 */
data class CsvHeader(
    val columnIndex: Int,
    val name: String,
    val placeholder: String = "{${name.lowercase().replace(" ", "")}}"
) {
    companion object {
        /**
         * Common phone column name patterns
         */
        private val PHONE_PATTERNS = listOf(
            "phone", "mobile", "contact", "telephone", "tel",
            "cell", "cellphone", "number", "phonenumber", "phone_number",
            "mobilephone", "mobile_number", "contact_number"
        )

        /**
         * Check if this header looks like a phone column
         */
        fun isPhoneColumn(name: String): Boolean {
            val normalized = name.lowercase().trim()
            return PHONE_PATTERNS.any { normalized.contains(it) }
        }
    }
}

/**
 * Data class for CSV detection results
 */
data class CsvDetectionResult(
    val headers: List<CsvHeader>,
    val detectedPhoneColumnIndex: Int? = null,
    val detectedPhoneColumnName: String? = null,
    val hasMultiplePhoneColumns: Boolean = false,
    val potentialPhoneColumns: List<String> = emptyList(),
    val totalColumns: Int = 0,
    val summary: String = ""
) {
    fun getPlaceholders(): List<String> = headers.map { it.placeholder }
    
    fun getHeaderNames(): List<String> = headers.map { it.name }
}
