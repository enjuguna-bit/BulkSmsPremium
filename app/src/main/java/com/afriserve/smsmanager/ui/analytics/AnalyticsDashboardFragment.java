package com.afriserve.smsmanager.ui.analytics;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.button.MaterialButton;

import com.afriserve.smsmanager.R;
import com.afriserve.smsmanager.ui.analytics.AnalyticsData;
import com.afriserve.smsmanager.ui.analytics.AnalyticsViewModel;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import dagger.hilt.android.AndroidEntryPoint;

/**
 * Analytics Dashboard Fragment - Java implementation
 */
@AndroidEntryPoint
public class AnalyticsDashboardFragment extends Fragment {
    private ProgressBar progressBar, progressBarDaily, progressBarMonthly;
    private TextView tvPlanName, tvPlanTier, tvDailyUsed, tvDailyLimit, tvDailyPercentage,
                     tvMonthlyUsed, tvMonthlyLimit, tvMonthlyPercentage, tvCampaignsUsed,
                     tvCampaignsLimit, tvTemplatesUsed, tvTemplatesLimit, tvUserProperties,
                     tvUpgradeSuggestion, tvStatus, tvLastUpdated, tvErrorMessage, tvEventCount;
    private RecyclerView recyclerViewEvents;
    private MaterialCardView cardUpgradeSuggestion;
    private MaterialButton btnRefresh, btnExportData, btnTestEvent, btnTestError, 
                          btnTestPerformance, btnFlush, btnResetUserProperties;
    private AnalyticsViewModel viewModel;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                           @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_analytics_dashboard, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        viewModel = new ViewModelProvider(this).get(AnalyticsViewModel.class);
        
        initViews(view);
        setupClickListeners();
        observeViewModel();
        
