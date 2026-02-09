package com.afriserve.smsmanager;

import android.content.Context;
import android.database.Cursor;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;
import java.util.concurrent.Executors;

/**
 * Enhanced AppDatabase with proper entity definitions and relationships
 */
@Database(entities = {
        com.afriserve.smsmanager.data.entity.SmsEntity.class,
        com.afriserve.smsmanager.data.entity.Customer.class,
        com.afriserve.smsmanager.data.entity.CampaignEntity.class,
        com.afriserve.smsmanager.data.entity.TemplateEntity.class,
        com.afriserve.smsmanager.data.entity.OptOutEntity.class,
        com.afriserve.smsmanager.data.entity.ScheduledCampaignEntity.class,
        com.afriserve.smsmanager.data.entity.ConversationEntity.class,
        com.afriserve.smsmanager.data.entity.SmsFtsEntity.class,
        com.afriserve.smsmanager.data.entity.SmsQueueEntity.class,
        com.afriserve.smsmanager.data.entity.KpiEntity.class,
        com.afriserve.smsmanager.data.entity.DashboardStatsEntity.class,
        com.afriserve.smsmanager.data.entity.DashboardMetricsEntity.class,
        com.afriserve.smsmanager.data.entity.SyncStatusEntity.class
}, version = 8, exportSchema = false)
@TypeConverters({ AppDatabase.Converters.class })
public abstract class AppDatabase extends RoomDatabase {

    public abstract com.afriserve.smsmanager.data.dao.SmsDao smsDao();

    public abstract com.afriserve.smsmanager.data.dao.CustomerDao customerDao();

    public abstract com.afriserve.smsmanager.data.dao.CampaignDao campaignDao();

    public abstract com.afriserve.smsmanager.data.dao.TemplateDao templateDao();

    public abstract com.afriserve.smsmanager.data.dao.OptOutDao optOutDao();

    public abstract com.afriserve.smsmanager.data.dao.ScheduledCampaignDao scheduledCampaignDao();

    public abstract com.afriserve.smsmanager.data.dao.ConversationDao conversationDao();

    public abstract com.afriserve.smsmanager.data.dao.SmsSearchDao smsSearchDao();

    public abstract com.afriserve.smsmanager.data.dao.DashboardDao dashboardDao();

    public abstract com.afriserve.smsmanager.data.dao.SmsQueueDao smsQueueDao();

    public abstract com.afriserve.smsmanager.data.dao.SyncStatusDao syncStatusDao();

    private static volatile AppDatabase INSTANCE;
    private static final String DB_NAME = "bulksms_database_v2";

