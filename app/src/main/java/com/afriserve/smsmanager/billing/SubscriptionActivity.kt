package com.afriserve.smsmanager.billing

import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.browser.customtabs.CustomTabsIntent
import androidx.lifecycle.lifecycleScope
import com.afriserve.smsmanager.databinding.ActivitySubscriptionBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Subscription Activity - Using Intasend Payment Links
 * READY TO USE - Just add this to your app!
 */
@AndroidEntryPoint
class SubscriptionActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySubscriptionBinding

    // Your actual Intasend payment links
    companion object {
        const val DAILY_PAYMENT_URL = "https://lipana.dev/pay/daily"
        const val WEEKLY_PAYMENT_URL = "https://lipana.dev/pay/weekly-subscription"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySubscriptionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
    }

    private fun setupUI() {
        // Daily Plan Button
        binding.btnSubscribeDaily.setOnClickListener {
            val phone = binding.etPhoneNumber.text.toString()
            if (validatePhoneNumber(phone)) {
                subscribeToPlan("Daily", 200, DAILY_PAYMENT_URL, phone)
            }
        }

        // Weekly Plan Button
        binding.btnSubscribeWeekly.setOnClickListener {
            val phone = binding.etPhoneNumber.text.toString()
            if (validatePhoneNumber(phone)) {
                subscribeToPlan("Weekly", 1000, WEEKLY_PAYMENT_URL, phone)
            }
        }

        // Check Status Button
        binding.btnCheckStatus.setOnClickListener {
            val phone = binding.etPhoneNumber.text.toString()
            if (validatePhoneNumber(phone)) {
                val formattedPhone = formatPhoneNumber(phone)
                saveLastPhoneOnly(formattedPhone)
                binding.btnCheckStatus.isEnabled = false
                lifecycleScope.launch {
                    val status = SubscriptionHelper.refreshSubscriptionStatus(
                        this@SubscriptionActivity,
                        true
                    )
                    binding.btnCheckStatus.isEnabled = true
                    showStatusDialog(status)
                }
            }
        }
    }

    private fun subscribeToPlan(planName: String, amount: Int, baseUrl: String, phone: String) {
        // Show confirmation dialog first
        AlertDialog.Builder(this)
            .setTitle("Subscribe to $planName Plan")
            .setMessage("You'll be charged KSH $amount $planName.\n\nYou'll receive an M-Pesa prompt on your phone to complete the payment.")
            .setPositiveButton("Continue") { _, _ ->
                openPaymentPage(baseUrl, phone, planName, amount)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun openPaymentPage(baseUrl: String, phone: String, planName: String, amount: Int) {
        try {
            // Format phone number
            val formattedPhone = formatPhoneNumber(phone)
            
            // Build URL with phone number
            val paymentUrl = "$baseUrl?phone=$formattedPhone"

            // Open in Chrome Custom Tab (better UX than browser)
            val builder = CustomTabsIntent.Builder()
            
            // Customize the Chrome Custom Tab
            builder.setShowTitle(true)
            builder.setStartAnimations(this, android.R.anim.slide_in_left, android.R.anim.slide_out_right)
            builder.setExitAnimations(this, android.R.anim.slide_in_left, android.R.anim.slide_out_right)
            
            // Set toolbar color (optional - use your brand color)
            // builder.setToolbarColor(ContextCompat.getColor(this, R.color.primary))

            val customTabsIntent = builder.build()
            customTabsIntent.launchUrl(this, Uri.parse(paymentUrl))

            // Show info message
            Toast.makeText(
                this,
                "Opening payment page. Check your phone for M-Pesa prompt.",
                Toast.LENGTH_LONG
            ).show()

            // Optional: Save subscription attempt to database
            saveSubscriptionAttempt(formattedPhone, planName, amount)

        } catch (e: Exception) {
            Toast.makeText(
                this,
                "Error opening payment page: ${e.message}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun validatePhoneNumber(phone: String): Boolean {
        if (phone.isBlank()) {
            binding.etPhoneNumber.error = "Phone number is required"
            return false
        }

        // Remove spaces and dashes
        val cleaned = phone.replace(Regex("[^0-9+]"), "")
        val digits = cleaned.replace("+", "")

        val valid = when {
            digits.startsWith("0") -> digits.length == 10 && (digits[1] == '7' || digits[1] == '1')
            digits.startsWith("254") -> digits.length == 12 && (digits[3] == '7' || digits[3] == '1')
            digits.startsWith("7") || digits.startsWith("1") -> digits.length == 9
            else -> false
        }

        if (!valid) {
            binding.etPhoneNumber.error = "Enter a valid Kenyan phone number"
        }

        return valid
    }

    private fun formatPhoneNumber(phone: String): String {
        // Remove all non-numeric characters except +
        var formatted = phone.replace(Regex("[^0-9+]"), "")
        
        // Remove + if present
        formatted = formatted.replace("+", "")

        // Convert to 254XXXXXXXXX format
        when {
            formatted.startsWith("0") && formatted.length == 10 -> {
                // 0712345678 -> 254712345678
                formatted = "254" + formatted.substring(1)
            }
            formatted.startsWith("254") && formatted.length == 12 -> {
                // Already in correct format
            }
            (formatted.startsWith("7") || formatted.startsWith("1")) && formatted.length == 9 -> {
                // 712345678 -> 254712345678
                formatted = "254$formatted"
            }
        }

        return formatted
    }

    private fun saveSubscriptionAttempt(phone: String, plan: String, amount: Int) {
        // TODO: Save to your database (Room/Firestore)
        // This helps you track who attempted to subscribe
        
        // Example with SharedPreferences (for now):
        val prefs = getSharedPreferences("subscriptions", MODE_PRIVATE)
        prefs.edit().apply {
            putString("last_phone", phone)
            putString("last_plan", plan)
            putInt("last_amount", amount)
            putLong("last_attempt", System.currentTimeMillis())
            apply()
        }
    }

    private fun saveLastPhoneOnly(phone: String) {
        val prefs = getSharedPreferences("subscriptions", MODE_PRIVATE)
        prefs.edit().putString("last_phone", phone).apply()
    }

    override fun onResume() {
        super.onResume()
        // User returned from payment page
        // You can show a "Thank you" message or check payment status
        checkIfReturningFromPayment()
    }

    private fun checkIfReturningFromPayment() {
        val prefs = getSharedPreferences("subscriptions", MODE_PRIVATE)
        val lastAttempt = prefs.getLong("last_attempt", 0)
        
        // If attempted payment in last 5 minutes
        if (System.currentTimeMillis() - lastAttempt < 5 * 60 * 1000) {
            lifecycleScope.launch {
                val status = SubscriptionHelper.refreshSubscriptionStatus(this@SubscriptionActivity, true)
                val now = System.currentTimeMillis()
                val active = status.premium && (status.paidUntilMillis == null || status.paidUntilMillis > now)

                if (active) {
                    AlertDialog.Builder(this@SubscriptionActivity)
                        .setTitle("Payment Confirmed")
                        .setMessage("Your subscription is active. You can start using the app now.")
                        .setPositiveButton("Start Using App") { _, _ ->
                            finish()
                        }
                        .show()

                    prefs.edit().remove("last_attempt").apply()
                } else {
                    AlertDialog.Builder(this@SubscriptionActivity)
                        .setTitle("Payment Processing")
                        .setMessage("We are still waiting for confirmation. You can refresh status or try again.")
                        .setPositiveButton("Refresh Status") { _, _ ->
                            checkIfReturningFromPayment()
                        }
                        .setNegativeButton("Try Again", null)
                        .show()
                }
            }
        }
    }

    private fun showStatusDialog(status: SubscriptionHelper.SubscriptionStatus) {
        val now = System.currentTimeMillis()
        val isActive = status.premium && (status.paidUntilMillis == null || status.paidUntilMillis > now)

        if (isActive) {
            val planLabel = status.plan?.trim()?.replaceFirstChar { it.uppercaseChar() }
            val paidUntil = formatPaidUntil(status.paidUntilMillis)
            val message = buildString {
                append("Premium Active")
                if (!planLabel.isNullOrBlank()) {
                    append(" - ").append(planLabel)
                }
                if (!paidUntil.isNullOrBlank()) {
                    append("\nPaid until: ").append(paidUntil)
                }
            }
            AlertDialog.Builder(this)
                .setTitle("Subscription Status")
                .setMessage(message)
                .setPositiveButton("OK", null)
                .show()
            return
        }

        AlertDialog.Builder(this)
            .setTitle("Subscription Status")
            .setMessage("No active subscription found for this number. If you paid recently, wait a few minutes and refresh.")
            .setPositiveButton("OK", null)
            .show()
    }

    private fun formatPaidUntil(paidUntilMillis: Long?): String? {
        if (paidUntilMillis == null || paidUntilMillis <= 0L) return null
        val formatter = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.getDefault())
        val date = Instant.ofEpochMilli(paidUntilMillis)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
        return formatter.format(date)
    }
}
