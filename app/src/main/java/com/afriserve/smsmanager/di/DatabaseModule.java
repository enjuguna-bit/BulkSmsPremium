package com.afriserve.smsmanager.di;

import android.content.Context;

import androidx.room.Room;

import com.afriserve.smsmanager.AppDatabase;
import com.afriserve.smsmanager.data.dao.CampaignDao;
import com.afriserve.smsmanager.data.dao.ConversationDao;
import com.afriserve.smsmanager.data.dao.CustomerDao;
import com.afriserve.smsmanager.data.dao.DashboardDao;
import com.afriserve.smsmanager.data.dao.OptOutDao;
import com.afriserve.smsmanager.data.dao.ScheduledCampaignDao;
import com.afriserve.smsmanager.data.dao.SmsDao;
import com.afriserve.smsmanager.data.dao.SmsQueueDao;
import com.afriserve.smsmanager.data.dao.SmsSearchDao;
import com.afriserve.smsmanager.data.dao.TemplateDao;

import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;
import dagger.hilt.InstallIn;
import dagger.hilt.android.qualifiers.ApplicationContext;
import dagger.hilt.components.SingletonComponent;

@Module
@InstallIn(SingletonComponent.class)
public class DatabaseModule {

    @Provides
    @Singleton
    public static AppDatabase provideDatabase(@ApplicationContext Context context) {
        return com.afriserve.smsmanager.AppDatabase.getInstance(context);
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

    @Provides
    public static com.afriserve.smsmanager.data.dao.SyncStatusDao provideSyncStatusDao(AppDatabase database) {
        return database.syncStatusDao();
    }
}
