package com.afriserve.smsmanager.data.csv

import android.content.ContentResolver
import android.net.Uri
import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Handles file operations for CSV/Excel uploads
 */
class CsvFileHandler(private val contentResolver: ContentResolver) {
    companion object {
        private const val TAG = "CsvFileHandler"
        private const val BUFFER_SIZE = 8192
    }
    
    /**
     * Read file content from URI
     * Supports CSV and Excel files
     */
    fun readFileContent(fileUri: Uri): String? {
        return try {
            val inputStream = contentResolver.openInputStream(fileUri) ?: return null
            BufferedReader(InputStreamReader(inputStream), BUFFER_SIZE).use { reader ->
                reader.readText()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading file", e)
            null
        }
    }
    
    /**
     * Check if file is supported format
     */
    fun isSupportedFormat(fileName: String): Boolean {
        val extension = fileName.substringAfterLast(".").lowercase()
        return extension in listOf("csv", "xlsx", "xls", "txt")
    }
    
    /**
     * Get file type from URI
     */
    fun getFileType(fileUri: Uri): String? {
        return contentResolver.getType(fileUri)
    }
    
    /**
     * Parse file and return CSV parsing result
     */
    fun parseFile(fileUri: Uri, fileName: String): CsvParsingResult {
        if (!isSupportedFormat(fileName)) {
            return CsvParsingResult(
                parseErrors = listOf("Unsupported file format. Supported: CSV, XLSX, XLS")
            )
        }
        
        val content = readFileContent(fileUri) ?: return CsvParsingResult(
            parseErrors = listOf("Failed to read file content")
        )
        
        // For Excel files, delegate to Excel parser if available
        // For now, treat as CSV
        val extension = fileName.substringAfterLast(".").lowercase()
        return when (extension) {
            "xlsx", "xls" -> parseExcelFile(content, fileName)
            else -> CsvParser.parseWithDetection(content)
        }
    }
    
    /**
     * Parse Excel file (currently treats as CSV after export)
     * In production, use Apache POI or similar library
     */
    private fun parseExcelFile(content: String, fileName: String): CsvParsingResult {
        Log.d(TAG, "Parsing Excel file: $fileName")
        // For now, treat Excel as CSV
        // In production, use proper Excel parsing library
        return CsvParser.parseWithDetection(content)
    }
}
