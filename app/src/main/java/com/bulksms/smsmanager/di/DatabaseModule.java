package com.bulksms.smsmanager.di;

import android.content.Context;

import androidx.room.Room;

import com.bulksms.smsmanager.AppDatabase;
import com.bulksms.smsmanager.data.dao.CampaignDao;
import com.bulksms.smsmanager.data.dao.ConversationDao;
import com.bulksms.smsmanager.data.dao.CustomerDao;
import com.bulksms.smsmanager.data.dao.DashboardDao;
import com.bulksms.smsmanager.data.dao.OptOutDao;
import com.bulksms.smsmanager.data.dao.ScheduledCampaignDao;
import com.bulksms.smsmanager.data.dao.SmsDao;
import com.bulksms.smsmanager.data.dao.SmsQueueDao;
import com.bulksms.smsmanager.data.dao.SmsSearchDao;
import com.bulksms.smsmanager.data.dao.TemplateDao;

import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;
import dagger.hilt.InstallIn;
import dagger.hilt.android.qualifiers.ApplicationContext;
import dagger.hilt.components.SingletonComponent;

/**
 * Database dependency injection module
 * Provides Room database and DAOs for dependency injection
 */
@Module
@InstallIn(SingletonComponent.class)
public abstract class DatabaseModule {

    @Provides
    @Singleton
    public static AppDatabase provideDatabase(@ApplicationContext Context context) {
        // Use the existing AppDatabase singleton to prevent dual instances
        // This ensures all components use the same database instance
        return com.bulksms.smsmanager.AppDatabase.getInstance(context);
    }

    @Provides
    public static SmsDao provideSmsDao(AppDatabase database) {
        return database.smsDao();
    }

    @Provides
    public static CustomerDao provideCustomerDao(AppDatabase database) {
        return database.customerDao();
    }

    @Provides
    public static CampaignDao provideCampaignDao(AppDatabase database) {
        return database.campaignDao();
    }

    @Provides
    public static TemplateDao provideTemplateDao(AppDatabase database) {
        return database.templateDao();
    }

    @Provides
    public static OptOutDao provideOptOutDao(AppDatabase database) {
        return database.optOutDao();
    }

    @Provides
    public static ScheduledCampaignDao provideScheduledCampaignDao(AppDatabase database) {
        return database.scheduledCampaignDao();
    }

    @Provides
    public static ConversationDao provideConversationDao(AppDatabase database) {
        return database.conversationDao();
    }

    @Provides
    public static SmsSearchDao provideSmsSearchDao(AppDatabase database) {
        return database.smsSearchDao();
    }

    @Provides
    public static DashboardDao provideDashboardDao(AppDatabase database) {
        return database.dashboardDao();
    }

    @Provides
    public static SmsQueueDao provideSmsQueueDao(AppDatabase database) {
        return database.smsQueueDao();
    }
}
