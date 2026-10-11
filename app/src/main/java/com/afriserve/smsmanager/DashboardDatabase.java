package com.afriserve.smsmanager;

import android.content.Context;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;

import com.afriserve.smsmanager.data.entity.DashboardMetricsEntity;
import com.afriserve.smsmanager.data.entity.DashboardStatsEntity;

/**
 * Dedicated Room database for dashboard / analytics snapshot tables.
 *
 * <h3>Why a separate database?</h3>
 * Dashboard entities ({@link DashboardStatsEntity}, {@link DashboardMetricsEntity}) are
 * write-heavy analytics snapshots that are independent of the messaging data model.
 * Keeping them in AppDatabase means every dashboard schema change forces a version
 * bump on the primary DB — risking data loss for SMS, campaigns, conversations, etc.
 *
 * <h3>Migration strategy</h3>
 * If you are upgrading from AppDatabase version 8 (which contained these tables):
 * <ol>
 *   <li>Run the one-time data migration utility
 *       {@code DashboardDbMigrationHelper.migrateFromAppDatabase(context)} BEFORE
 *       opening DashboardDatabase for the first time.</li>
 *   <li>After migration succeeds, remove {@link DashboardStatsEntity} and
 *       {@link DashboardMetricsEntity} from {@code AppDatabase}'s entity list and
 *       bump its version with a DROP TABLE migration.</li>
 * </ol>
 */
@Database(
    entities = {
        DashboardStatsEntity.class,
        DashboardMetricsEntity.class
    },
    version = 1,
    exportSchema = true
)
@TypeConverters({ AppDatabase.Converters.class })
public abstract class DashboardDatabase extends RoomDatabase {

    private static volatile DashboardDatabase INSTANCE;
    private static final String DB_NAME = "bulksms_dashboard_db";

    /**
     * Returns the singleton DashboardDatabase instance.
     *
     * <p>Call {@code DashboardDbMigrationHelper.migrateFromAppDatabase(context)}
     * once before this if upgrading from AppDatabase v8.
     */
    public static DashboardDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (DashboardDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                            context.getApplicationContext(),
                            DashboardDatabase.class,
                            DB_NAME
                    )
                    .fallbackToDestructiveMigration() // Dashboard data is always re-computed; safe.
                    .build();
                }
            }
        }
        return INSTANCE;
    }

    /** For testing only — inject a pre-built instance. */
    static void setTestInstance(DashboardDatabase instance) {
        INSTANCE = instance;
    }
}
