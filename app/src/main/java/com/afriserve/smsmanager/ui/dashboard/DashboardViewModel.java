package com.afriserve.smsmanager.ui.dashboard;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.afriserve.smsmanager.data.entity.SmsEntity;
import com.afriserve.smsmanager.data.repository.SmsRepository;
import com.afriserve.smsmanager.models.SmsModel;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import javax.inject.Inject;
import dagger.hilt.android.lifecycle.HiltViewModel;
import io.reactivex.rxjava3.disposables.CompositeDisposable;

/**
 * ViewModel for Dashboard Fragment - handles data loading and state management
 */
@HiltViewModel
public class DashboardViewModel extends ViewModel {
    public enum DashboardState {
        CONTENT,
        EMPTY,
        PERMISSION_REQUIRED,
        ERROR
    }

    private static final int RECENT_ACTIVITY_LIMIT = 10;

    private final SmsRepository repository;
    private final CompositeDisposable disposables = new CompositeDisposable();

    private final MutableLiveData<DashboardStats> dashboardStats = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>(false);
    private final MutableLiveData<String> error = new MutableLiveData<>();
    private final MutableLiveData<DashboardState> dashboardState = new MutableLiveData<>(DashboardState.EMPTY);
    private final MutableLiveData<String> stateMessage = new MutableLiveData<>();

    @Inject
    public DashboardViewModel(SmsRepository repository) {
        this.repository = repository;
    }

    public LiveData<DashboardStats> getDashboardStats() {
        return dashboardStats;
    }

    public LiveData<Boolean> getIsLoading() {
        return isLoading;
    }

    public LiveData<String> getError() {
        return error;
    }

    public LiveData<DashboardState> getDashboardState() {
        return dashboardState;
    }

    public LiveData<String> getStateMessage() {
        return stateMessage;
    }

    public void loadDashboardData() {
        isLoading.setValue(true);
        error.setValue(null);
        stateMessage.setValue(null);

        disposables.add(repository.getDashboardSnapshot(RECENT_ACTIVITY_LIMIT)
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
                dashboardStats.postValue(stats);

                if (stats.isEmpty()) {
                    dashboardState.postValue(DashboardState.EMPTY);
                    stateMessage.postValue(null);
                } else {
                    dashboardState.postValue(DashboardState.CONTENT);
                }
                isLoading.postValue(false);
            }, throwable -> {
                if (throwable instanceof SecurityException) {
                    dashboardState.postValue(DashboardState.PERMISSION_REQUIRED);
                    stateMessage.postValue(null);
                } else {
                    dashboardState.postValue(DashboardState.ERROR);
                    stateMessage.postValue(null);
                }
                isLoading.postValue(false);
            }));
    }

    private List<SmsModel> mapRecentActivity(List<SmsEntity> entities) {
        List<SmsModel> items = new ArrayList<>();
        if (entities == null) {
            return items;
        }

        SimpleDateFormat formatter = new SimpleDateFormat("hh:mm a", Locale.getDefault());
        for (SmsEntity entity : entities) {
            String id = String.valueOf(entity.id);
            String address = entity.phoneNumber != null ? entity.phoneNumber : "";
            String body = entity.message != null ? entity.message : "";
            String preview = body.length() > 40 ? body.substring(0, 40) + "..." : body;
            String time = formatter.format(new Date(entity.createdAt));
            int type = entity.boxType != null ? entity.boxType : 0;
            boolean read = entity.isRead != null && entity.isRead;
            int threadId = entity.threadId != null ? entity.threadId.intValue() : 0;
            items.add(new SmsModel(id, address, preview, time, entity.createdAt, type, read, threadId));
        }
        return items;
    }

    @Override
    protected void onCleared() {
        disposables.clear();
        super.onCleared();
    }
}