    private static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(SupportSQLiteDatabase db) {
            migrateSchema(db);
        }
    };

    private static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override
        public void migrate(SupportSQLiteDatabase db) {
            migrateSchema(db);
        }
    };

    private static final Migration MIGRATION_3_4 = new Migration(3, 4) {
        @Override
        public void migrate(SupportSQLiteDatabase db) {
            migrateSchema(db);
        }
    };

    private static final Migration MIGRATION_4_5 = new Migration(4, 5) {
        @Override
        public void migrate(SupportSQLiteDatabase db) {
            migrateSchema(db);
        }
    };

    private static final Migration MIGRATION_5_6 = new Migration(5, 6) {
        @Override
        public void migrate(SupportSQLiteDatabase db) {
            rebuildSmsEntitiesTable(db);
            migrateSchema(db);
        }
    };

    private static final Migration MIGRATION_6_7 = new Migration(6, 7) {
        @Override
        public void migrate(SupportSQLiteDatabase db) {
            migrateSchema(db);
        }
    };

    private static final Migration MIGRATION_7_8 = new Migration(7, 8) {
        @Override
        public void migrate(SupportSQLiteDatabase db) {
            migrateSchema(db);
        }
    };

    private static void migrateSchema(SupportSQLiteDatabase db) {
        createCampaignEntitiesTable(db);
        createSmsEntitiesTable(db);
        createCustomerTable(db);
        createTemplateTable(db);
        createOptOutTable(db);
        createScheduledCampaignsTable(db);
        createConversationTable(db);
        createSmsQueueTable(db);
        createKpiTable(db);
        createDashboardStatsTable(db);
        createDashboardMetricsTable(db);
        createSyncStatusTable(db);
        createSmsFtsTable(db);
        createIndexes(db);
        dropLegacyIndexes(db);
    }

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
                DB_NAME)
                .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
                .setQueryExecutor(Executors.newFixedThreadPool(4))
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8)
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

    private static void createSmsEntitiesTable(SupportSQLiteDatabase db) {
        db.execSQL(
                "CREATE TABLE IF NOT EXISTS `sms_entities` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`deviceSmsId` INTEGER, " +
                        "`boxType` INTEGER, " +
                        "`threadId` INTEGER, " +
                        "`isRead` INTEGER, " +
                        "`phoneNumber` TEXT, " +
                        "`message` TEXT, " +
                        "`isMms` INTEGER, " +
                        "`mediaUri` TEXT, " +
                        "`attachmentCount` INTEGER, " +
                        "`status` TEXT, " +
                        "`createdAt` INTEGER NOT NULL DEFAULT 0, " +
                        "`sentAt` INTEGER, " +
                        "`deliveredAt` INTEGER, " +
                        "`campaignId` INTEGER, " +
                        "`retryCount` INTEGER NOT NULL DEFAULT 0, " +
                        "`nextRetryAt` INTEGER, " +
                        "`errorCode` TEXT, " +
                        "`errorMessage` TEXT, " +
                        "FOREIGN KEY(`campaignId`) REFERENCES `campaign_entities`(`id`) ON UPDATE CASCADE ON DELETE SET NULL" +
                        ")");

        ensureColumns(db, "sms_entities", new String[][] {
                { "deviceSmsId", "INTEGER" },
                { "boxType", "INTEGER" },
                { "threadId", "INTEGER" },
                { "isRead", "INTEGER" },
                { "phoneNumber", "TEXT" },
                { "message", "TEXT" },
                { "isMms", "INTEGER" },
                { "mediaUri", "TEXT" },
                { "attachmentCount", "INTEGER" },
                { "status", "TEXT" },
                { "createdAt", "INTEGER NOT NULL DEFAULT 0" },
                { "sentAt", "INTEGER" },
                { "deliveredAt", "INTEGER" },
                { "campaignId", "INTEGER" },
                { "retryCount", "INTEGER NOT NULL DEFAULT 0" },
                { "nextRetryAt", "INTEGER" },
                { "errorCode", "TEXT" },
                { "errorMessage", "TEXT" }
        });
    }

    private static void rebuildSmsEntitiesTable(SupportSQLiteDatabase db) {
        if (!tableExists(db, "sms_entities")) {
            return;
        }

        db.execSQL("ALTER TABLE `sms_entities` RENAME TO `sms_entities_old`");
        createSmsEntitiesTable(db);

        String[][] columns = new String[][] {
                { "id", "id" },
                { "deviceSmsId", "deviceSmsId" },
                { "boxType", "boxType" },
                { "threadId", "threadId" },
                { "isRead", "isRead" },
                { "phoneNumber", "phoneNumber" },
                { "message", "message" },
                { "isMms", "isMms" },
                { "mediaUri", "mediaUri" },
                { "attachmentCount", "attachmentCount" },
                { "status", "status" },
                { "createdAt", "createdAt" },
                { "sentAt", "sentAt" },
                { "deliveredAt", "deliveredAt" },
                { "campaignId", "campaignId" },
                { "retryCount", "retryCount" },
                { "nextRetryAt", "nextRetryAt" },
                { "errorCode", "errorCode" },
                { "errorMessage", "errorMessage" }
        };

        StringBuilder insertColumns = new StringBuilder();
        StringBuilder selectColumns = new StringBuilder();
        for (int i = 0; i < columns.length; i++) {
            String column = columns[i][0];
            if (i > 0) {
                insertColumns.append(", ");
                selectColumns.append(", ");
            }
            insertColumns.append("`").append(column).append("`");
            if (columnExists(db, "sms_entities_old", column)) {
                selectColumns.append("`").append(column).append("`");
            } else {
                selectColumns.append(defaultForSmsColumn(column));
            }
        }

        db.execSQL("INSERT INTO `sms_entities` (" + insertColumns + ") SELECT " +
                selectColumns + " FROM `sms_entities_old`");

        db.execSQL("DROP TABLE IF EXISTS `sms_entities_old`");
    }

    private static String defaultForSmsColumn(String column) {
        if ("createdAt".equals(column)) {
            return "0";
        }
        if ("retryCount".equals(column)) {
            return "0";
        }
        if ("attachmentCount".equals(column)) {
            return "0";
        }
        return "NULL";
    }

    private static void createCampaignEntitiesTable(SupportSQLiteDatabase db) {
        db.execSQL(
                "CREATE TABLE IF NOT EXISTS `campaign_entities` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`name` TEXT, " +
                        "`description` TEXT, " +
                        "`status` TEXT, " +
                        "`templateId` INTEGER, " +
                        "`recipientCount` INTEGER NOT NULL DEFAULT 0, " +
                        "`sentCount` INTEGER NOT NULL DEFAULT 0, " +
                        "`deliveredCount` INTEGER NOT NULL DEFAULT 0, " +
                        "`failedCount` INTEGER NOT NULL DEFAULT 0, " +
                        "`skippedCount` INTEGER NOT NULL DEFAULT 0, " +
                        "`scheduledAt` INTEGER, " +
                        "`startedAt` INTEGER, " +
                        "`completedAt` INTEGER, " +
                        "`createdAt` INTEGER NOT NULL DEFAULT 0, " +
                        "`updatedAt` INTEGER NOT NULL DEFAULT 0, " +
                        "`settings` TEXT" +
                        ")");

        ensureColumns(db, "campaign_entities", new String[][] {
                { "name", "TEXT" },
                { "description", "TEXT" },
                { "status", "TEXT" },
                { "templateId", "INTEGER" },
                { "recipientCount", "INTEGER NOT NULL DEFAULT 0" },
                { "sentCount", "INTEGER NOT NULL DEFAULT 0" },
                { "deliveredCount", "INTEGER NOT NULL DEFAULT 0" },
                { "failedCount", "INTEGER NOT NULL DEFAULT 0" },
                { "skippedCount", "INTEGER NOT NULL DEFAULT 0" },
                { "scheduledAt", "INTEGER" },
                { "startedAt", "INTEGER" },
                { "completedAt", "INTEGER" },
                { "createdAt", "INTEGER NOT NULL DEFAULT 0" },
                { "updatedAt", "INTEGER NOT NULL DEFAULT 0" },
                { "settings", "TEXT" }
        });
    }

    private static void createCustomerTable(SupportSQLiteDatabase db) {
        db.execSQL(
                "CREATE TABLE IF NOT EXISTS `customers` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`name` TEXT, " +
                        "`phone` TEXT, " +
                        "`email` TEXT, " +
                        "`address` TEXT, " +
                        "`company` TEXT, " +
                        "`notes` TEXT, " +
                        "`isFavorite` INTEGER NOT NULL DEFAULT 0, " +
                        "`lastSeen` INTEGER, " +
                        "`createdAt` INTEGER NOT NULL DEFAULT 0, " +
                        "`updatedAt` INTEGER NOT NULL DEFAULT 0" +
                        ")");

        ensureColumns(db, "customers", new String[][] {
                { "name", "TEXT" },
                { "phone", "TEXT" },
                { "email", "TEXT" },
                { "address", "TEXT" },
                { "company", "TEXT" },
                { "notes", "TEXT" },
                { "isFavorite", "INTEGER NOT NULL DEFAULT 0" },
                { "lastSeen", "INTEGER" },
                { "createdAt", "INTEGER NOT NULL DEFAULT 0" },
                { "updatedAt", "INTEGER NOT NULL DEFAULT 0" }
        });
    }

    private static void createTemplateTable(SupportSQLiteDatabase db) {
        db.execSQL(
                "CREATE TABLE IF NOT EXISTS `template_entities` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`name` TEXT, " +
                        "`content` TEXT, " +
                        "`category` TEXT, " +
                        "`description` TEXT, " +
                        "`isFavorite` INTEGER NOT NULL DEFAULT 0, " +
                        "`usageCount` INTEGER NOT NULL DEFAULT 0, " +
                        "`lastUsed` INTEGER, " +
                        "`variables` TEXT, " +
                        "`createdAt` INTEGER NOT NULL DEFAULT 0, " +
                        "`updatedAt` INTEGER NOT NULL DEFAULT 0" +
                        ")");

        ensureColumns(db, "template_entities", new String[][] {
                { "name", "TEXT" },
                { "content", "TEXT" },
                { "category", "TEXT" },
                { "description", "TEXT" },
                { "isFavorite", "INTEGER NOT NULL DEFAULT 0" },
                { "usageCount", "INTEGER NOT NULL DEFAULT 0" },
                { "lastUsed", "INTEGER" },
                { "variables", "TEXT" },
                { "createdAt", "INTEGER NOT NULL DEFAULT 0" },
                { "updatedAt", "INTEGER NOT NULL DEFAULT 0" }
        });
    }

    private static void createOptOutTable(SupportSQLiteDatabase db) {
        db.execSQL(
                "CREATE TABLE IF NOT EXISTS `opt_outs` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`phoneNumber` TEXT, " +
                        "`reason` TEXT, " +
                        "`optOutTime` INTEGER NOT NULL DEFAULT 0, " +
                        "`source` TEXT, " +
                        "`campaignId` INTEGER, " +
                        "`notes` TEXT, " +
                        "`isActive` INTEGER NOT NULL DEFAULT 1, " +
                        "`createdAt` INTEGER NOT NULL DEFAULT 0, " +
                        "`updatedAt` INTEGER NOT NULL DEFAULT 0" +
                        ")");

        ensureColumns(db, "opt_outs", new String[][] {
                { "phoneNumber", "TEXT" },
                { "reason", "TEXT" },
                { "optOutTime", "INTEGER NOT NULL DEFAULT 0" },
                { "source", "TEXT" },
                { "campaignId", "INTEGER" },
                { "notes", "TEXT" },
                { "isActive", "INTEGER NOT NULL DEFAULT 1" },
                { "createdAt", "INTEGER NOT NULL DEFAULT 0" },
                { "updatedAt", "INTEGER NOT NULL DEFAULT 0" }
        });
    }

    private static void createScheduledCampaignsTable(SupportSQLiteDatabase db) {
        db.execSQL(
                "CREATE TABLE IF NOT EXISTS `scheduled_campaigns` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`campaignId` INTEGER NOT NULL DEFAULT 0, " +
                        "`scheduledTime` INTEGER NOT NULL DEFAULT 0, " +
                        "`timezone` TEXT, " +
                        "`status` TEXT, " +
                        "`isActive` INTEGER NOT NULL DEFAULT 1, " +
                        "`isRecurring` INTEGER NOT NULL DEFAULT 0, " +
                        "`recurrencePattern` TEXT, " +
                        "`recurrenceInterval` INTEGER NOT NULL DEFAULT 1, " +
                        "`recurrenceDays` TEXT, " +
                        "`recurrenceTime` TEXT, " +
                        "`maxOccurrences` INTEGER, " +
                        "`currentOccurrences` INTEGER NOT NULL DEFAULT 0, " +
                        "`nextExecutionTime` INTEGER, " +
                        "`lastExecutionTime` INTEGER, " +
                        "`executionHistory` TEXT, " +
                        "`settings` TEXT, " +
                        "`createdAt` INTEGER NOT NULL DEFAULT 0, " +
                        "`updatedAt` INTEGER NOT NULL DEFAULT 0" +
                        ")");

        ensureColumns(db, "scheduled_campaigns", new String[][] {
                { "campaignId", "INTEGER NOT NULL DEFAULT 0" },
                { "scheduledTime", "INTEGER NOT NULL DEFAULT 0" },
                { "timezone", "TEXT" },
                { "status", "TEXT" },
                { "isActive", "INTEGER NOT NULL DEFAULT 1" },
                { "isRecurring", "INTEGER NOT NULL DEFAULT 0" },
                { "recurrencePattern", "TEXT" },
                { "recurrenceInterval", "INTEGER NOT NULL DEFAULT 1" },
                { "recurrenceDays", "TEXT" },
                { "recurrenceTime", "TEXT" },
                { "maxOccurrences", "INTEGER" },
                { "currentOccurrences", "INTEGER NOT NULL DEFAULT 0" },
                { "nextExecutionTime", "INTEGER" },
                { "lastExecutionTime", "INTEGER" },
                { "executionHistory", "TEXT" },
                { "settings", "TEXT" },
                { "createdAt", "INTEGER NOT NULL DEFAULT 0" },
                { "updatedAt", "INTEGER NOT NULL DEFAULT 0" }
        });
    }

    private static void createConversationTable(SupportSQLiteDatabase db) {
        db.execSQL(
                "CREATE TABLE IF NOT EXISTS `conversations` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`phoneNumber` TEXT, " +
                        "`contactName` TEXT, " +
                        "`contactPhotoUri` TEXT, " +
                        "`threadId` INTEGER, " +
                        "`lastMessageTime` INTEGER NOT NULL DEFAULT 0, " +
                        "`lastMessagePreview` TEXT, " +
                        "`lastMessageType` TEXT, " +
                        "`messageCount` INTEGER NOT NULL DEFAULT 0, " +
                        "`unreadCount` INTEGER NOT NULL DEFAULT 0, " +
                        "`createdAt` INTEGER NOT NULL DEFAULT 0, " +
                        "`updatedAt` INTEGER NOT NULL DEFAULT 0, " +
                        "`isArchived` INTEGER NOT NULL DEFAULT 0, " +
                        "`isPinned` INTEGER NOT NULL DEFAULT 0" +
                        ")");

        ensureColumns(db, "conversations", new String[][] {
                { "phoneNumber", "TEXT" },
                { "contactName", "TEXT" },
                { "contactPhotoUri", "TEXT" },
                { "threadId", "INTEGER" },
                { "lastMessageTime", "INTEGER NOT NULL DEFAULT 0" },
                { "lastMessagePreview", "TEXT" },
                { "lastMessageType", "TEXT" },
                { "messageCount", "INTEGER NOT NULL DEFAULT 0" },
                { "unreadCount", "INTEGER NOT NULL DEFAULT 0" },
                { "createdAt", "INTEGER NOT NULL DEFAULT 0" },
                { "updatedAt", "INTEGER NOT NULL DEFAULT 0" },
                { "isArchived", "INTEGER NOT NULL DEFAULT 0" },
                { "isPinned", "INTEGER NOT NULL DEFAULT 0" }
        });
    }

    private static void createSmsQueueTable(SupportSQLiteDatabase db) {
        db.execSQL(
                "CREATE TABLE IF NOT EXISTS `sms_queue` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`phoneNumber` TEXT, " +
                        "`message` TEXT, " +
                        "`simSlot` INTEGER NOT NULL DEFAULT 0, " +
                        "`originalSmsId` INTEGER, " +
                        "`retryCount` INTEGER NOT NULL DEFAULT 0, " +
                        "`status` TEXT, " +
                        "`createdAt` INTEGER NOT NULL DEFAULT 0, " +
                        "`nextRetryAt` INTEGER NOT NULL DEFAULT 0, " +
                        "`lastFailureAt` INTEGER, " +
                        "`errorMessage` TEXT, " +
                        "`errorCode` TEXT" +
                        ")");

        ensureColumns(db, "sms_queue", new String[][] {
                { "phoneNumber", "TEXT" },
                { "message", "TEXT" },
                { "simSlot", "INTEGER NOT NULL DEFAULT 0" },
                { "originalSmsId", "INTEGER" },
                { "retryCount", "INTEGER NOT NULL DEFAULT 0" },
                { "status", "TEXT" },
                { "createdAt", "INTEGER NOT NULL DEFAULT 0" },
                { "nextRetryAt", "INTEGER NOT NULL DEFAULT 0" },
                { "lastFailureAt", "INTEGER" },
                { "errorMessage", "TEXT" },
                { "errorCode", "TEXT" }
        });
    }

    private static void createKpiTable(SupportSQLiteDatabase db) {
        db.execSQL(
                "CREATE TABLE IF NOT EXISTS `kpi_data` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`kpiType` TEXT, " +
                        "`kpiName` TEXT, " +
                        "`kpiValue` REAL NOT NULL DEFAULT 0, " +
                        "`targetValue` REAL NOT NULL DEFAULT 0, " +
                        "`thresholdWarning` REAL NOT NULL DEFAULT 0, " +
                        "`thresholdCritical` REAL NOT NULL DEFAULT 0, " +
                        "`period` TEXT, " +
                        "`timestamp` INTEGER NOT NULL DEFAULT 0, " +
                        "`status` TEXT, " +
                        "`trend` TEXT, " +
                        "`trendPercentage` REAL NOT NULL DEFAULT 0, " +
                        "`unit` TEXT, " +
                        "`category` TEXT, " +
                        "`description` TEXT, " +
                        "`isAlert` INTEGER NOT NULL DEFAULT 0, " +
                        "`alertMessage` TEXT, " +
                        "`metadata` TEXT, " +
                        "`createdAt` INTEGER NOT NULL DEFAULT 0, " +
                        "`updatedAt` INTEGER NOT NULL DEFAULT 0" +
                        ")");

        ensureColumns(db, "kpi_data", new String[][] {
                { "kpiType", "TEXT" },
                { "kpiName", "TEXT" },
                { "kpiValue", "REAL NOT NULL DEFAULT 0" },
                { "targetValue", "REAL NOT NULL DEFAULT 0" },
                { "thresholdWarning", "REAL NOT NULL DEFAULT 0" },
                { "thresholdCritical", "REAL NOT NULL DEFAULT 0" },
                { "period", "TEXT" },
                { "timestamp", "INTEGER NOT NULL DEFAULT 0" },
                { "status", "TEXT" },
                { "trend", "TEXT" },
                { "trendPercentage", "REAL NOT NULL DEFAULT 0" },
                { "unit", "TEXT" },
                { "category", "TEXT" },
                { "description", "TEXT" },
                { "isAlert", "INTEGER NOT NULL DEFAULT 0" },
                { "alertMessage", "TEXT" },
                { "metadata", "TEXT" },
                { "createdAt", "INTEGER NOT NULL DEFAULT 0" },
                { "updatedAt", "INTEGER NOT NULL DEFAULT 0" }
        });
    }

    private static void createDashboardStatsTable(SupportSQLiteDatabase db) {
        db.execSQL(
                "CREATE TABLE IF NOT EXISTS `dashboard_stats` (" +
                        "`statType` TEXT NOT NULL PRIMARY KEY, " +
                        "`totalSent` INTEGER NOT NULL DEFAULT 0, " +
                        "`totalDelivered` INTEGER NOT NULL DEFAULT 0, " +
                        "`totalFailed` INTEGER NOT NULL DEFAULT 0, " +
                        "`totalPending` INTEGER NOT NULL DEFAULT 0, " +
                        "`activeCampaigns` INTEGER NOT NULL DEFAULT 0, " +
                        "`scheduledCampaigns` INTEGER NOT NULL DEFAULT 0, " +
                        "`totalCampaigns` INTEGER NOT NULL DEFAULT 0, " +
                        "`totalRecipients` INTEGER NOT NULL DEFAULT 0, " +
                        "`uniqueRecipients` INTEGER NOT NULL DEFAULT 0, " +
                        "`optOutCount` INTEGER NOT NULL DEFAULT 0, " +
                        "`complianceViolations` INTEGER NOT NULL DEFAULT 0, " +
                        "`averageDeliveryTime` INTEGER NOT NULL DEFAULT 0, " +
                        "`lastSentTime` INTEGER NOT NULL DEFAULT 0, " +
                        "`lastDeliveryTime` INTEGER NOT NULL DEFAULT 0, " +
                        "`totalCost` REAL NOT NULL DEFAULT 0, " +
                        "`totalRevenue` REAL NOT NULL DEFAULT 0, " +
                        "`conversionRate` REAL NOT NULL DEFAULT 0, " +
                        "`responseRate` REAL NOT NULL DEFAULT 0, " +
                        "`bounceRate` REAL NOT NULL DEFAULT 0, " +
                        "`peakHourActivity` INTEGER NOT NULL DEFAULT 0, " +
                        "`currentRateLimit` INTEGER NOT NULL DEFAULT 0, " +
                        "`rateLimitStatus` TEXT, " +
                        "`systemStatus` TEXT, " +
                        "`lastUpdated` INTEGER NOT NULL DEFAULT 0, " +
                        "`createdAt` INTEGER NOT NULL DEFAULT 0" +
                        ")");

        ensureColumns(db, "dashboard_stats", new String[][] {
                { "totalSent", "INTEGER NOT NULL DEFAULT 0" },
                { "totalDelivered", "INTEGER NOT NULL DEFAULT 0" },
                { "totalFailed", "INTEGER NOT NULL DEFAULT 0" },
                { "totalPending", "INTEGER NOT NULL DEFAULT 0" },
                { "activeCampaigns", "INTEGER NOT NULL DEFAULT 0" },
                { "scheduledCampaigns", "INTEGER NOT NULL DEFAULT 0" },
                { "totalCampaigns", "INTEGER NOT NULL DEFAULT 0" },
                { "totalRecipients", "INTEGER NOT NULL DEFAULT 0" },
                { "uniqueRecipients", "INTEGER NOT NULL DEFAULT 0" },
                { "optOutCount", "INTEGER NOT NULL DEFAULT 0" },
                { "complianceViolations", "INTEGER NOT NULL DEFAULT 0" },
                { "averageDeliveryTime", "INTEGER NOT NULL DEFAULT 0" },
                { "lastSentTime", "INTEGER NOT NULL DEFAULT 0" },
                { "lastDeliveryTime", "INTEGER NOT NULL DEFAULT 0" },
                { "totalCost", "REAL NOT NULL DEFAULT 0" },
                { "totalRevenue", "REAL NOT NULL DEFAULT 0" },
                { "conversionRate", "REAL NOT NULL DEFAULT 0" },
                { "responseRate", "REAL NOT NULL DEFAULT 0" },
                { "bounceRate", "REAL NOT NULL DEFAULT 0" },
                { "peakHourActivity", "INTEGER NOT NULL DEFAULT 0" },
                { "currentRateLimit", "INTEGER NOT NULL DEFAULT 0" },
                { "rateLimitStatus", "TEXT" },
                { "systemStatus", "TEXT" },
                { "lastUpdated", "INTEGER NOT NULL DEFAULT 0" },
                { "createdAt", "INTEGER NOT NULL DEFAULT 0" }
        });
    }

    private static void createDashboardMetricsTable(SupportSQLiteDatabase db) {
        db.execSQL(
                "CREATE TABLE IF NOT EXISTS `dashboard_metrics` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`metricDate` INTEGER NOT NULL DEFAULT 0, " +
                        "`metricType` TEXT, " +
                        "`sentCount` INTEGER NOT NULL DEFAULT 0, " +
                        "`deliveredCount` INTEGER NOT NULL DEFAULT 0, " +
                        "`failedCount` INTEGER NOT NULL DEFAULT 0, " +
                        "`pendingCount` INTEGER NOT NULL DEFAULT 0, " +
                        "`campaignCount` INTEGER NOT NULL DEFAULT 0, " +
                        "`activeCampaigns` INTEGER NOT NULL DEFAULT 0, " +
                        "`scheduledCampaigns` INTEGER NOT NULL DEFAULT 0, " +
                        "`optOutCount` INTEGER NOT NULL DEFAULT 0, " +
                        "`complianceViolations` INTEGER NOT NULL DEFAULT 0, " +
                        "`averageDeliveryTime` INTEGER NOT NULL DEFAULT 0, " +
                        "`peakHour` INTEGER NOT NULL DEFAULT -1, " +
                        "`totalRecipients` INTEGER NOT NULL DEFAULT 0, " +
                        "`uniqueRecipients` INTEGER NOT NULL DEFAULT 0, " +
                        "`totalCost` REAL NOT NULL DEFAULT 0, " +
                        "`totalRevenue` REAL NOT NULL DEFAULT 0, " +
                        "`conversionRate` REAL NOT NULL DEFAULT 0, " +
                        "`responseRate` REAL NOT NULL DEFAULT 0, " +
                        "`bounceRate` REAL NOT NULL DEFAULT 0, " +
                        "`createdAt` INTEGER NOT NULL DEFAULT 0, " +
                        "`updatedAt` INTEGER NOT NULL DEFAULT 0" +
                        ")");

        ensureColumns(db, "dashboard_metrics", new String[][] {
                { "metricDate", "INTEGER NOT NULL DEFAULT 0" },
                { "metricType", "TEXT" },
                { "sentCount", "INTEGER NOT NULL DEFAULT 0" },
                { "deliveredCount", "INTEGER NOT NULL DEFAULT 0" },
                { "failedCount", "INTEGER NOT NULL DEFAULT 0" },
                { "pendingCount", "INTEGER NOT NULL DEFAULT 0" },
                { "campaignCount", "INTEGER NOT NULL DEFAULT 0" },
                { "activeCampaigns", "INTEGER NOT NULL DEFAULT 0" },
                { "scheduledCampaigns", "INTEGER NOT NULL DEFAULT 0" },
                { "optOutCount", "INTEGER NOT NULL DEFAULT 0" },
                { "complianceViolations", "INTEGER NOT NULL DEFAULT 0" },
                { "averageDeliveryTime", "INTEGER NOT NULL DEFAULT 0" },
                { "peakHour", "INTEGER NOT NULL DEFAULT -1" },
                { "totalRecipients", "INTEGER NOT NULL DEFAULT 0" },
                { "uniqueRecipients", "INTEGER NOT NULL DEFAULT 0" },
                { "totalCost", "REAL NOT NULL DEFAULT 0" },
                { "totalRevenue", "REAL NOT NULL DEFAULT 0" },
                { "conversionRate", "REAL NOT NULL DEFAULT 0" },
                { "responseRate", "REAL NOT NULL DEFAULT 0" },
                { "bounceRate", "REAL NOT NULL DEFAULT 0" },
                { "createdAt", "INTEGER NOT NULL DEFAULT 0" },
                { "updatedAt", "INTEGER NOT NULL DEFAULT 0" }
        });
    }

    private static void createSyncStatusTable(SupportSQLiteDatabase db) {
        db.execSQL(
                "CREATE TABLE IF NOT EXISTS `sync_status` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`entityType` TEXT, " +
                        "`entityId` TEXT, " +
                        "`lastSyncAt` INTEGER NOT NULL DEFAULT 0, " +
                        "`lastServerModifiedAt` INTEGER NOT NULL DEFAULT 0, " +
                        "`status` TEXT, " +
                        "`conflictData` TEXT, " +
                        "`eTag` TEXT, " +
                        "`syncVersion` INTEGER NOT NULL DEFAULT 1, " +
                        "`pendingOperations` INTEGER NOT NULL DEFAULT 0, " +
                        "`lastError` TEXT" +
                        ")");

        ensureColumns(db, "sync_status", new String[][] {
                { "entityType", "TEXT" },
                { "entityId", "TEXT" },
                { "lastSyncAt", "INTEGER NOT NULL DEFAULT 0" },
                { "lastServerModifiedAt", "INTEGER NOT NULL DEFAULT 0" },
                { "status", "TEXT" },
                { "conflictData", "TEXT" },
                { "eTag", "TEXT" },
                { "syncVersion", "INTEGER NOT NULL DEFAULT 1" },
                { "pendingOperations", "INTEGER NOT NULL DEFAULT 0" },
                { "lastError", "TEXT" }
        });
    }

    private static void createSmsFtsTable(SupportSQLiteDatabase db) {
        db.execSQL("DROP TABLE IF EXISTS `sms_fts`");
        db.execSQL("CREATE VIRTUAL TABLE IF NOT EXISTS `sms_fts` USING FTS4(" +
                "`phoneNumber` TEXT, " +
                "`message` TEXT, " +
                "content='sms_entities')");

        db.execSQL("CREATE TRIGGER IF NOT EXISTS `sms_fts_ai` AFTER INSERT ON `sms_entities` " +
                "BEGIN " +
                "INSERT INTO `sms_fts`(rowid, `phoneNumber`, `message`) VALUES (new.`id`, new.`phoneNumber`, new.`message`);" +
                "END");

        db.execSQL("CREATE TRIGGER IF NOT EXISTS `sms_fts_ad` AFTER DELETE ON `sms_entities` " +
                "BEGIN " +
                "INSERT INTO `sms_fts`(`sms_fts`, rowid, `phoneNumber`, `message`) VALUES ('delete', old.`id`, old.`phoneNumber`, old.`message`);" +
                "END");

        db.execSQL("CREATE TRIGGER IF NOT EXISTS `sms_fts_au` AFTER UPDATE ON `sms_entities` " +
                "BEGIN " +
                "INSERT INTO `sms_fts`(`sms_fts`, rowid, `phoneNumber`, `message`) VALUES ('delete', old.`id`, old.`phoneNumber`, old.`message`);" +
                "INSERT INTO `sms_fts`(rowid, `phoneNumber`, `message`) VALUES (new.`id`, new.`phoneNumber`, new.`message`);" +
                "END");
    }

    private static void createIndexes(SupportSQLiteDatabase db) {
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_sms_entities_status` ON `sms_entities` (`status`)");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_sms_entities_campaignId` ON `sms_entities` (`campaignId`)");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_sms_entities_phoneNumber` ON `sms_entities` (`phoneNumber`)");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_sms_entities_createdAt` ON `sms_entities` (`createdAt`)");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_sms_entities_nextRetryAt` ON `sms_entities` (`nextRetryAt`)");
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_sms_entities_deviceSmsId` ON `sms_entities` (`deviceSmsId`)");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_sms_entities_boxType` ON `sms_entities` (`boxType`)");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_sms_entities_isRead` ON `sms_entities` (`isRead`)");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_sms_entities_threadId` ON `sms_entities` (`threadId`)");

        db.execSQL("CREATE INDEX IF NOT EXISTS `index_campaign_entities_status` ON `campaign_entities` (`status`)");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_campaign_entities_createdAt` ON `campaign_entities` (`createdAt`)");

        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_customers_phone` ON `customers` (`phone`)");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_customers_lastSeen` ON `customers` (`lastSeen`)");

        db.execSQL("CREATE INDEX IF NOT EXISTS `index_template_entities_category` ON `template_entities` (`category`)");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_template_entities_isFavorite` ON `template_entities` (`isFavorite`)");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_template_entities_usageCount` ON `template_entities` (`usageCount`)");

        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_opt_outs_phoneNumber` ON `opt_outs` (`phoneNumber`)");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_opt_outs_optOutTime` ON `opt_outs` (`optOutTime`)");

        db.execSQL("CREATE INDEX IF NOT EXISTS `index_scheduled_campaigns_campaignId` ON `scheduled_campaigns` (`campaignId`)");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_scheduled_campaigns_scheduledTime` ON `scheduled_campaigns` (`scheduledTime`)");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_scheduled_campaigns_status` ON `scheduled_campaigns` (`status`)");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_scheduled_campaigns_isActive` ON `scheduled_campaigns` (`isActive`)");

        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_conversations_phoneNumber` ON `conversations` (`phoneNumber`)");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_conversations_lastMessageTime` ON `conversations` (`lastMessageTime`)");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_conversations_threadId` ON `conversations` (`threadId`)");

        db.execSQL("CREATE INDEX IF NOT EXISTS `index_sms_queue_status` ON `sms_queue` (`status`)");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_sms_queue_nextRetryAt` ON `sms_queue` (`nextRetryAt`)");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_sms_queue_phoneNumber` ON `sms_queue` (`phoneNumber`)");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_sms_queue_createdAt` ON `sms_queue` (`createdAt`)");

        db.execSQL("CREATE INDEX IF NOT EXISTS `index_kpi_data_kpiType` ON `kpi_data` (`kpiType`)");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_kpi_data_timestamp` ON `kpi_data` (`timestamp`)");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_kpi_data_period` ON `kpi_data` (`period`)");

        db.execSQL("CREATE INDEX IF NOT EXISTS `index_dashboard_stats_statType` ON `dashboard_stats` (`statType`)");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_dashboard_stats_lastUpdated` ON `dashboard_stats` (`lastUpdated`)");

        db.execSQL("CREATE INDEX IF NOT EXISTS `index_dashboard_metrics_metricDate` ON `dashboard_metrics` (`metricDate`)");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_dashboard_metrics_metricType` ON `dashboard_metrics` (`metricType`)");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_dashboard_metrics_createdAt` ON `dashboard_metrics` (`createdAt`)");

        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_sync_status_entityType_entityId` ON `sync_status` (`entityType`, `entityId`)");
    }

    private static void dropLegacyIndexes(SupportSQLiteDatabase db) {
        db.execSQL("DROP INDEX IF EXISTS `idx_sms_status`");
        db.execSQL("DROP INDEX IF EXISTS `idx_sms_campaign`");
        db.execSQL("DROP INDEX IF EXISTS `idx_sms_phone`");
        db.execSQL("DROP INDEX IF EXISTS `idx_sms_created`");
        db.execSQL("DROP INDEX IF EXISTS `idx_sms_retry`");
        db.execSQL("DROP INDEX IF EXISTS `idx_sms_thread`");
        db.execSQL("DROP INDEX IF EXISTS `idx_campaign_status`");
        db.execSQL("DROP INDEX IF EXISTS `idx_campaign_created`");
        db.execSQL("DROP INDEX IF EXISTS `idx_customer_phone`");
        db.execSQL("DROP INDEX IF EXISTS `idx_customer_last_seen`");
        db.execSQL("DROP INDEX IF EXISTS `idx_template_category`");
        db.execSQL("DROP INDEX IF EXISTS `idx_template_favorite`");
        db.execSQL("DROP INDEX IF EXISTS `idx_template_usage`");
    }

    private static void ensureColumns(SupportSQLiteDatabase db, String table, String[][] columns) {
        for (String[] column : columns) {
            if (column.length < 2) {
                continue;
            }
            String name = column[0];
            String definition = column[1];
            if (!columnExists(db, table, name)) {
                db.execSQL("ALTER TABLE `" + table + "` ADD COLUMN `" + name + "` " + definition);
            }
        }
    }

    private static boolean columnExists(SupportSQLiteDatabase db, String table, String column) {
        Cursor cursor = null;
        try {
            cursor = db.query("PRAGMA table_info(`" + table + "`)");
            int nameIndex = cursor.getColumnIndex("name");
            while (cursor.moveToNext()) {
                String name = cursor.getString(nameIndex);
                if (column.equals(name)) {
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            return false;
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    private static boolean tableExists(SupportSQLiteDatabase db, String table) {
        Cursor cursor = null;
        try {
            cursor = db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='" + table + "'");
            return cursor.moveToFirst();
        } catch (Exception e) {
            return false;
        } finally {
            if (cursor != null) {
                cursor.close();
            }
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
