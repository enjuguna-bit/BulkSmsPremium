package com.bulksms.smsmanager.di;

import com.bulksms.smsmanager.AppDatabase;
import com.bulksms.smsmanager.data.dao.*;

import dagger.Module;
import dagger.Provides;

@Module
public class DatabaseModule {

    @Provides
    public static AppDatabase provideDatabase() {
        return new AppDatabase();
    }

    @Provides
    public static SmsDao provideSmsDao(AppDatabase db) { return db.smsDao(); }

    @Provides
    public static CustomerDao provideCustomerDao(AppDatabase db) { return db.customerDao(); }

    @Provides
    public static CampaignDao provideCampaignDao(AppDatabase db) { return db.campaignDao(); }

    @Provides
    public static TemplateDao provideTemplateDao(AppDatabase db) { return db.templateDao(); }

    @Provides
    public static OptOutDao provideOptOutDao(AppDatabase db) { return db.optOutDao(); }

    @Provides
    public static ScheduledCampaignDao provideScheduledCampaignDao(AppDatabase db) { return db.scheduledCampaignDao(); }

    @Provides
    public static ConversationDao provideConversationDao(AppDatabase db) { return db.conversationDao(); }

    @Provides
    public static SmsSearchDao provideSmsSearchDao(AppDatabase db) { return db.smsSearchDao(); }

    @Provides
    public static DashboardDao provideDashboardDao(AppDatabase db) { return db.dashboardDao(); }

    @Provides
    public static SmsQueueDao provideSmsQueueDao(AppDatabase db) { return db.smsQueueDao(); }
}
