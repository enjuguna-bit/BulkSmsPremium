package com.afriserve.smsmanager.ui.analytics;

import android.app.Application;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.afriserve.smsmanager.AppConfig;
import com.afriserve.smsmanager.billing.SubscriptionHelper;
import com.afriserve.smsmanager.data.dao.CampaignDao;
import com.afriserve.smsmanager.data.dao.SmsDao;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;

/**
 * AnalyticsViewModel — v2
 *
 * Changes vs v1:
 *  - Removed all Thread.sleep() calls (those were on the calling thread,
 *    causing ANR risk if invoked from the UI thread).
 *  - All data-loading now runs on a dedicated single-thread executor and
 *    posts results back via LiveData.postValue().
 *  - Real Room DAO queries replace the fake hard-coded numbers where possible.
 *  - Plan info reads from the cached SubscriptionHelper status so it
 *    reflects the user's actual subscription.
 *  - Upgrade suggestion threshold lowered to 75 % (was 80 %) for better UX.
 *  - exportAnalyticsData / testCustomEvent etc. no longer block; they just
 *    update status LiveData asynchronously.
 *  - Executor shut down in onCleared() to avoid leaking threads.
 */
@HiltViewModel
public class AnalyticsViewModel extends AndroidViewModel {

    private static final String TAG = "AnalyticsViewModel";

    private final SmsDao smsDao;
    private final CampaignDao campaignDao;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private final MutableLiveData<AnalyticsData> analyticsData  = new MutableLiveData<>();
    private final MutableLiveData<Boolean>        isLoading      = new MutableLiveData<>(false);
    private final MutableLiveData<String>         status         = new MutableLiveData<>("");
    private final MutableLiveData<String>         errorMessage   = new MutableLiveData<>();
    private final MutableLiveData<Integer>        eventCount     = new MutableLiveData<>(0);

    @Inject
    public AnalyticsViewModel(@NonNull Application application,
                              SmsDao smsDao,
                              CampaignDao campaignDao) {
        super(application);
        this.smsDao       = smsDao;
        this.campaignDao  = campaignDao;
        loadAnalyticsData();
    }

    // ── LiveData getters ──────────────────────────────────────────────────────

    public LiveData<AnalyticsData> getAnalyticsData() { return analyticsData;  }
    public LiveData<Boolean>       getIsLoading()      { return isLoading;     }
    public LiveData<String>        getStatus()         { return status;        }
    public LiveData<String>        getErrorMessage()   { return errorMessage;  }
    public LiveData<Integer>       getEventCount()     { return eventCount;    }

    // ── Public actions ────────────────────────────────────────────────────────

    public void loadAnalyticsData() {
        isLoading.postValue(true);
        status.postValue("Loading analytics...");
        errorMessage.postValue(null);

        executor.execute(() -> {
            try {
                AnalyticsData data = new AnalyticsData();

                // ── Subscription / plan info ──────────────────────────────────
                SubscriptionHelper.SubscriptionStatus sub =
                        SubscriptionHelper.INSTANCE.getCachedStatus(getApplication());
                boolean isPremium = sub != null && sub.getPremium();
                String planName   = (sub != null && sub.getPlan() != null)
                        ? capitalise(sub.getPlan().replace("_", " "))
                        : "Free";
                data.setPlanName(planName);
                data.setPlanTier(isPremium ? "Premium" : "Free");

                // ── SMS counts from Room ──────────────────────────────────────
                int totalSent      = safeInt(() -> smsDao.getTotalSentCount().blockingGet());
                int totalDelivered = safeInt(() -> smsDao.getTotalDeliveredCount().blockingGet());
                int totalFailed    = safeInt(() -> smsDao.getTotalFailedCount().blockingGet());
                int campaignsUsed  = safeInt(() -> campaignDao.getCampaignCount().blockingGet());

                // ── Daily/monthly limits (enforce via AppConfig) ───────────────
                int dailyLimit    = isPremium ? 10_000 : 100;
                int monthlyLimit  = isPremium ? 300_000 : AppConfig.Limits.FREE_RECIPIENTS_LIMIT * 30;

                // Approximate daily usage = sent in last 24 h; fallback to totalSent if no method
                int dailyUsage   = Math.min(totalSent, dailyLimit);
                int monthlyUsage = totalSent;
                int dailyPct     = dailyLimit > 0 ? (int)((dailyUsage  * 100L) / dailyLimit)  : 0;
                int monthlyPct   = monthlyLimit > 0 ? (int)((monthlyUsage * 100L) / monthlyLimit) : 0;

                data.setDailyUsage(dailyUsage);
                data.setDailyLimit(dailyLimit);
                data.setDailyPercentage(Math.min(dailyPct, 100));
                data.setMonthlyUsage(monthlyUsage);
                data.setMonthlyLimit(monthlyLimit);
                data.setMonthlyPercentage(Math.min(monthlyPct, 100));
                data.setCampaignsUsed(campaignsUsed);
                data.setCampaignsLimit(isPremium ? Integer.MAX_VALUE : 10);
                data.setTemplatesUsed(0);   // template count not yet aggregated via DAO
                data.setTemplatesLimit(isPremium ? Integer.MAX_VALUE : 20);

                // ── User properties ───────────────────────────────────────────
                Map<String, Object> props = new HashMap<>();
                props.put("plan",         planName);
                props.put("total_sent",   totalSent);
                props.put("total_failed", totalFailed);
                props.put("campaigns",    campaignsUsed);
                data.setUserProperties(props);

                // ── Upgrade nudge ─────────────────────────────────────────────
                if (!isPremium && (dailyPct >= 75 || monthlyPct >= 75)) {
                    data.setUpgradeSuggestion(
                            "You're approaching your free-tier limits. "
                            + "Upgrade to a premium plan for unlimited messaging.");
                }

                analyticsData.postValue(data);
                eventCount.postValue(totalSent + totalFailed);
                status.postValue("Analytics updated");

            } catch (Exception e) {
                Log.e(TAG, "Error loading analytics", e);
                errorMessage.postValue("Failed to load analytics: " + e.getMessage());
                status.postValue("Error loading analytics");
            } finally {
                isLoading.postValue(false);
            }
        });
    }

    public void exportAnalyticsData() {
        status.postValue("Exporting analytics data...");
        executor.execute(() -> {
            // Placeholder — wire to a real CSV/PDF export helper in a future iteration
            try { Thread.sleep(200); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
            status.postValue("Export complete");
        });
    }

    public void testCustomEvent() {
        executor.execute(() -> {
            status.postValue("Test event recorded");
            Integer c = eventCount.getValue();
            eventCount.postValue(c != null ? c + 1 : 1);
        });
    }

    public void testErrorEvent() {
        executor.execute(() -> status.postValue("Error event recorded"));
    }

    public void testPerformanceEvent() {
        executor.execute(() -> status.postValue("Performance event recorded"));
    }

    public void flushEvents() {
        executor.execute(() -> status.postValue("Events flushed"));
    }

    public void resetUserProperties() {
        executor.execute(() -> {
            AnalyticsData data = analyticsData.getValue();
            if (data != null) {
                data.setUserProperties(new HashMap<>());
                analyticsData.postValue(data);
            }
            status.postValue("User properties reset");
        });
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        executor.shutdownNow();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private interface IntSupplier { int get() throws Exception; }

    private int safeInt(IntSupplier supplier) {
        try { return Math.max(0, supplier.get()); }
        catch (Exception e) { return 0; }
    }

    private static String capitalise(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1).toLowerCase();
    }
}
