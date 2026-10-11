package com.afriserve.smsmanager.workers;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.afriserve.smsmanager.AppDatabase;
import com.afriserve.smsmanager.data.entity.CampaignEntity;
import com.afriserve.smsmanager.data.export.CampaignReportExporter;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Daily 4pm snapshot plus previous-day closure, using Nairobi business time. */
public class CampaignLifecycleWorker extends Worker {
    public static final String UNIQUE_NAME = "campaign-lifecycle-daily";
    /** Separate startup check so an overdue campaign is not delayed until tomorrow's 4pm run. */
    private static final String STARTUP_CHECK_NAME = "campaign-lifecycle-startup-check";
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Africa/Nairobi");

    public CampaignLifecycleWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        try {
            AppDatabase db = AppDatabase.getInstance(getApplicationContext());
            ZonedDateTime now = ZonedDateTime.now(BUSINESS_ZONE);
            String today = now.toLocalDate().toString();
            List<CampaignEntity> open = db.campaignDao().getOpenCampaignsBefore(today).blockingGet();
            for (CampaignEntity campaign : open) {
                db.campaignDao().closeCampaign(campaign.id, System.currentTimeMillis()).blockingAwait();
                CampaignReportExporter.export(getApplicationContext(), campaign.id);
                db.campaignDao().markSnapshot(campaign.id, System.currentTimeMillis()).blockingAwait();
            }

            // Snapshot all campaigns created today at/after the daily snapshot point.
            if (now.getHour() >= 16) {
                List<CampaignEntity> todayCampaigns = db.campaignDao().getCampaignsByDate(today).blockingGet();
                for (CampaignEntity campaign : todayCampaigns) {
                    CampaignReportExporter.export(getApplicationContext(), campaign.id);
                    db.campaignDao().markSnapshot(campaign.id, System.currentTimeMillis()).blockingAwait();
                }
            }
            enqueueNext(getApplicationContext());
            return Result.success();
        } catch (Exception e) {
            enqueueNext(getApplicationContext());
            return Result.retry();
        }
    }

    public static void schedule(Context context) {
        Context appContext = context.getApplicationContext();
        WorkManager workManager = WorkManager.getInstance(appContext);

        // Run once when the app process starts. This closes campaigns from prior
        // business dates immediately, even when the app was launched after 4pm.
        OneTimeWorkRequest startupCheck = new OneTimeWorkRequest.Builder(CampaignLifecycleWorker.class)
                .build();
        workManager.enqueueUniqueWork(
                STARTUP_CHECK_NAME,
                ExistingWorkPolicy.REPLACE,
                startupCheck);

        // Keep the regular Nairobi 4pm lifecycle/snapshot schedule.
        enqueueNext(appContext);
    }

    private static void enqueueNext(Context context) {
        ZonedDateTime now = ZonedDateTime.now(BUSINESS_ZONE);
        ZonedDateTime next = now.withHour(16).withMinute(0).withSecond(0).withNano(0);
        if (!next.isAfter(now)) next = next.plusDays(1);
        long delay = Math.max(60_000L, Duration.between(Instant.now(), next.toInstant()).toMillis());
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(CampaignLifecycleWorker.class)
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .build();
        WorkManager.getInstance(context).enqueueUniqueWork(UNIQUE_NAME, ExistingWorkPolicy.REPLACE, request);
    }
}