        // Load initial data
        viewModel.loadAnalyticsData();
    }

    private void initViews(View view) {
        // Progress bars
        progressBar = view.findViewById(R.id.progressBar);
        progressBarDaily = view.findViewById(R.id.progressBarDaily);
        progressBarMonthly = view.findViewById(R.id.progressBarMonthly);
        
        // Plan info
        tvPlanName = view.findViewById(R.id.tvPlanName);
        tvPlanTier = view.findViewById(R.id.tvPlanTier);
        
        // Usage stats
        tvDailyUsed = view.findViewById(R.id.tvDailyUsed);
        tvDailyLimit = view.findViewById(R.id.tvDailyLimit);
        tvDailyPercentage = view.findViewById(R.id.tvDailyPercentage);
        tvMonthlyUsed = view.findViewById(R.id.tvMonthlyUsed);
        tvMonthlyLimit = view.findViewById(R.id.tvMonthlyLimit);
        tvMonthlyPercentage = view.findViewById(R.id.tvMonthlyPercentage);
        
        // Limits
        tvCampaignsUsed = view.findViewById(R.id.tvCampaignsUsed);
        tvCampaignsLimit = view.findViewById(R.id.tvCampaignsLimit);
        tvTemplatesUsed = view.findViewById(R.id.tvTemplatesUsed);
        tvTemplatesLimit = view.findViewById(R.id.tvTemplatesLimit);
        
        // Status and info
        tvUserProperties = view.findViewById(R.id.tvUserProperties);
        tvUpgradeSuggestion = view.findViewById(R.id.tvUpgradeSuggestion);
        tvStatus = view.findViewById(R.id.tvStatus);
        tvLastUpdated = view.findViewById(R.id.tvLastUpdated);
        tvErrorMessage = view.findViewById(R.id.tvErrorMessage);
        tvEventCount = view.findViewById(R.id.tvEventCount);
        
        // Cards and lists
        cardUpgradeSuggestion = view.findViewById(R.id.cardUpgradeSuggestion);
        recyclerViewEvents = view.findViewById(R.id.recyclerViewEvents);
        
        // Buttons
        btnRefresh = view.findViewById(R.id.btnRefresh);
        btnExportData = view.findViewById(R.id.btnExportData);
        btnTestEvent = view.findViewById(R.id.btnTestEvent);
        btnTestError = view.findViewById(R.id.btnTestError);
        btnTestPerformance = view.findViewById(R.id.btnTestPerformance);
        btnFlush = view.findViewById(R.id.btnFlush);
        btnResetUserProperties = view.findViewById(R.id.btnResetUserProperties);
        
        // Setup recycler view
        if (recyclerViewEvents != null) {
            recyclerViewEvents.setLayoutManager(new LinearLayoutManager(requireContext()));
        }
    }

    private void setupClickListeners() {
        if (btnRefresh != null) {
            btnRefresh.setOnClickListener(v -> viewModel.loadAnalyticsData());
        }
        
        if (btnExportData != null) {
            btnExportData.setOnClickListener(v -> viewModel.exportAnalyticsData());
        }
        
        if (btnTestEvent != null) {
            btnTestEvent.setOnClickListener(v -> viewModel.testCustomEvent());
        }
        
        if (btnTestError != null) {
            btnTestError.setOnClickListener(v -> viewModel.testErrorEvent());
        }
        
        if (btnTestPerformance != null) {
            btnTestPerformance.setOnClickListener(v -> viewModel.testPerformanceEvent());
        }
        
        if (btnFlush != null) {
            btnFlush.setOnClickListener(v -> viewModel.flushEvents());
        }
        
        if (btnResetUserProperties != null) {
            btnResetUserProperties.setOnClickListener(v -> viewModel.resetUserProperties());
        }
    }

    private void observeViewModel() {
        viewModel.getIsLoading().observe(getViewLifecycleOwner(), isLoading -> {
            if (progressBar != null) {
                progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
            }
            
            // Disable buttons during loading
            boolean enabled = !isLoading;
            if (btnRefresh != null) btnRefresh.setEnabled(enabled);
            if (btnExportData != null) btnExportData.setEnabled(enabled);
            if (btnTestEvent != null) btnTestEvent.setEnabled(enabled);
            if (btnTestError != null) btnTestError.setEnabled(enabled);
            if (btnTestPerformance != null) btnTestPerformance.setEnabled(enabled);
        });

        viewModel.getAnalyticsData().observe(getViewLifecycleOwner(), this::updateAnalyticsUI);
        
        viewModel.getStatus().observe(getViewLifecycleOwner(), status -> {
            if (tvStatus != null) {
                tvStatus.setText(status);
            }
        });

        viewModel.getErrorMessage().observe(getViewLifecycleOwner(), error -> {
            if (tvErrorMessage != null) {
                tvErrorMessage.setText(error);
                tvErrorMessage.setVisibility(error != null && !error.isEmpty() ? View.VISIBLE : View.GONE);
            }
        });

        viewModel.getEventCount().observe(getViewLifecycleOwner(), count -> {
            if (tvEventCount != null) {
                tvEventCount.setText("Events: " + count);
            }
        });
    }

    private void updateAnalyticsUI(AnalyticsData data) {
        if (data == null) return;

        // Update plan info
        if (tvPlanName != null) {
            tvPlanName.setText(data.getPlanName());
        }
        if (tvPlanTier != null) {
            tvPlanTier.setText(data.getPlanTier());
        }

        // Update daily usage
        if (tvDailyUsed != null) {
            tvDailyUsed.setText(String.valueOf(data.getDailyUsage()));
        }
        if (tvDailyLimit != null) {
            tvDailyLimit.setText(String.valueOf(data.getDailyLimit()));
        }
        if (tvDailyPercentage != null) {
            tvDailyPercentage.setText(data.getDailyPercentage() + "%");
        }
        if (progressBarDaily != null) {
            progressBarDaily.setProgress(data.getDailyPercentage());
        }

        // Update monthly usage
        if (tvMonthlyUsed != null) {
            tvMonthlyUsed.setText(String.valueOf(data.getMonthlyUsage()));
        }
        if (tvMonthlyLimit != null) {
            tvMonthlyLimit.setText(String.valueOf(data.getMonthlyLimit()));
        }
        if (tvMonthlyPercentage != null) {
            tvMonthlyPercentage.setText(data.getMonthlyPercentage() + "%");
        }
        if (progressBarMonthly != null) {
            progressBarMonthly.setProgress(data.getMonthlyPercentage());
        }

        // Update limits
        if (tvCampaignsUsed != null) {
            tvCampaignsUsed.setText(String.valueOf(data.getCampaignsUsed()));
        }
        if (tvCampaignsLimit != null) {
            tvCampaignsLimit.setText("/" + data.getCampaignsLimit());
        }
        if (tvTemplatesUsed != null) {
            tvTemplatesUsed.setText(String.valueOf(data.getTemplatesUsed()));
        }
        if (tvTemplatesLimit != null) {
            tvTemplatesLimit.setText("/" + data.getTemplatesLimit());
        }

        // Update user properties
        if (tvUserProperties != null) {
            tvUserProperties.setText(formatUserProperties(data.getUserProperties()));
        }

        // Update upgrade suggestion
        if (tvUpgradeSuggestion != null && cardUpgradeSuggestion != null) {
            String suggestion = data.getUpgradeSuggestion();
            if (suggestion != null && !suggestion.isEmpty()) {
                tvUpgradeSuggestion.setText(suggestion);
                cardUpgradeSuggestion.setVisibility(View.VISIBLE);
            } else {
                cardUpgradeSuggestion.setVisibility(View.GONE);
            }
        }

        // Update last updated time
        if (tvLastUpdated != null) {
            String timestamp = new SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
                    .format(new Date(data.getLastUpdated()));
            tvLastUpdated.setText("Last updated: " + timestamp);
        }
    }

    private String formatUserProperties(Map<String, Object> properties) {
        if (properties == null || properties.isEmpty()) {
            return "No user properties set";
        }

        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Object> entry : properties.entrySet()) {
            sb.append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
        }
        return sb.toString().trim();
    }

    private void showMessage(String message) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
    }
}
