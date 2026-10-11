package com.afriserve.smsmanager.ui.dashboard;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.afriserve.smsmanager.AppConfig;
import com.afriserve.smsmanager.data.entity.SmsEntity;
import com.afriserve.smsmanager.data.repository.SmsRepository;
import com.afriserve.smsmanager.models.SmsModel;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import javax.inject.Inject;
import dagger.hilt.android.lifecycle.HiltViewModel;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * DashboardViewModel — v2
 *
 * Changes vs v1:
 *  - SimpleDateFormat now uses Africa/Nairobi (EAT) timezone so timestamps
 *    in the activity list display correctly for Kenyan users.
 *  - Added RECENT_ACTIVITY_LIMIT constant from AppConfig for consistency.
 *  - loadDashboardData() skips reload when data is already fresh (within
 *    AppConfig.Cache.DASHBOARD_STATS_TTL_MS) to avoid unnecessary DB hits.
 *  - disposables.dispose() in onCleared() (was disposables.clear()) to also
 *    prevent downstream observers from re-subscribing after ViewModel death.
 */
@HiltViewModel
public class DashboardViewModel extends ViewModel {

    public enum DashboardState { CONTENT, EMPTY, PERMISSION_REQUIRED, ERROR }

    private static final int RECENT_ACTIVITY_LIMIT = 10;
    /** Africa/Nairobi (UTC+3) for all time display. */
    private static final TimeZone EAT = TimeZone.getTimeZone("Africa/Nairobi");

    private final SmsRepository repository;
    private final CompositeDisposable disposables = new CompositeDisposable();

    private final MutableLiveData<DashboardStats> dashboardStats = new MutableLiveData<>();
    private final MutableLiveData<Boolean>         isLoading      = new MutableLiveData<>(false);
    private final MutableLiveData<String>          error          = new MutableLiveData<>();
    private final MutableLiveData<DashboardState>  dashboardState = new MutableLiveData<>(DashboardState.EMPTY);
    private final MutableLiveData<String>          stateMessage   = new MutableLiveData<>();

    /** Epoch-ms of last successful load; 0 = never loaded. */
    private volatile long lastLoadMs = 0L;

    @Inject
    public DashboardViewModel(SmsRepository repository) {
        this.repository = repository;
    }

    // ── LiveData getters ──────────────────────────────────────────────────────

    public LiveData<DashboardStats>  getDashboardStats()  { return dashboardStats; }
    public LiveData<Boolean>         getIsLoading()        { return isLoading;      }
    public LiveData<String>          getError()            { return error;          }
    public LiveData<DashboardState>  getDashboardState()   { return dashboardState; }
    public LiveData<String>          getStateMessage()     { return stateMessage;   }

    // ── Load ──────────────────────────────────────────────────────────────────

    /**
     * Loads dashboard data.  If the current data is fresh (within TTL) and a
     * force-reload is not requested, the existing value is kept.
     *
     * @param forceReload pass {@code true} to bypass the TTL cache.
     */
    public void loadDashboardData(boolean forceReload) {
        long now = System.currentTimeMillis();
        if (!forceReload && (now - lastLoadMs) < AppConfig.Cache.DASHBOARD_STATS_TTL_MS
                && dashboardStats.getValue() != null) {
            return;
        }

        isLoading.setValue(true);
        error.setValue(null);
        stateMessage.setValue(null);

        disposables.add(repository.getDashboardSnapshot(RECENT_ACTIVITY_LIMIT)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(snapshot -> {
                SmsStats smsStats = new SmsStats(
                        snapshot.totalSent,
                        snapshot.totalDelivered,
                        snapshot.totalFailed,
                        snapshot.totalQueued,
                        snapshot.trendPercent
                );
                DashboardStats stats = new DashboardStats(smsStats);
                stats.setRecentActivity(mapRecentActivity(snapshot.recentActivity));
                dashboardStats.setValue(stats);
                lastLoadMs = System.currentTimeMillis();

                dashboardState.setValue(stats.isEmpty()
                        ? DashboardState.EMPTY : DashboardState.CONTENT);
                isLoading.setValue(false);
            }, throwable -> {
                dashboardState.setValue(throwable instanceof SecurityException
                        ? DashboardState.PERMISSION_REQUIRED : DashboardState.ERROR);
                isLoading.setValue(false);
            }));
    }

    /** Convenience overload — always forces a fresh load. */
    public void loadDashboardData() {
        loadDashboardData(true);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private List<SmsModel> mapRecentActivity(List<SmsEntity> entities) {
        List<SmsModel> items = new ArrayList<>();
        if (entities == null) return items;

        SimpleDateFormat fmt = new SimpleDateFormat("hh:mm a", Locale.getDefault());
        fmt.setTimeZone(EAT); // Show times in Kenya local time

        for (SmsEntity e : entities) {
            String id      = String.valueOf(e.id);
            String address = e.phoneNumber != null ? e.phoneNumber : "";
            String body    = e.message    != null ? e.message    : "";
            String preview = body.length() > 40 ? body.substring(0, 40) + "..." : body;
            String time    = fmt.format(new Date(e.createdAt));
            int    type    = e.boxType  != null ? e.boxType  : 0;
            boolean read   = e.isRead   != null && e.isRead;
            int    thread  = e.threadId != null ? e.threadId.intValue() : 0;
            items.add(new SmsModel(id, address, preview, time, e.createdAt, type, read, thread));
        }
        return items;
    }

    @Override
    protected void onCleared() {
        disposables.dispose(); // dispose (not just clear) to stop in-flight subscriptions
        super.onCleared();
    }
}
