package com.afriserve.smsmanager;

import android.app.Application;
import android.os.StrictMode;
import android.util.Log;

import androidx.hilt.work.HiltWorkerFactory;
import androidx.work.Configuration;
import androidx.work.WorkManager;
import com.afriserve.smsmanager.workers.CampaignLifecycleWorker;

import javax.inject.Inject;

import dagger.hilt.android.HiltAndroidApp;

/**
 * BulkSmsApplication — v2
 *
 * Changes vs v1:
 *  - Removed duplicate WorkManager.initialize() call; {@link Configuration.Provider}
 *    interface already handles initialisation through Hilt's manifest-merger
 *    removal of the default initialiser.  We keep the try/catch as a safety net
 *    for edge-cases (e.g. multi-process scenarios).
 *  - StrictMode enabled in DEBUG builds to catch accidental main-thread I/O.
 *  - Thread-policy violations set to LOG only (not crash) so debug sessions
 *    remain usable while surfacing real issues in Logcat.
 *  - Minimal startup telemetry log helps correlate cold-start timing with
 *    Firebase Performance traces.
 */
@HiltAndroidApp
public class BulkSmsApplication extends Application implements Configuration.Provider {

    private static final String TAG = "BulkSmsApplication";

    @Inject
    HiltWorkerFactory workerFactory;

    @Inject
    BulkSmsService smsService;

    @Override
    public void onCreate() {
        super.onCreate();
        long t0 = System.currentTimeMillis();

        if (BuildConfig.DEBUG) {
            enableStrictMode();
        }

        initWorkManager();
        CampaignLifecycleWorker.schedule(this);

        Log.i(TAG, "Application ready in " + (System.currentTimeMillis() - t0) + " ms");
    }

    @Override
    public Configuration getWorkManagerConfiguration() {
        return new Configuration.Builder()
                .setWorkerFactory(workerFactory)
                // Limit concurrent workers to 20 on low-RAM devices; WorkManager
                // may reduce this further based on available threads.
                .setMaxSchedulerLimit(20)
                .setMinimumLoggingLevel(BuildConfig.DEBUG ? Log.DEBUG : Log.WARN)
                .build();
    }

    public BulkSmsService getSmsService() {
        return smsService;
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private void initWorkManager() {
        try {
            WorkManager.getInstance(this);
        } catch (IllegalStateException e) {
            // WorkManager not yet initialised (rare, e.g. secondary process)
            Log.w(TAG, "WorkManager not initialised — initialising manually", e);
            WorkManager.initialize(this, getWorkManagerConfiguration());
        }
    }

    private void enableStrictMode() {
        StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder()
                .detectDiskReads()
                .detectDiskWrites()
                .detectNetwork()
                .penaltyLog()  // Log only — do not crash; investigate in Logcat
                .build());

        StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder()
                .detectLeakedSqlLiteObjects()
                .detectLeakedClosableObjects()
                .detectActivityLeaks()
                .penaltyLog()
                .build());
    }
}
