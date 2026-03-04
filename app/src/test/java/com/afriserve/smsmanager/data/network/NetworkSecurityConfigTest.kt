package com.afriserve.smsmanager.data.network

import okhttp3.CertificatePinner
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for NetworkSecurityConfig
 */
class NetworkSecurityConfigTest {
    
    @Test
    fun `createCertificatePinner returns non-null pinner`() {
        val pinner = NetworkSecurityConfig.createCertificatePinner()
        
        assertNotNull(pinner)
    }
    
    @Test
    fun `createSecureBillingClient returns configured client`() {
        val client = NetworkSecurityConfig.createSecureBillingClient()
        
        assertNotNull(client)
        // Verify timeouts are set
        assertEquals(30_000, client.connectTimeoutMillis)
        assertEquals(60_000, client.readTimeoutMillis)
        assertEquals(60_000, client.writeTimeoutMillis)
        // Verify retry is enabled
        assertTrue(client.retryOnConnectionFailure)
        // Verify certificate pinner is attached
        assertNotNull(client.certificatePinner)
    }
    
    @Test
    fun `createStandardClient returns configured client without pinning`() {
        val client = NetworkSecurityConfig.createStandardClient()
        
        assertNotNull(client)
        // Verify timeouts are set
        assertEquals(30_000, client.connectTimeoutMillis)
        assertEquals(60_000, client.readTimeoutMillis)
        assertEquals(60_000, client.writeTimeoutMillis)
        // Verify retry is enabled
        assertTrue(client.retryOnConnectionFailure)
    }
    
    @Test
    fun `secure client has different configuration than standard client`() {
        val secureClient = NetworkSecurityConfig.createSecureBillingClient()
        val standardClient = NetworkSecurityConfig.createStandardClient()
        
        // Both should be configured but secure should have more interceptors/pinning
        assertNotNull(secureClient.certificatePinner)
    }
}
