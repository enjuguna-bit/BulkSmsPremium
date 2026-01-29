package com.bulksms.smsmanager;

import android.content.Context;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;
import androidx.sqlite.db.SupportSQLiteDatabase;
import java.util.concurrent.Executors;

/**
 * Enhanced AppDatabase with proper entity definitions and relationships
 */
@Database(
    entities = {
        com.bulksms.smsmanager.data.entity.SmsEntity.class,
        com.bulksms.smsmanager.data.entity.Customer.class,
        com.bulksms.smsmanager.data.entity.CampaignEntity.class,
        com.bulksms.smsmanager.data.entity.TemplateEntity.class,
        com.bulksms.smsmanager.data.entity.OptOutEntity.class,
        com.bulksms.smsmanager.data.entity.ScheduledCampaignEntity.class,
        com.bulksms.smsmanager.data.entity.ConversationEntity.class,
        com.bulksms.smsmanager.data.entity.SmsFtsEntity.class,
        com.bulksms.smsmanager.data.entity.SmsQueueEntity.class,
        com.bulksms.smsmanager.data.entity.KpiEntity.class,
        com.bulksms.smsmanager.data.entity.DashboardStatsEntity.class,
        com.bulksms.smsmanager.data.entity.DashboardMetricsEntity.class
    },
    version = 3,
    exportSchema = false
)
@TypeConverters({AppDatabase.Converters.class})
public abstract class AppDatabase extends RoomDatabase {
    
    public abstract com.bulksms.smsmanager.data.dao.SmsDao smsDao();
    public abstract com.bulksms.smsmanager.data.dao.CustomerDao customerDao();
    public abstract com.bulksms.smsmanager.data.dao.CampaignDao campaignDao();
    public abstract com.bulksms.smsmanager.data.dao.TemplateDao templateDao();
    public abstract com.bulksms.smsmanager.data.dao.OptOutDao optOutDao();
    public abstract com.bulksms.smsmanager.data.dao.ScheduledCampaignDao scheduledCampaignDao();
    public abstract com.bulksms.smsmanager.data.dao.ConversationDao conversationDao();
    public abstract com.bulksms.smsmanager.data.dao.SmsSearchDao smsSearchDao();
    public abstract com.bulksms.smsmanager.data.dao.DashboardDao dashboardDao();
    public abstract com.bulksms.smsmanager.data.dao.SmsQueueDao smsQueueDao();
    
    private static volatile AppDatabase INSTANCE;
    private static final String DB_NAME = "bulksms_database_v2";
    
    public static AppDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = buildDatabase(context);
                }
            }
        }
        return INSTANCE;
    }
    
    private static AppDatabase buildDatabase(Context context) {
        return Room.databaseBuilder(
            context.getApplicationContext(),
            AppDatabase.class,
            DB_NAME
        )
        .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
        .setQueryExecutor(Executors.newFixedThreadPool(4))
        .fallbackToDestructiveMigrationOnDowngrade()
        .addCallback(new DatabaseCallback())
        .addCallback(new RoomDatabase.Callback() {
            @Override
            public void onOpen(SupportSQLiteDatabase db) {
                super.onOpen(db);
                db.execSQL("PRAGMA foreign_keys = ON");
            }
        })
        .build();
    }
    
    public static void closeDatabase() {
        if (INSTANCE != null) {
            INSTANCE.close();
            INSTANCE = null;
        }
    }
    
    /**
     * Database callback for initialization and optimization
     */
    private static class DatabaseCallback extends RoomDatabase.Callback {
        @Override
        public void onCreate(SupportSQLiteDatabase db) {
            super.onCreate(db);
            // Pre-population can be handled here if needed
        }
        
        @Override
        public void onOpen(SupportSQLiteDatabase db) {
            super.onOpen(db);
            createPerformanceIndexes(db);
        }
        
        private void createPerformanceIndexes(SupportSQLiteDatabase db) {
            // SmsEntity indexes
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_sms_status ON sms_entities(status)");
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_sms_campaign ON sms_entities(campaignId)");
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_sms_phone ON sms_entities(phoneNumber)");
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_sms_created ON sms_entities(createdAt)");
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_sms_retry ON sms_entities(nextRetryAt) WHERE nextRetryAt IS NOT NULL");
            
            // CampaignEntity indexes
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_campaign_status ON campaign_entities(status)");
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_campaign_created ON campaign_entities(createdAt)");
            
            // Customer indexes
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_customer_phone ON customers(phone)");
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_customer_last_seen ON customers(lastSeen)");
            
            // TemplateEntity indexes
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_template_category ON template_entities(category)");
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_template_favorite ON template_entities(isFavorite)");
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_template_usage ON template_entities(usageCount)");
        }
    }
    
    /**
     * Type converters for complex data types
     */
    public static class Converters {
        @androidx.room.TypeConverter
        public static java.util.Date fromTimestamp(Long value) {
            return value == null ? null : new java.util.Date(value);
        }
        
        @androidx.room.TypeConverter
        public static Long dateToTimestamp(java.util.Date date) {
            return date == null ? null : date.getTime();
        }
    }
}
