package com.bulksms.smsmanager.ui.csv

import android.content.ContentResolver
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModelProvider
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.recyclerview.widget.LinearLayoutManager
import com.bulksms.smsmanager.R
import com.bulksms.smsmanager.data.csv.CsvFileHandler
import com.bulksms.smsmanager.data.csv.CsvPreviewService
import dagger.hilt.android.AndroidEntryPoint

/**
 * Fragment for CSV upload and bulk SMS configuration
 */
// @AndroidEntryPoint
class CsvUploadFragment : Fragment() {
    
    private val viewModel: CsvUploadViewModel by viewModels()
    private lateinit var filePickerLauncher: ActivityResultLauncher<String>
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize file picker
        filePickerLauncher = registerForActivityResult(
            ActivityResultContracts.GetContent()
        ) { uri ->
            if (uri != null) {
                val fileName = getFileName(uri)
                viewModel.parseFile(fileName, uri)
            }
        }
    }
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_csv_upload, container, false)
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupUI()
        observeViewModel()
    }
    
    private fun setupUI() {
        // File selection button
        view?.findViewById<View>(R.id.btn_select_csv)?.setOnClickListener {
            filePickerLauncher.launch("text/*")
        }
        
        // Placeholder dropdown
        view?.findViewById<View>(R.id.btn_insert_placeholder)?.setOnClickListener {
            showPlaceholderMenu()
        }
        
        // Message template input
        view?.findViewById<View>(R.id.et_message_template)?.also { editText ->
            editText.id // Reference the view
        }
        
        // Preview button
        view?.findViewById<View>(R.id.btn_preview)?.setOnClickListener {
            viewModel.regeneratePreviews()
        }
        
        // Send button
        view?.findViewById<View>(R.id.btn_send)?.setOnClickListener {
            sendBulkSms()
        }
    }
    
    private fun observeViewModel() {
        // Summary
        viewModel.summary.observe(viewLifecycleOwner) { summary ->
            view?.findViewById<android.widget.TextView>(R.id.tv_summary)?.text = summary
        }
        
        // Placeholders
        viewModel.placeholders.observe(viewLifecycleOwner) { placeholders ->
            // Update placeholder dropdown or chip group
            updatePlaceholderUI(placeholders)
        }
        
        // Message previews
        viewModel.messagePreviews.observe(viewLifecycleOwner) { previews ->
            displayPreviews(previews)
        }
        
        // Phone validation
        viewModel.phoneValidation.observe(viewLifecycleOwner) { validation ->
            validation?.let {
                val validationText = "Valid: ${it.validCount}/${it.totalCount} (${it.validityPercentage}%)"
                view?.findViewById<android.widget.TextView>(R.id.tv_phone_validation)?.text = validationText
            }
        }
        
        // Loading state
        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            view?.findViewById<View>(R.id.progress_loading)?.visibility =
                if (isLoading) View.VISIBLE else View.GONE
        }
        
        // Error messages
        viewModel.error.observe(viewLifecycleOwner) { error ->
            error?.let {
                Toast.makeText(requireContext(), it, Toast.LENGTH_LONG).show()
            }
        }
    }
    
    private fun updatePlaceholderUI(placeholders: List<String>) {
        // Implementation would update UI components with available placeholders
        // Example: Show as chips or in a dropdown menu
    }
    
    private fun showPlaceholderMenu() {
        val placeholders = viewModel.placeholders.value ?: return
        if (placeholders.isEmpty()) {
            Toast.makeText(requireContext(), "No placeholders available. Upload a CSV first.", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Create simple dialog with placeholder selection
        // In production, use a proper dialog or bottom sheet
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Insert Placeholder")
            .setItems(placeholders.toTypedArray()) { _, which ->
                val placeholder = placeholders[which]
                val editText = view?.findViewById<android.widget.EditText>(R.id.et_message_template)
                editText?.text?.insert(editText.selectionStart, placeholder)
            }
            .show()
    }
    
    private fun displayPreviews(previews: List<String>) {
        val previewContainer = view?.findViewById<android.widget.LinearLayout>(R.id.ll_preview_container)
        previewContainer?.removeAllViews()
        
        previews.forEachIndexed { index, preview ->
            val previewView = android.widget.TextView(requireContext()).apply {
                text = preview
                setPadding(16, 8, 16, 8)
                setBackgroundColor(android.graphics.Color.parseColor("#f5f5f5"))
                layoutParams = android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, 8, 0, 8)
                }
            }
            previewContainer?.addView(previewView)
        }
    }
    
    private fun sendBulkSms() {
        val template = viewModel.messageTemplate.value
        val recipients = viewModel.getRecipientsToSend()
        
        if (template.isNullOrBlank()) {
            Toast.makeText(requireContext(), "Please enter a message template", Toast.LENGTH_SHORT).show()
            return
        }
        
        if (recipients.isEmpty()) {
            Toast.makeText(requireContext(), "No valid recipients", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Proceed with sending
        Toast.makeText(requireContext(), "Sending to ${recipients.size} recipients", Toast.LENGTH_SHORT).show()
        // TODO: Implement actual bulk SMS sending
    }
    
    private fun getFileName(uri: android.net.Uri): String {
        var result = ""
        requireContext().contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val displayNameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (displayNameIndex >= 0) {
                    result = cursor.getString(displayNameIndex)
                }
            }
        }
        return result
    }
}
