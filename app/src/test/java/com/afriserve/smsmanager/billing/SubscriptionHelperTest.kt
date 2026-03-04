package com.afriserve.smsmanager.billing

import android.content.Context
import android.content.SharedPreferences
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.MockitoAnnotations

/**
 * Unit tests for SubscriptionHelper
 */
class SubscriptionHelperTest {
    
    @Mock
    private lateinit var mockContext: Context
    
    @Mock
    private lateinit var mockSharedPreferences: SharedPreferences
    
    @Mock
    private lateinit var mockEditor: SharedPreferences.Editor
    
    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        
        `when`(mockContext.getSharedPreferences(anyString(), anyInt()))
            .thenReturn(mockSharedPreferences)
        `when`(mockSharedPreferences.edit()).thenReturn(mockEditor)
        `when`(mockEditor.putBoolean(anyString(), anyBoolean())).thenReturn(mockEditor)
        `when`(mockEditor.putString(anyString(), anyString())).thenReturn(mockEditor)
        `when`(mockEditor.putLong(anyString(), anyLong())).thenReturn(mockEditor)
    }
    
    // ============== Cached Status Tests ==============
    
    @Test
    fun `getCachedStatus returns false when no subscription cached`() {
        `when`(mockSharedPreferences.getBoolean("premium_active", false)).thenReturn(false)
        `when`(mockSharedPreferences.getString("premium_plan", null)).thenReturn(null)
        `when`(mockSharedPreferences.getLong("premium_until", 0L)).thenReturn(0L)
        `when`(mockSharedPreferences.getLong("premium_last_checked", 0L)).thenReturn(0L)
        
        val status = SubscriptionHelper.getCachedStatus(mockContext)
        
        assertFalse(status.premium)
        assertNull(status.plan)
        assertNull(status.paidUntilMillis)
    }
    
    @Test
    fun `getCachedStatus returns active subscription when cached`() {
        val futureTime = System.currentTimeMillis() + 30 * 24 * 60 * 60 * 1000L // 30 days
        
        `when`(mockSharedPreferences.getBoolean("premium_active", false)).thenReturn(true)
        `when`(mockSharedPreferences.getString("premium_plan", null)).thenReturn("monthly")
        `when`(mockSharedPreferences.getLong("premium_until", 0L)).thenReturn(futureTime)
        `when`(mockSharedPreferences.getLong("premium_last_checked", 0L)).thenReturn(System.currentTimeMillis())
        
        val status = SubscriptionHelper.getCachedStatus(mockContext)
        
        assertTrue(status.premium)
        assertEquals("monthly", status.plan)
        assertEquals(futureTime, status.paidUntilMillis)
    }
    
    // ============== Active Subscription Tests ==============
    
    @Test
    fun `hasActiveSubscription returns false when not premium`() {
        `when`(mockSharedPreferences.getBoolean("premium_active", false)).thenReturn(false)
        `when`(mockSharedPreferences.getString("premium_plan", null)).thenReturn(null)
        `when`(mockSharedPreferences.getLong("premium_until", 0L)).thenReturn(0L)
        `when`(mockSharedPreferences.getLong("premium_last_checked", 0L)).thenReturn(0L)
        
        val hasSubscription = SubscriptionHelper.hasActiveSubscription(mockContext)
        
        assertFalse(hasSubscription)
    }
    
    @Test
    fun `hasActiveSubscription returns false when subscription expired`() {
        val pastTime = System.currentTimeMillis() - 24 * 60 * 60 * 1000L // Yesterday
        
        `when`(mockSharedPreferences.getBoolean("premium_active", false)).thenReturn(true)
        `when`(mockSharedPreferences.getString("premium_plan", null)).thenReturn("monthly")
        `when`(mockSharedPreferences.getLong("premium_until", 0L)).thenReturn(pastTime)
        `when`(mockSharedPreferences.getLong("premium_last_checked", 0L)).thenReturn(System.currentTimeMillis())
        
        val hasSubscription = SubscriptionHelper.hasActiveSubscription(mockContext)
        
        assertFalse(hasSubscription)
    }
    
    @Test
    fun `hasActiveSubscription returns true when subscription active`() {
        val futureTime = System.currentTimeMillis() + 30 * 24 * 60 * 60 * 1000L // 30 days
        
        `when`(mockSharedPreferences.getBoolean("premium_active", false)).thenReturn(true)
        `when`(mockSharedPreferences.getString("premium_plan", null)).thenReturn("monthly")
        `when`(mockSharedPreferences.getLong("premium_until", 0L)).thenReturn(futureTime)
        `when`(mockSharedPreferences.getLong("premium_last_checked", 0L)).thenReturn(System.currentTimeMillis())
        
        val hasSubscription = SubscriptionHelper.hasActiveSubscription(mockContext)
        
        assertTrue(hasSubscription)
    }
    
    // ============== Payment Pending Tests ==============
    
    @Test
    fun `isPaymentPending returns false when no recent attempt`() {
        `when`(mockSharedPreferences.getLong("last_attempt", 0L)).thenReturn(0L)
        
        val isPending = SubscriptionHelper.isPaymentPending(mockContext)
        
        assertFalse(isPending)
    }
    
    @Test
    fun `isPaymentPending returns true when recent attempt`() {
        val recentTime = System.currentTimeMillis() - 60 * 1000L // 1 minute ago
        
        `when`(mockSharedPreferences.getLong("last_attempt", 0L)).thenReturn(recentTime)
        
        val isPending = SubscriptionHelper.isPaymentPending(mockContext)
        
        assertTrue(isPending)
    }
    
    @Test
    fun `isPaymentPending returns false when attempt too old`() {
        val oldTime = System.currentTimeMillis() - 10 * 60 * 1000L // 10 minutes ago (>5 min threshold)
        
        `when`(mockSharedPreferences.getLong("last_attempt", 0L)).thenReturn(oldTime)
        
        val isPending = SubscriptionHelper.isPaymentPending(mockContext)
        
        assertFalse(isPending)
    }
    
    // ============== SubscriptionStatus Data Class Tests ==============
    
    @Test
    fun `SubscriptionStatus equality works correctly`() {
        val status1 = SubscriptionHelper.SubscriptionStatus(
            premium = true,
            plan = "monthly",
            paidUntilMillis = 1000L,
            lastCheckedMillis = 500L
        )
        val status2 = SubscriptionHelper.SubscriptionStatus(
            premium = true,
            plan = "monthly",
            paidUntilMillis = 1000L,
            lastCheckedMillis = 500L
        )
        
        assertEquals(status1, status2)
    }
    
    @Test
    fun `ClaimResult data class works correctly`() {
        val result = SubscriptionHelper.ClaimResult(
            success = true,
            processing = false,
            message = "Payment successful"
        )
        
        assertTrue(result.success)
        assertFalse(result.processing)
        assertEquals("Payment successful", result.message)
    }
}
