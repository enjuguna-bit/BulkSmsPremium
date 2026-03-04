package com.afriserve.smsmanager.fixtures

import com.afriserve.smsmanager.data.entity.SmsEntity
import com.afriserve.smsmanager.data.entity.ConversationEntity
import com.afriserve.smsmanager.data.entity.CampaignEntity
import com.afriserve.smsmanager.data.entity.TemplateEntity
import com.afriserve.smsmanager.data.entity.DashboardStatsEntity
import com.afriserve.smsmanager.data.entity.DashboardMetricsEntity

/**
 * Test fixtures for creating test entities.
 * Provides factory methods for creating consistent test data.
 */
object TestFixtures {
    
    private var idCounter = 1L
    
    /**
     * Reset the ID counter for tests that need predictable IDs
     */
    fun resetIdCounter() {
        idCounter = 1L
    }
    
    /**
     * Get next unique ID
     */
    fun nextId(): Long = idCounter++
    
    // ============== SMS Fixtures ==============
    
    object Sms {
        fun create(
            id: Long = nextId(),
            phoneNumber: String = "+254700000001",
            message: String = "Test message",
            status: String = "SENT",
            boxType: Int = 2, // Sent
            threadId: Long? = null,
            isRead: Boolean = true,
            createdAt: Long = System.currentTimeMillis()
        ): SmsEntity {
            return SmsEntity().apply {
                this.id = id
                this.phoneNumber = phoneNumber
                this.message = message
                this.status = status
                this.boxType = boxType
                this.threadId = threadId
                this.isRead = isRead
                this.createdAt = createdAt
            }
        }
        
        fun createInbox(
            phoneNumber: String = "+254700000001",
            message: String = "Incoming message"
        ) = create(
            phoneNumber = phoneNumber,
            message = message,
            boxType = 1, // Inbox
            status = "RECEIVED"
        )
        
        fun createSent(
            phoneNumber: String = "+254700000001",
            message: String = "Sent message"
        ) = create(
            phoneNumber = phoneNumber,
            message = message,
            boxType = 2, // Sent
            status = "SENT"
        )
        
        fun createFailed(
            phoneNumber: String = "+254700000001",
            message: String = "Failed message"
        ) = create(
            phoneNumber = phoneNumber,
            message = message,
            boxType = 5, // Failed
            status = "FAILED"
        )
        
        fun createPending(
            phoneNumber: String = "+254700000001",
            message: String = "Pending message"
        ) = create(
            phoneNumber = phoneNumber,
            message = message,
            boxType = 4, // Outbox
            status = "PENDING"
        )
        
        fun createList(count: Int, basePhoneNumber: String = "+25470000000"): List<SmsEntity> {
            return (1..count).map { index ->
                create(
                    phoneNumber = "$basePhoneNumber$index",
                    message = "Test message $index"
                )
            }
        }
    }
    
    // ============== Conversation Fixtures ==============
    
    object Conversation {
        fun create(
            id: Long = nextId(),
            phoneNumber: String = "+254700000001",
            contactName: String? = "Test Contact",
            lastMessageTime: Long = System.currentTimeMillis(),
            lastMessagePreview: String = "Last message preview",
            messageCount: Int = 10,
            unreadCount: Int = 0,
            threadId: Long? = null
        ): ConversationEntity {
            return ConversationEntity().apply {
                this.id = id
                this.phoneNumber = phoneNumber
                this.contactName = contactName
                this.lastMessageTime = lastMessageTime
                this.lastMessagePreview = lastMessagePreview
                this.messageCount = messageCount
                this.unreadCount = unreadCount
                this.threadId = threadId
            }
        }
        
        fun createWithUnread(unreadCount: Int = 3) = create(
            unreadCount = unreadCount,
            lastMessagePreview = "New unread message"
        )
        
        fun createList(count: Int): List<ConversationEntity> {
            return (1..count).map { index ->
                create(
                    phoneNumber = "+25470000000$index",
                    contactName = "Contact $index",
                    messageCount = index * 5
                )
            }
        }
    }
    
