package com.afriserve.smsmanager.billing

import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.afriserve.smsmanager.R
import com.afriserve.smsmanager.databinding.ActivitySubscriptionBinding
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * SubscriptionActivity — v3
 *
 * Plans (price → duration):
 *   KSH  60  →  6 hours   → lipana.dev/pay/1hrmaster
 *   KSH 100  → 12 hours   → lipana.dev/pay/smartuser
 *   KSH 200  → 24 hours   → lipana.dev/pay/daily
 *   KSH 1000 → Weekly     → lipana.dev/pay/weekly-subscription
 *   KSH 3000 → Monthly    → lipana.dev/pay/cruisemaster
 *
 * 1-hour plan removed.
 */
@AndroidEntryPoint
class SubscriptionActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySubscriptionBinding
    private lateinit var selectedPlan: PlanOption

    data class PlanOption(
        val displayName: String,
        val planCode: String,
        val amount: Int,          // KES
        val paymentUrl: String
    )

    companion object {
        // ── Payment page URLs ───────────────────────────────────────────────
        const val SIX_HOUR_PAYMENT_URL    = "https://lipana.dev/pay/1hrmaster"
        const val TWELVE_HOUR_PAYMENT_URL = "https://lipana.dev/pay/smartuser"
        const val DAILY_PAYMENT_URL       = "https://lipana.dev/pay/daily"
        const val WEEKLY_PAYMENT_URL      = "https://lipana.dev/pay/weekly-subscription"
        const val MONTHLY_PAYMENT_URL     = "https://lipana.dev/pay/cruisemaster"

        /** Minimum ms after a payment attempt before we auto-check status on resume. */
        private const val POST_PAYMENT_CHECK_WINDOW_MS = 5 * 60 * 1000L  // 5 min
        /** How many times we retry subscription status check after returning from payment. */
        private const val CLAIM_MAX_RETRIES = 3
        private const val CLAIM_RETRY_DELAY_MS = 3_000L
    }

    /**
     * All plans in display order.  Update here (and in cloudflare-worker/src/index.js)
     * when prices or durations change.
     *
     * Plans:
     *   6 Hours   → KSH  60  (1hrmaster)
     *   12 Hours  → KSH 100  (smartuser)
     *   24 Hours  → KSH 200  (daily)
     *   Weekly    → KSH 1,000
     *   Monthly   → KSH 3,000 (cruisemaster)
     */
    private val plans: Map<Int, PlanOption> by lazy {
        mapOf(
            R.id.chipPlanSixHour    to PlanOption("6-Hour",  "six_hour",    60,   SIX_HOUR_PAYMENT_URL),
            R.id.chipPlanTwelveHour to PlanOption("12-Hour", "twelve_hour", 100,  TWELVE_HOUR_PAYMENT_URL),
            R.id.chipPlanDaily      to PlanOption("24-Hour", "daily",       200,  DAILY_PAYMENT_URL),
            R.id.chipPlanWeekly     to PlanOption("Weekly",  "weekly",      1000, WEEKLY_PAYMENT_URL),
            R.id.chipPlanMonthly    to PlanOption("Monthly", "monthly",     3000, MONTHLY_PAYMENT_URL)
        )
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySubscriptionBinding.inflate(layoutInflater)
        setContentView(binding.root)
        // Default selection: Weekly (best value)
        selectedPlan = plans.getValue(R.id.chipPlanWeekly)
        setupUI()
        renderSelectedPlan()
        // Restore last used phone number for convenience
        restoreLastPhone()
    }

    override fun onResume() {
        super.onResume()
        checkIfReturningFromPayment()
    }

    // ── UI setup ──────────────────────────────────────────────────────────────

    private fun setupUI() {
        binding.chipGroupPlans.setOnCheckedStateChangeListener { _, checkedIds ->
            val id = checkedIds.firstOrNull() ?: return@setOnCheckedStateChangeListener
            selectedPlan = plans[id] ?: selectedPlan
            renderSelectedPlan()
        }

        binding.btnStartPayment.setOnClickListener {
            val phone = binding.etPhoneNumber.text?.toString().orEmpty()
            if (validatePhoneNumber(phone)) startSubscriptionFlow(selectedPlan, phone)
        }

        binding.btnCheckStatus.setOnClickListener {
            val phone = binding.etPhoneNumber.text?.toString().orEmpty()
            if (!validatePhoneNumber(phone)) return@setOnClickListener
            saveLastPhoneOnly(formatPhoneNumber(phone))
            refreshStatus(forceRefresh = true)
        }
    }

    private fun renderSelectedPlan() {
        // Show both the plan name and the price so the user can confirm before paying
        val label = "${selectedPlan.displayName}  ·  KES ${selectedPlan.amount}"
        binding.txtSelectedPlanSummary.text = getString(R.string.subscription_plan_format, label)
    }

    private fun restoreLastPhone() {
        val prefs = getSharedPreferences("subscriptions", MODE_PRIVATE)
        val lastPhone = prefs.getString("last_phone", null) ?: return
        if (binding.etPhoneNumber.text.isNullOrBlank()) {
            binding.etPhoneNumber.setText(lastPhone)
        }
    }

    // ── Payment flow ──────────────────────────────────────────────────────────

    private fun startSubscriptionFlow(plan: PlanOption, phone: String) {
        val formattedPhone = formatPhoneNumber(phone)
        val deviceId = SubscriptionHelper.getDeviceId(this)
        setBusy(true, getString(R.string.subscription_loading_payment))

        lifecycleScope.launch {
            val intentId = SubscriptionHelper.createPaymentIntent(
                formattedPhone, deviceId, plan.planCode, plan.amount
            )
            if (intentId.isNullOrBlank()) {
                setBusy(false, null)
                showSnackbar(getString(R.string.subscription_payment_error))
                return@launch
            }
            SubscriptionHelper.saveLastIntent(this@SubscriptionActivity, intentId)
            val opened = openPaymentPage(plan, formattedPhone, deviceId, intentId)
            if (opened) saveSubscriptionAttempt(formattedPhone, plan.displayName, plan.amount)
            else SubscriptionHelper.clearLastIntent(this@SubscriptionActivity)
            setBusy(false, null)
        }
    }

    private fun openPaymentPage(
        plan: PlanOption,
        formattedPhone: String,
        deviceId: String,
        intentId: String
    ): Boolean {
        return try {
            val reference =
                "bulksms|intent=$intentId|plan=${plan.planCode}|device=$deviceId|phone=$formattedPhone"
            val paymentUrl = Uri.parse(plan.paymentUrl).buildUpon()
                .appendQueryParameter("phone",     formattedPhone)
                .appendQueryParameter("plan",      plan.planCode)
                .appendQueryParameter("amount",    plan.amount.toString())
                .appendQueryParameter("reference", reference)
                .appendQueryParameter("device_id", deviceId)
                .appendQueryParameter("intent_id", intentId)
                .build().toString()

            val colorParams = CustomTabColorSchemeParams.Builder()
                .setToolbarColor(ContextCompat.getColor(this, R.color.color_primary))
                .build()
            val cct = CustomTabsIntent.Builder()
                .setShowTitle(true)
                .setDefaultColorSchemeParams(colorParams)
                .build()
            cct.launchUrl(this, Uri.parse(paymentUrl))
            showSnackbar(getString(R.string.subscription_payment_opened))
            true
        } catch (_: Exception) {
            showSnackbar(getString(R.string.subscription_payment_error))
            false
        }
    }

    // ── Status refresh ────────────────────────────────────────────────────────

    private fun refreshStatus(forceRefresh: Boolean) {
        setBusy(true, getString(R.string.subscription_loading_status))
        lifecycleScope.launch {
            val status = SubscriptionHelper.refreshSubscriptionStatus(this@SubscriptionActivity, forceRefresh)
            setBusy(false, null)
            updateInlineStatus(status)
        }
    }

    /**
     * Called in [onResume] when the user returns from the payment browser tab.
     * Retries up to [CLAIM_MAX_RETRIES] times with [CLAIM_RETRY_DELAY_MS] back-off
     * to handle webhook processing latency.
     */
    private fun checkIfReturningFromPayment() {
        val prefs = getSharedPreferences("subscriptions", MODE_PRIVATE)
        val lastAttempt = prefs.getLong("last_attempt", 0)
        if (System.currentTimeMillis() - lastAttempt >= POST_PAYMENT_CHECK_WINDOW_MS) return

        lifecycleScope.launch {
            var retries = 0
            var activated = false
            val maxRetries = 6 // Increased retries to handle latency (approx 18-20 seconds total)

            while (retries < maxRetries && !activated) {
                if (retries > 0) {
                    setStatusUi("Verifying payment (Retry ${retries})...", R.color.status_info)
                    kotlinx.coroutines.delay(CLAIM_RETRY_DELAY_MS)
                }

                val status = SubscriptionHelper.refreshSubscriptionStatus(
                    this@SubscriptionActivity, true
                )
                val now = System.currentTimeMillis()
                activated = status.premium &&
                    (status.paidUntilMillis == null || status.paidUntilMillis > now)

                if (!activated) {
                    val intentId = SubscriptionHelper.getLastIntent(this@SubscriptionActivity)
                    if (!intentId.isNullOrBlank()) {
                        // Attempt to claim manually if the auto-check failed
                        val claim = SubscriptionHelper.claimSubscriptionWithIntent(
                            this@SubscriptionActivity, intentId
                        )
                        if (claim.success) {
                            val refreshed = SubscriptionHelper.refreshSubscriptionStatus(
                                this@SubscriptionActivity, true
                            )
                            activated = refreshed.premium
                            if (activated) updateInlineStatus(refreshed)
                        }
                    }
                }
                retries++
            }

            if (activated) {
                showSnackbar(getString(R.string.subscription_active)) { finish() }
                prefs.edit().remove("last_attempt").apply()
                SubscriptionHelper.clearLastIntent(this@SubscriptionActivity)
            } else {
                setStatusUi(getString(R.string.subscription_payment_pending), R.color.status_warning)
            }
        }
    }

    // ── Status display ────────────────────────────────────────────────────────

    private fun updateInlineStatus(status: SubscriptionHelper.SubscriptionStatus) {
        val now = System.currentTimeMillis()
        val isActive = status.premium &&
            (status.paidUntilMillis == null || status.paidUntilMillis > now)
        val planLabel = status.plan?.trim()
            ?.replace("_", " ")
            ?.replaceFirstChar { it.uppercaseChar() }
        val paidUntil = formatPaidUntil(status.paidUntilMillis)

        val message = when {
            isActive && !planLabel.isNullOrBlank() && !paidUntil.isNullOrBlank() ->
                getString(R.string.subscription_active_plan_and_time, planLabel, paidUntil)
            isActive && !paidUntil.isNullOrBlank() ->
                getString(R.string.subscription_active_time_only, paidUntil)
            isActive && !planLabel.isNullOrBlank() ->
                getString(R.string.subscription_active_plan_only, planLabel)
            isActive ->
                getString(R.string.subscription_active)
            SubscriptionHelper.isPaymentPending(this) ->
                getString(R.string.subscription_payment_pending)
            else ->
                getString(R.string.subscription_status_default)
        }
        val color = when {
            isActive                                   -> R.color.status_success
            SubscriptionHelper.isPaymentPending(this)  -> R.color.status_warning
            else                                       -> R.color.status_muted
        }
        setStatusUi(message, color)
    }

    private fun setStatusUi(message: String, colorRes: Int) {
        binding.statusCard.visibility = View.VISIBLE
        binding.txtInlineStatus.text  = message
        binding.txtInlineStatus.setTextColor(ContextCompat.getColor(this, colorRes))
        binding.statusCard.strokeColor = ContextCompat.getColor(this, colorRes)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun setBusy(busy: Boolean, statusMessage: String?) {
        binding.paymentProgress.visibility = if (busy) View.VISIBLE else View.GONE
        binding.btnStartPayment.isEnabled  = !busy
        binding.btnCheckStatus.isEnabled   = !busy
        if (busy && !statusMessage.isNullOrBlank()) setStatusUi(statusMessage, R.color.status_info)
    }

    /** Validates that the entered number looks like a Kenyan mobile. */
    private fun validatePhoneNumber(phone: String): Boolean {
        if (phone.isBlank()) {
            showPhoneError(getString(R.string.subscription_phone_required))
            return false
        }
        val digits = phone.replace(Regex("[^\\d]"), "")
        val valid = when {
            digits.startsWith("0")   && digits.length == 10
                && (digits[1] == '7' || digits[1] == '1') -> true
            digits.startsWith("254") && digits.length == 12
                && (digits[3] == '7' || digits[3] == '1') -> true
            (digits.startsWith("7") || digits.startsWith("1")) && digits.length == 9 -> true
            else -> false
        }
        if (!valid) showPhoneError(getString(R.string.subscription_phone_invalid))
        else binding.phoneInputLayout.error = null
        return valid
    }

    private fun showPhoneError(msg: String) {
        binding.phoneInputLayout.error = msg
        binding.subscriptionScroll.smoothScrollTo(0, 0)
        binding.etPhoneNumber.requestFocus()
        showSnackbar(msg)
    }

    /** Normalises any Kenyan format to 254XXXXXXXXX (no '+', no leading zero). */
    private fun formatPhoneNumber(phone: String): String {
        var d = phone.replace(Regex("[^\\d]"), "")
        return when {
            d.startsWith("0")   && d.length == 10 -> "254${d.substring(1)}"
            d.startsWith("254") && d.length == 12 -> d
            (d.startsWith("7") || d.startsWith("1")) && d.length == 9 -> "254$d"
            else -> d
        }
    }

    private fun saveSubscriptionAttempt(phone: String, plan: String, amount: Int) {
        getSharedPreferences("subscriptions", MODE_PRIVATE).edit().apply {
            putString("last_phone",   phone)
            putString("last_plan",    plan)
            putInt("last_amount",     amount)
            putLong("last_attempt",   System.currentTimeMillis())
            apply()
        }
    }

    private fun saveLastPhoneOnly(phone: String) {
        getSharedPreferences("subscriptions", MODE_PRIVATE).edit()
            .putString("last_phone", phone).apply()
    }

    private fun formatPaidUntil(paidUntilMillis: Long?): String? {
        if (paidUntilMillis == null || paidUntilMillis <= 0L) return null
        val formatter = DateTimeFormatter.ofPattern("MMM d, yyyy 'at' h:mm a", Locale.getDefault())
        return Instant.ofEpochMilli(paidUntilMillis)
            .atZone(ZoneId.systemDefault())
            .toLocalDateTime()
            .format(formatter)
    }

    private fun showSnackbar(message: String, action: (() -> Unit)? = null) {
        val sb = Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG)
        if (action != null) sb.setAction(R.string.subscription_close) { action() }
        sb.show()
    }
}
