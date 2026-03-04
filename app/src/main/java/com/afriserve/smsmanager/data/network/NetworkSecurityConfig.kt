package com.afriserve.smsmanager.data.network

import okhttp3.CertificatePinner
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit
import com.afriserve.smsmanager.BuildConfig
import java.net.URL

/**
 * Network security configuration with certificate pinning.
 * 
 * Certificate pinning prevents man-in-the-middle attacks by validating
 * that the server's certificate matches known trusted certificates.
 * 
 * To update pins:
 * 1. Run: openssl s_client -servername <domain> -connect <domain>:443 | openssl x509 -pubkey -noout | openssl pkey -pubin -outform der | openssl dgst -sha256 -binary | openssl enc -base64
 * 2. Update the pin values below
 * 3. Always include at least one backup pin
 */
object NetworkSecurityConfig {
    
    // Extract domain from BuildConfig URL
    private val BILLING_DOMAIN: String by lazy {
        try {
            URL(BuildConfig.BILLING_BASE_URL).host
        } catch (e: Exception) {
            "bulksmsbilling.enjuguna794.workers.dev"
        }
    }
    
    // Cloudflare certificate pins (workers.dev uses Cloudflare)
    // These pins are for the Cloudflare intermediate certificates
    // Update these pins when Cloudflare rotates their certificates
    // Current pins valid for Cloudflare's certificate chain
    private val CLOUDFLARE_PINS = listOf(
        // Cloudflare Inc ECC CA-3 (Primary)
        "sha256/Wf2JRxU+Y7V2OcYEpJFjJFoLOtrCwU7G8OKxPgCtdtk=",
        // DigiCert TLS RSA SHA256 2020 CA1 (Backup 1)
        "sha256/RQeZkB42znUfsDIIFWIRiYEcKl7nHwNFwWCrnMMJbVc=",
        // DigiCert Global Root CA (Backup 2 - Root)
        "sha256/r/mIkG3eEpVdm+u/ko/cwxzOMo1bk4TyHIlByibiA5E="
    )
    
    /**
     * Connection timeouts in seconds
     */
    private const val CONNECT_TIMEOUT = 30L
    private const val READ_TIMEOUT = 60L
    private const val WRITE_TIMEOUT = 60L
    
    /**
     * Create a certificate pinner for the billing endpoint.
     * Pins the Cloudflare certificate chain used by workers.dev domains.
     */
    fun createCertificatePinner(): CertificatePinner {
        return CertificatePinner.Builder()
            .apply {
                CLOUDFLARE_PINS.forEach { pin ->
                    add(BILLING_DOMAIN, pin)
                }
            }
            .build()
    }
    
    /**
     * Create a secure OkHttpClient with certificate pinning enabled.
     * Use this client for all billing/subscription API calls.
     */
    fun createSecureBillingClient(): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT, TimeUnit.SECONDS)
            .writeTimeout(WRITE_TIMEOUT, TimeUnit.SECONDS)
            .certificatePinner(createCertificatePinner())
            .retryOnConnectionFailure(true)
        
        // Add logging in debug builds only
        if (BuildConfig.DEBUG) {
            val loggingInterceptor = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.HEADERS
            }
            builder.addInterceptor(loggingInterceptor)
        }
        
        return builder.build()
    }
    
    /**
     * Create a standard OkHttpClient without certificate pinning.
     * Use this for non-sensitive API calls where pinning isn't required.
     */
    fun createStandardClient(): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT, TimeUnit.SECONDS)
            .writeTimeout(WRITE_TIMEOUT, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
        
        if (BuildConfig.DEBUG) {
            val loggingInterceptor = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            }
            builder.addInterceptor(loggingInterceptor)
        }
        
        return builder.build()
    }
}
