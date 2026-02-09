package com.bulksms.smsmanager.ui.csv

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bulksms.smsmanager.data.csv.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for CSV upload and bulk SMS functionality
 */
@HiltViewModel
class CsvUploadViewModel @Inject constructor(
    private val csvFileHandler: CsvFileHandler,
    private val csvPreviewService: CsvPreviewService
) : ViewModel() {
    
    // Parsing state
    private val _parsingResult = MutableLiveData<CsvParsingResult?>(null)
    val parsingResult: LiveData<CsvParsingResult?> = _parsingResult
    
    // Selected phone column
    private val _selectedPhoneColumnIndex = MutableLiveData<Int?>(null)
    val selectedPhoneColumnIndex: LiveData<Int?> = _selectedPhoneColumnIndex
    
    // Available placeholders
    private val _placeholders = MutableLiveData<List<String>>(emptyList())
    val placeholders: LiveData<List<String>> = _placeholders
    
    // Message template
    private val _messageTemplate = MutableLiveData<String>("")
    val messageTemplate: LiveData<String> = _messageTemplate
    
    // Preview messages
    private val _messagePreviews = MutableLiveData<List<String>>(emptyList())
    val messagePreviews: LiveData<List<String>> = _messagePreviews
    
    // Summary
    private val _summary = MutableLiveData<String>("")
    val summary: LiveData<String> = _summary
    
    // Phone validation result
    private val _phoneValidation = MutableLiveData<PhoneValidationResult?>(null)
    val phoneValidation: LiveData<PhoneValidationResult?> = _phoneValidation
    
    // Loading state
    private val _isLoading = MutableLiveData<Boolean>(false)
    val isLoading: LiveData<Boolean> = _isLoading
    
    // Error messages
    private val _error = MutableLiveData<String?>(null)
    val error: LiveData<String?> = _error
    
    /**
     * Parse CSV file from URI
     */
    fun parseFile(fileName: String, fileUri: android.net.Uri) {
        viewModelScope.launch(Dispatchers.Default) {
            _isLoading.postValue(true)
            _error.postValue(null)
            
            try {
                val result = csvFileHandler.parseFile(fileUri, fileName)
                _parsingResult.postValue(result)
                
                // Update placeholders
                _placeholders.postValue(result.headers.map { it.placeholder })
                
                // Set default phone column
                result.detectionResult?.detectedPhoneColumnIndex?.let {
                    _selectedPhoneColumnIndex.postValue(it)
                }
                
                // Create summary
                val summaryText = CsvPreviewService.createSummary(result, fileName)
                _summary.postValue(summaryText)
                
                // Validate phone numbers
                val validation = CsvPreviewService.validatePhoneNumbers(result.validRecipients)
                _phoneValidation.postValue(validation)
                
            } catch (e: Exception) {
                _error.postValue("Error parsing file: ${e.message}")
            } finally {
                _isLoading.postValue(false)
            }
        }
    }
    
    /**
     * Update message template and regenerate previews
     */
    fun updateMessageTemplate(template: String) {
        _messageTemplate.postValue(template)
        regeneratePreviews()
    }
    
    /**
     * Regenerate message previews with current template
     */
    fun regeneratePreviews() {
        val result = _parsingResult.value ?: return
        val template = _messageTemplate.value ?: return
        
        val previews = CsvPreviewService.generateMessagePreviews(
            template,
            result.validRecipients,
            count = 3
        )
        _messagePreviews.postValue(previews)
    }
    
    /**
     * Update selected phone column
     */
    fun setPhoneColumn(columnIndex: Int) {
        _selectedPhoneColumnIndex.postValue(columnIndex)
    }
    
    /**
     * Insert placeholder into message at cursor position
     */
    fun insertPlaceholder(placeholder: String, currentTemplate: String, cursorPosition: Int): String {
        val before = currentTemplate.substring(0, cursorPosition)
        val after = currentTemplate.substring(cursorPosition)
        return before + placeholder + after
    }
    
    /**
     * Get recipients ready to send
     */
    fun getRecipientsToSend(): List<CsvRecipient> {
        return _parsingResult.value?.validRecipients ?: emptyList()
    }
    
    /**
     * Clear all data
     */
    fun clear() {
        _parsingResult.postValue(null)
        _selectedPhoneColumnIndex.postValue(null)
        _placeholders.postValue(emptyList())
        _messageTemplate.postValue("")
        _messagePreviews.postValue(emptyList())
        _summary.postValue("")
        _phoneValidation.postValue(null)
        _error.postValue(null)
    }
}
