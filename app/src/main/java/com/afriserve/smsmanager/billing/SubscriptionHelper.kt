package com.afriserve.smsmanager.billing

import android.content.Context
import android.content.Intent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.time.Instant

/**
 * Helper object to launch subscription from anywhere in the app
 * Usage: SubscriptionHelper.launch(context)
 */
object SubscriptionHelper {

    private const val PREFS = "subscriptions"
    private const val STATUS_BASE_URL = "https://bulksmsbilling.enjuguna794.workers.dev/status"
    private const val STATUS_CACHE_MS = 5 * 60 * 1000L

    private const val KEY_LAST_PHONE = "last_phone"
    private const val KEY_LAST_ATTEMPT = "last_attempt"
    private const val KEY_PREMIUM_ACTIVE = "premium_active"
    private const val KEY_PREMIUM_PLAN = "premium_plan"
    private const val KEY_PREMIUM_UNTIL = "premium_until"
    private const val KEY_PREMIUM_LAST_CHECK = "premium_last_checked"

    private val httpClient: OkHttpClient by lazy { OkHttpClient() }

    data class SubscriptionStatus(
        val premium: Boolean,
        val plan: String?,
        val paidUntilMillis: Long?,
        val lastCheckedMillis: Long
    )
    
    /**
     * Launch SubscriptionActivity from any Activity or Fragment
     */
    fun launch(context: Context) {
        val intent = Intent(context, SubscriptionActivity::class.java)
        context.startActivity(intent)
    }
    
    /**
     * Check cached subscription state (fast, offline).
     * Use refreshSubscriptionStatus() to update from server.
     */
    fun hasActiveSubscription(context: Context): Boolean {
        val cached = getCachedStatus(context)
        if (!cached.premium) return false
        val now = System.currentTimeMillis()
        return cached.paidUntilMillis?.let { it > now } ?: true
    }

    /**
     * Returns true if a payment attempt was made very recently.
     * Useful for showing a "processing" state while waiting for webhook status.
     */
    fun isPaymentPending(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val lastAttempt = prefs.getLong(KEY_LAST_ATTEMPT, 0L)
        return lastAttempt > 0 && System.currentTimeMillis() - lastAttempt < 5 * 60 * 1000L
    }

    /**
     * Get cached status from SharedPreferences.
     */
    fun getCachedStatus(context: Context): SubscriptionStatus {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val premium = prefs.getBoolean(KEY_PREMIUM_ACTIVE, false)
        val plan = prefs.getString(KEY_PREMIUM_PLAN, null)
        val paidUntilRaw = prefs.getLong(KEY_PREMIUM_UNTIL, 0L)
        val paidUntil = if (paidUntilRaw > 0) paidUntilRaw else null
        val lastChecked = prefs.getLong(KEY_PREMIUM_LAST_CHECK, 0L)
        return SubscriptionStatus(premium, plan, paidUntil, lastChecked)
    }

    /**
     * Refresh status from the Cloudflare Worker and update cache.
     * When forceRefresh is false, a fresh cache returns immediately.
     */
    suspend fun refreshSubscriptionStatus(
        context: Context,
        forceRefresh: Boolean = false
    ): SubscriptionStatus = withContext(Dispatchers.IO) {
        val cached = getCachedStatus(context)
        val lastPhone = getLastPhone(context)

        if (lastPhone.isNullOrBlank()) {
            if (cached.lastCheckedMillis > 0) {
                return@withContext cached
            }
            val fallback = SubscriptionStatus(false, null, null, System.currentTimeMillis())
            saveStatusCache(context, fallback)
            return@withContext fallback
        }

        if (!forceRefresh &&
            cached.lastCheckedMillis > 0 &&
            System.currentTimeMillis() - cached.lastCheckedMillis < STATUS_CACHE_MS
        ) {
            return@withContext cached
        }

        val candidates = normalizePhoneCandidates(lastPhone)
        var latest: SubscriptionStatus? = null

        for (phone in candidates) {
            val status = fetchStatusFromServer(phone)
            if (status != null) {
                latest = status
                if (status.premium) break
            }
        }

        if (latest != null) {
            saveStatusCache(context, latest)
            return@withContext latest
        }

        // If the network fails, do not wipe a previously active cache.
        if (cached.lastCheckedMillis > 0) {
            return@withContext cached
        }

        val fallback = SubscriptionStatus(false, null, null, System.currentTimeMillis())
        saveStatusCache(context, fallback)
        return@withContext fallback
    }

    /**
     * Java-friendly blocking refresh wrapper. Call from a background thread.
     */
    fun refreshSubscriptionStatusBlocking(context: Context): SubscriptionStatus {
        return refreshSubscriptionStatusBlocking(context, false)
    }

    /**
     * Java-friendly blocking refresh wrapper with force refresh.
     */
    fun refreshSubscriptionStatusBlocking(context: Context, forceRefresh: Boolean): SubscriptionStatus {
        return runBlocking {
            refreshSubscriptionStatus(context, forceRefresh)
        }
    }
    
    /**
     * Show subscription prompt if not subscribed
     * Returns true if user needs to subscribe
     */
    fun requiresSubscription(context: Context): Boolean {
        return !hasActiveSubscription(context)
    }

    private fun getLastPhone(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getString(KEY_LAST_PHONE, null)
    }

    private fun normalizePhoneCandidates(phone: String): List<String> {
        val trimmed = phone.trim()
        val cleaned = trimmed.replace(Regex("[^0-9+]"), "")
        val digits = cleaned.replace("+", "")

        val normalized = when {
            digits.startsWith("0") && digits.length == 10 -> "254" + digits.substring(1)
            digits.startsWith("254") && digits.length == 12 -> digits
            (digits.startsWith("7") || digits.startsWith("1")) && digits.length == 9 -> "254$digits"
            else -> null
        }

        val candidates = mutableListOf<String>()
        if (normalized != null) {
            candidates.add(normalized)
            candidates.add("+$normalized")
        }
        if (digits.isNotBlank()) {
            candidates.add(digits)
            candidates.add("+$digits")
        }
        if (cleaned.isNotBlank()) {
            candidates.add(cleaned)
        }
        return candidates.distinct()
    }

    private fun fetchStatusFromServer(phone: String): SubscriptionStatus? {
        val url = STATUS_BASE_URL.toHttpUrlOrNull()
            ?.newBuilder()
            ?.addQueryParameter("phone", phone)
            ?.build()
            ?: return null

        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val body = response.body?.string().orEmpty()
            if (body.isBlank()) return null

            val json = JSONObject(body)
            val premium = json.optBoolean("premium", false)
            val plan = json.optString("plan", "").takeIf { it.isNotBlank() }
            val paidUntil = parseIsoToMillis(json.optString("paid_until", "").takeIf { it.isNotBlank() })
            val lastChecked = System.currentTimeMillis()

            return SubscriptionStatus(premium, plan, paidUntil, lastChecked)
        }
    }

    private fun parseIsoToMillis(value: String?): Long? {
        if (value.isNullOrBlank()) return null
        return try {
            Instant.parse(value).toEpochMilli()
        } catch (_: Exception) {
            null
        }
    }

    private fun saveStatusCache(context: Context, status: SubscriptionStatus) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit().apply {
            putBoolean(KEY_PREMIUM_ACTIVE, status.premium)
            putString(KEY_PREMIUM_PLAN, status.plan)
            putLong(KEY_PREMIUM_UNTIL, status.paidUntilMillis ?: 0L)
            putLong(KEY_PREMIUM_LAST_CHECK, status.lastCheckedMillis)
            apply()
        }
    }
}
