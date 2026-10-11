package com.afriserve.smsmanager.data.network

import com.afriserve.smsmanager.AppConfig
import com.afriserve.smsmanager.BuildConfig
import okhttp3.CertificatePinner
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.net.URL
import java.util.concurrent.TimeUnit

/**
 * NetworkSecurityConfig — v2
 *
 * All timeout values now read from [AppConfig.Network] so there is a single
 * source of truth.  The old hardcoded 30/60/60-second values have been
 * removed; the AppConfig defaults are 15/20/15 s which are more appropriate
 * for mobile networks where long sockets cause battery drain.
 *
 * Certificate pinning is preserved unchanged.  Pins are for the Cloudflare
 * intermediate certificates used by *.workers.dev domains.  To regenerate:
 *
 *   openssl s_client -servername <domain> -connect <domain>:443 \
 *     | openssl x509 -pubkey -noout \
 *     | openssl pkey -pubin -outform der \
 *     | openssl dgst -sha256 -binary \
 *     | openssl enc -base64
 *
 * Always keep at least two backup pins to survive a CA rotation.
 */
object NetworkSecurityConfig {

    // ── Billing domain ────────────────────────────────────────────────────────
    private val BILLING_DOMAIN: String by lazy {
        try {
            URL(BuildConfig.BILLING_BASE_URL).host
        } catch (_: Exception) {
            "bulksms-webhook.enjuguna794.workers.dev"
        }
    }

    // ── workers.dev certificate pins ──────────────────────────────────────────
    private val CLOUDFLARE_PINS = listOf(
        // Current Google Trust Services / workers.dev chain observed on device
        "sha256/kIdp6NNEd8wsugYyyIYFsi1ylMCED3hZbSR8ZFsa/A4=",
        "sha256/mEflZT5enoR1FuXLgYYGqnVEoZvmf9c2bVBpiOjYQ0c=",
        // Previous workers.dev chain backups kept for certificate rotation safety
        "sha256/Wf2JRxU+Y7V2OcYEpJFjJFoLOtrCwU7G8OKxPgCtdtk=",
        "sha256/RQeZkB42znUfsDIIFWIRiYEcKl7nHwNFwWCrnMMJbVc=",
        "sha256/r/mIkG3eEpVdm+u/ko/cwxzOMo1bk4TyHIlByibiA5E="
    )

    // ── Public API ────────────────────────────────────────────────────────────

    fun createCertificatePinner(): CertificatePinner =
        CertificatePinner.Builder()
            .apply { CLOUDFLARE_PINS.forEach { add(BILLING_DOMAIN, it) } }
            .build()

    /**
     * Secure OkHttpClient with certificate pinning for billing/subscription calls.
     * Timeouts sourced from [AppConfig.Network].
     */
    fun createSecureBillingClient(): OkHttpClient =
        baseBuilder()
            .certificatePinner(createCertificatePinner())
            .addDebugLogging(HttpLoggingInterceptor.Level.HEADERS)
            .build()

    /**
     * Standard OkHttpClient without certificate pinning for non-sensitive calls.
     * Timeouts sourced from [AppConfig.Network].
     */
    fun createStandardClient(): OkHttpClient =
        baseBuilder()
            .addDebugLogging(HttpLoggingInterceptor.Level.BODY)
            .build()

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun baseBuilder(): OkHttpClient.Builder =
        OkHttpClient.Builder()
            .connectTimeout(AppConfig.Network.CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .readTimeout(AppConfig.Network.READ_TIMEOUT_MS,    TimeUnit.MILLISECONDS)
            .writeTimeout(AppConfig.Network.WRITE_TIMEOUT_MS,  TimeUnit.MILLISECONDS)
            .retryOnConnectionFailure(true)

    private fun OkHttpClient.Builder.addDebugLogging(
        level: HttpLoggingInterceptor.Level
    ): OkHttpClient.Builder {
        if (BuildConfig.DEBUG) {
            addInterceptor(HttpLoggingInterceptor().apply { this.level = level })
        }
        return this
    }
}
