package com.bulksms.smsmanager.di;

import android.content.Context;

import com.bulksms.smsmanager.BulkSmsService;
import com.bulksms.smsmanager.data.dao.CampaignDao;
import com.bulksms.smsmanager.data.dao.SmsDao;
import com.bulksms.smsmanager.data.compliance.RateLimitManager;
import com.bulksms.smsmanager.data.compliance.ComplianceManager;
import com.bulksms.smsmanager.data.persistence.UploadPersistenceService;
import com.bulksms.smsmanager.data.queue.SmsQueueManager;
import com.bulksms.smsmanager.data.tracking.DeliveryTracker;
import com.bulksms.smsmanager.data.tracking.EnhancedDeliveryTracker;

import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;
import dagger.hilt.InstallIn;
import dagger.hilt.android.qualifiers.ApplicationContext;
import dagger.hilt.components.SingletonComponent;

/**
 * Service dependency injection module
 * Provides services for dependency injection
 */
@Module
@InstallIn(SingletonComponent.class)
public class ServiceModule {

    @Provides
    @Singleton
    public static BulkSmsService provideBulkSmsService(
            @ApplicationContext Context context,
            SmsDao smsDao,
            CampaignDao campaignDao,
            RateLimitManager rateLimitManager,
            ComplianceManager complianceManager,
            DeliveryTracker deliveryTracker) {
        return new BulkSmsService(context, smsDao, campaignDao, rateLimitManager, complianceManager, deliveryTracker);
    }

    @Provides
    @Singleton
    public static EnhancedDeliveryTracker provideEnhancedDeliveryTracker(
            @ApplicationContext Context context,
            SmsDao smsDao,
            SmsQueueManager queueManager) {
        return new EnhancedDeliveryTracker(context, smsDao, queueManager);
    }

    @Provides
    @Singleton
    public static UploadPersistenceService provideUploadPersistenceService(
            @ApplicationContext Context context) {
        return new UploadPersistenceService(context);
    }
}
