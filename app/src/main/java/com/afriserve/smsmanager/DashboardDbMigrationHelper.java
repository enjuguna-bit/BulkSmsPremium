package com.afriserve.smsmanager;

import android.content.Context;
import android.database.Cursor;
import android.util.Log;
import androidx.sqlite.db.SupportSQLiteDatabase;

/**
 * One-time helper that copies dashboard rows from the legacy AppDatabase
 * (version 8, which contained dashboard_stats and dashboard_metrics tables)
 * into the new standalone {@link DashboardDatabase}.
 *
 * <h3>When to run this</h3>
 * Call {@link #migrateFromAppDatabase(Context)} exactly once, during the
 * app's first launch after updating from a build that used AppDatabase v8.
 * Use a SharedPreference flag to ensure it only runs once:
 *
 * <pre>{@code
 * if (!prefs.getBoolean("dashboard_db_migrated", false)) {
 *     DashboardDbMigrationHelper.migrateFromAppDatabase(context);
 *     prefs.edit().putBoolean("dashboard_db_migrated", true).apply();
 * }
 * }</pre>
 *
 * <p>After migration, bump AppDatabase to version 9 with DROP TABLE migrations
 * for dashboard_stats and dashboard_metrics.
 *
 * <h3>Safety</h3>
 * If AppDatabase v8 tables don't exist (fresh install), this is a no-op.
 * DashboardDatabase uses {@code fallbackToDestructiveMigration} — dashboard data
 * is always re-computable from the SMS tables, so loss is acceptable.
 */
public final class DashboardDbMigrationHelper {

    private static final String TAG = "DashboardDbMigration";

    private DashboardDbMigrationHelper() { /* utility */ }

    /**
     * Copies rows from AppDatabase dashboard tables into DashboardDatabase.
     * Safe to call on a background thread. No-op if source tables are absent.
     *
     * @param context application context
     */
    public static void migrateFromAppDatabase(Context context) {
        AppDatabase      src  = AppDatabase.getInstance(context);
        DashboardDatabase dst = DashboardDatabase.getInstance(context);

        SupportSQLiteDatabase srcDb = src.getOpenHelper().getReadableDatabase();
        SupportSQLiteDatabase dstDb = dst.getOpenHelper().getWritableDatabase();

        migrateDashboardStats(srcDb, dstDb);
        migrateDashboardMetrics(srcDb, dstDb);
        Log.i(TAG, "Dashboard DB migration complete");
    }

    private static void migrateDashboardStats(SupportSQLiteDatabase src, SupportSQLiteDatabase dst) {
        if (!tableExists(src, "dashboard_stats")) {
            Log.d(TAG, "dashboard_stats table not found in AppDatabase — skipping");
            return;
        }
        try (Cursor c = src.query("SELECT * FROM dashboard_stats", null)) {
            if (c.getCount() == 0) return;
            dst.beginTransaction();
            try {
                dst.execSQL("DELETE FROM dashboard_stats");
                while (c.moveToNext()) {
                    StringBuilder cols = new StringBuilder();
                    StringBuilder vals = new StringBuilder();
                    for (int i = 0; i < c.getColumnCount(); i++) {
                        if (i > 0) { cols.append(','); vals.append(','); }
                        cols.append(c.getColumnName(i));
                        vals.append('?');
                    }
                    Object[] args = new Object[c.getColumnCount()];
                    for (int i = 0; i < c.getColumnCount(); i++) {
                        args[i] = c.isNull(i) ? null : c.getString(i);
                    }
                    dst.execSQL("INSERT OR IGNORE INTO dashboard_stats (" + cols + ") VALUES (" + vals + ")", args);
                }
                dst.setTransactionSuccessful();
                Log.d(TAG, "Migrated " + c.getCount() + " dashboard_stats rows");
            } finally {
                dst.endTransaction();
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to migrate dashboard_stats", e);
        }
    }

    private static void migrateDashboardMetrics(SupportSQLiteDatabase src, SupportSQLiteDatabase dst) {
        if (!tableExists(src, "dashboard_metrics")) {
            Log.d(TAG, "dashboard_metrics table not found in AppDatabase — skipping");
            return;
        }
        try (Cursor c = src.query("SELECT * FROM dashboard_metrics", null)) {
            if (c.getCount() == 0) return;
            dst.beginTransaction();
            try {
                dst.execSQL("DELETE FROM dashboard_metrics");
                while (c.moveToNext()) {
                    StringBuilder cols = new StringBuilder();
                    StringBuilder vals = new StringBuilder();
                    for (int i = 0; i < c.getColumnCount(); i++) {
                        if (i > 0) { cols.append(','); vals.append(','); }
                        cols.append(c.getColumnName(i));
                        vals.append('?');
                    }
                    Object[] args = new Object[c.getColumnCount()];
                    for (int i = 0; i < c.getColumnCount(); i++) {
                        args[i] = c.isNull(i) ? null : c.getString(i);
                    }
                    dst.execSQL("INSERT OR IGNORE INTO dashboard_metrics (" + cols + ") VALUES (" + vals + ")", args);
                }
                dst.setTransactionSuccessful();
                Log.d(TAG, "Migrated " + c.getCount() + " dashboard_metrics rows");
            } finally {
                dst.endTransaction();
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to migrate dashboard_metrics", e);
        }
    }

    private static boolean tableExists(SupportSQLiteDatabase db, String tableName) {
        try (Cursor c = db.query(
                "SELECT name FROM sqlite_master WHERE type='table' AND name=?",
                new Object[]{tableName})) {
            return c.getCount() > 0;
        } catch (Exception e) {
            return false;
        }
    }
}