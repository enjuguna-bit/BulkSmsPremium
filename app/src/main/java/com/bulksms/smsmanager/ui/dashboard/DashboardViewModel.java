package com.bulksms.smsmanager.ui.dashboard;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.bulksms.smsmanager.billing.FirebaseBillingManager;
import com.bulksms.smsmanager.billing.SubscriptionInfo;
import com.bulksms.smsmanager.models.SmsModel;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import javax.inject.Inject;
import dagger.hilt.android.lifecycle.HiltViewModel;

/**
 * ViewModel for Dashboard Fragment - handles data loading and state management
 */
@HiltViewModel
public class DashboardViewModel extends ViewModel {
    private final SmsRepository repository;

    private final MutableLiveData<DashboardStats> dashboardStats = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>(false);
    private final MutableLiveData<String> error = new MutableLiveData<>();

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

    public void loadDashboardData() {
        isLoading.setValue(true);
        error.setValue(null);

        // Load SMS stats
        repository.getSmsStats()
            .thenAccept(smsStats -> {
                try {
                    DashboardStats stats = new DashboardStats(smsStats, null);
                    dashboardStats.postValue(stats);
                } catch (Exception e) {
                    error.postValue("Failed to load dashboard data: " + e.getMessage());
                } finally {
                    isLoading.postValue(false);
                }
            })
            .exceptionally(throwable -> {
                error.postValue("Failed to load dashboard data: " + throwable.getMessage());
                isLoading.postValue(false);
                return null;
            });
    }

    public void loadRecentActivity() {
        repository.getRecentSmsActivity()
            .thenAccept(activity -> {
                DashboardStats currentStats = dashboardStats.getValue();
                if (currentStats != null) {
                    currentStats.setRecentActivity(activity);
                    dashboardStats.postValue(currentStats);
                }
            })
            .exceptionally(throwable -> {
                error.postValue("Failed to load recent activity: " + throwable.getMessage());
                return null;
            });
    }
}