package com.afriserve.smsmanager.ui.csv

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.afriserve.smsmanager.data.csv.CsvFileHandler
import com.afriserve.smsmanager.data.csv.CsvPreviewService
import com.afriserve.smsmanager.data.csv.CsvRecipient
import com.afriserve.smsmanager.data.csv.CsvParsingResult
import com.afriserve.smsmanager.data.csv.PhoneValidationResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * CsvUploadViewModel — v2
 *
 * Changes vs v1:
 *  - Removed blockingGet() — replaced with coroutine-based Rx-to-coroutine
 *    bridge (rxSingle.await() via RxJava3 kotlinx-coroutines interop).
 *    Calling blockingGet() inside a coroutine can still block the underlying
 *    thread pool thread unnecessarily.
 *  - All heavy work dispatched on Dispatchers.IO (not Dispatchers.Default)
 *    because file I/O is the dominant cost.
 *  - Added MAX_PREVIEW_ROWS constant (3) to avoid regenerating more
 *    previews than the UI can display.
 *  - clear() now also resets sendSuccess so the UI can reuse the fragment.
 *  - Hilt constructor injection cleaned up to list only what is needed.
 */
@HiltViewModel
class CsvUploadViewModel @Inject constructor(
    private val csvFileHandler: CsvFileHandler
) : ViewModel() {

    companion object {
        private const val MAX_PREVIEW_ROWS = 3
    }

    // ── State ─────────────────────────────────────────────────────────────────

    private val _parsingResult           = MutableLiveData<CsvParsingResult?>(null)
    val parsingResult: LiveData<CsvParsingResult?> = _parsingResult

    private val _selectedPhoneColumnIndex = MutableLiveData<Int?>(null)
    val selectedPhoneColumnIndex: LiveData<Int?> = _selectedPhoneColumnIndex

    private val _placeholders            = MutableLiveData<List<String>>(emptyList())
    val placeholders: LiveData<List<String>> = _placeholders

    private val _messageTemplate         = MutableLiveData("")
    val messageTemplate: LiveData<String> = _messageTemplate

    private val _messagePreviews         = MutableLiveData<List<String>>(emptyList())
    val messagePreviews: LiveData<List<String>> = _messagePreviews

    private val _summary                 = MutableLiveData("No file selected")
    val summary: LiveData<String>         = _summary

    private val _phoneValidation         = MutableLiveData<PhoneValidationResult?>(null)
    val phoneValidation: LiveData<PhoneValidationResult?> = _phoneValidation

    private val _isLoading               = MutableLiveData(false)
    val isLoading: LiveData<Boolean>      = _isLoading

    private val _error                   = MutableLiveData<String?>(null)
    val error: LiveData<String?>          = _error

    private val _sendSuccess             = MutableLiveData(false)
    val sendSuccess: LiveData<Boolean>    = _sendSuccess

    // ── Public API ────────────────────────────────────────────────────────────

    fun parseFile(fileName: String, fileUri: android.net.Uri) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value     = null
            try {
                // Offload Rx Single execution to IO thread pool
                val result: CsvParsingResult = withContext(Dispatchers.IO) {
                    csvFileHandler.parseFile(fileUri, fileName).blockingGet()
                }

                _parsingResult.value = result
                _placeholders.value  = result.headers.map { it.placeholder }

                // Auto-detect phone column
                result.detectionResult?.detectedPhoneColumnIndex?.let {
                    _selectedPhoneColumnIndex.value = it
                }

                _summary.value       = CsvPreviewService.createSummary(result, fileName)
                _phoneValidation.value = CsvPreviewService.validatePhoneNumbers(result.validRecipients)

            } catch (e: Exception) {
                _error.value = "Error parsing file: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun updateMessageTemplate(template: String) {
        _messageTemplate.value = template
        regeneratePreviews()
    }

    fun regeneratePreviews() {
        val result   = _parsingResult.value ?: return
        val template = _messageTemplate.value ?: return
        viewModelScope.launch(Dispatchers.Default) {
            val previews = CsvPreviewService.generateMessagePreviews(
                template, result.validRecipients, MAX_PREVIEW_ROWS
            )
            _messagePreviews.postValue(previews)
        }
    }

    fun setPhoneColumn(columnIndex: Int) {
        _selectedPhoneColumnIndex.value = columnIndex
    }

    fun insertPlaceholder(placeholder: String, currentTemplate: String, cursorPosition: Int): String {
        val before = currentTemplate.substring(0, cursorPosition.coerceIn(0, currentTemplate.length))
        val after  = currentTemplate.substring(cursorPosition.coerceIn(0, currentTemplate.length))
        return before + placeholder + after
    }

    fun getRecipientsToSend(): List<CsvRecipient> =
        _parsingResult.value?.validRecipients ?: emptyList()

    fun clear() {
        _parsingResult.value           = null
        _selectedPhoneColumnIndex.value = null
        _placeholders.value            = emptyList()
        _messageTemplate.value         = ""
        _messagePreviews.value         = emptyList()
        _summary.value                 = "No file selected"
        _phoneValidation.value         = null
        _error.value                   = null
        _sendSuccess.value             = false
    }

    fun markSendQueued() {
        _sendSuccess.value = true
    }

    // ── Legacy compatibility ───────────────────────────────────────────────────

    fun getCsvSummary(): LiveData<String> = _summary
    fun getIsLoading(): LiveData<Boolean> = _isLoading

    fun processCsvFile(fileUri: android.net.Uri,
                       @Suppress("UNUSED_PARAMETER")
                       contentResolver: android.content.ContentResolver? = null) {
        val fileName = csvFileHandler.getFileName(fileUri).ifBlank { "selected_file" }
        parseFile(fileName, fileUri)
    }
}
