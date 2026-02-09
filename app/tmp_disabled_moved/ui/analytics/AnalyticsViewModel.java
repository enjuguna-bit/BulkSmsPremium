package com.bulksms.smsmanager.ui.analytics;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.bulksms.smsmanager.dashboard.DashboardRepository;
import com.bulksms.smsmanager.dashboard.SmsStats; 

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

/**
 * ViewModel for Analytics Dashboard Fragment
 */
public class AnalyticsViewModel extends ViewModel {
    private final DashboardRepository repository;

    private final MutableLiveData<AnalyticsData> analyticsData = new MutableLiveData<>();
    private final MutableLiveData<AnalyticsUIState> uiState = new MutableLiveData<>(AnalyticsUIState.LOADING);
    private final MutableLiveData<String> error = new MutableLiveData<>();

    public AnalyticsViewModel(DashboardRepository repository) {
        this.repository = repository;
    }

    public LiveData<AnalyticsData> getAnalyticsData() {
        return analyticsData;
    }

    public LiveData<AnalyticsUIState> getUIState() {
        return uiState;
    }

    public LiveData<String> getError() {
        return error;
    }

    public void loadAnalyticsData() {
        uiState.setValue(AnalyticsUIState.LOADING);
        error.setValue(null);

        repository.loadDashboardStats(new DashboardRepository.Callback<com.bulksms.smsmanager.dashboard.DashboardStats>() {
            @Override
            public void onSuccess(com.bulksms.smsmanager.dashboard.DashboardStats dashboardStats) {
                AnalyticsData data = new AnalyticsData(dashboardStats.getSmsStats());
                analyticsData.postValue(data);
                uiState.postValue(AnalyticsUIState.READY);
            }

            @Override
            public void onError(String errorMessage) {
                error.postValue(errorMessage);
                uiState.postValue(AnalyticsUIState.ERROR);
            }
        });
    }

    public enum AnalyticsUIState {
        LOADING,
        READY,
        ERROR
    }
}