    // ============== Campaign Fixtures ==============
    
    object Campaign {
        fun create(
            id: Long = nextId(),
            name: String = "Test Campaign",
            description: String = "Test campaign description",
            status: String = "DRAFT",
            recipientCount: Int = 100,
            sentCount: Int = 0,
            deliveredCount: Int = 0,
            failedCount: Int = 0,
            createdAt: Long = System.currentTimeMillis()
        ): CampaignEntity {
            return CampaignEntity().apply {
                this.id = id
                this.name = name
                this.description = description
                this.status = status
                this.recipientCount = recipientCount
                this.sentCount = sentCount
                this.deliveredCount = deliveredCount
                this.failedCount = failedCount
                this.createdAt = createdAt
            }
        }
        
        fun createActive(progress: Int = 50) = create(
            status = "ACTIVE",
            recipientCount = 100,
            sentCount = progress,
            deliveredCount = (progress * 0.9).toInt()
        )
        
        fun createCompleted() = create(
            status = "COMPLETED",
            recipientCount = 100,
            sentCount = 100,
            deliveredCount = 95,
            failedCount = 5
        )
    }
    
    // ============== Template Fixtures ==============
    
    object Template {
        fun create(
            id: Long = nextId(),
            name: String = "Test Template",
            content: String = "Hello {name}, this is a test message.",
            category: String = "General",
            isFavorite: Boolean = false,
            usageCount: Int = 0
        ): TemplateEntity {
            return TemplateEntity().apply {
                this.id = id
                this.name = name
                this.content = content
                this.category = category
                this.isFavorite = isFavorite
                this.usageCount = usageCount
                this.createdAt = System.currentTimeMillis()
                this.updatedAt = System.currentTimeMillis()
            }
        }
        
        fun createFavorite() = create(
            name = "Favorite Template",
            isFavorite = true,
            usageCount = 50
        )
    }
    
    // ============== Dashboard Stats Fixtures ==============
    
    object DashboardStats {
        fun create(
            statType: String = "current",
            totalSent: Int = 100,
            totalDelivered: Int = 95,
            totalFailed: Int = 5,
            totalPending: Int = 0,
            activeCampaigns: Int = 2,
            totalCampaigns: Int = 10
        ): DashboardStatsEntity {
            return DashboardStatsEntity().apply {
                this.statType = statType
                this.totalSent = totalSent
                this.totalDelivered = totalDelivered
                this.totalFailed = totalFailed
                this.totalPending = totalPending
                this.activeCampaigns = activeCampaigns
                this.totalCampaigns = totalCampaigns
                this.lastUpdated = System.currentTimeMillis()
            }
        }
        
        fun createEmpty() = create(
            totalSent = 0,
            totalDelivered = 0,
            totalFailed = 0,
            activeCampaigns = 0,
            totalCampaigns = 0
        )
    }
    
    // ============== Dashboard Metrics Fixtures ==============
    
    object DashboardMetrics {
        fun create(
            id: Long = nextId(),
            metricDate: Long = System.currentTimeMillis(),
            metricType: String = "DAILY",
            sentCount: Int = 100,
            deliveredCount: Int = 95,
            failedCount: Int = 5
        ): DashboardMetricsEntity {
            return DashboardMetricsEntity().apply {
                this.id = id
                this.metricDate = metricDate
                this.metricType = metricType
                this.sentCount = sentCount
                this.deliveredCount = deliveredCount
                this.failedCount = failedCount
                this.createdAt = System.currentTimeMillis()
            }
        }
        
        fun createWeeklyMetrics(weeks: Int = 4): List<DashboardMetricsEntity> {
            val now = System.currentTimeMillis()
            val weekMs = 7 * 24 * 60 * 60 * 1000L
            
            return (0 until weeks).map { index ->
                create(
                    metricDate = now - (index * weekMs),
                    metricType = "WEEKLY",
                    sentCount = (100..500).random(),
                    deliveredCount = (90..450).random(),
                    failedCount = (5..50).random()
                )
            }
        }
    }
}
