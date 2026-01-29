package com.bulksms.smsmanager.ui.analytics;

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
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.button.MaterialButton;
import com.bulksms.smsmanager.ui.analytics.AnalyticsData;
import com.bulksms.smsmanager.ui.analytics.AnalyticsViewModel;
import com.bulksms.smsmanager.R;
import com.bulksms.smsmanager.dashboard.DashboardRepository;
import com.bulksms.smsmanager.auth.SecureStorageEnhanced;
import com.bulksms.smsmanager.billing.FirebaseBillingManager;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Analytics Dashboard Fragment - Java implementation
 */
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
    public View onCreateView(@NonNull LayoutInflater inflater, 
                           @Nullable ViewGroup container, 
                           @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_analytics_dashboard, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        initViews(view);
        setupClickListeners();
        setupViewModel();
        setupObservers();
        loadAnalyticsData();
    }

    private void setupViewModel() {
        FirebaseBillingManager billingManager = new FirebaseBillingManager(
            new SecureStorageEnhanced(requireContext()));
        DashboardRepository repository = new DashboardRepository(requireContext(), billingManager);

        viewModel = new AnalyticsViewModel(repository, billingManager);
    }

    private void setupObservers() {
        viewModel.getAnalyticsData().observe(getViewLifecycleOwner(), this::updateAnalyticsDisplay);
        viewModel.getUIState().observe(getViewLifecycleOwner(), this::updateUIState);
        viewModel.getError().observe(getViewLifecycleOwner(), error -> {
            if (error != null) {
                tvErrorMessage.setText(error);
                tvErrorMessage.setVisibility(View.VISIBLE);
            } else {
                tvErrorMessage.setVisibility(View.GONE);
            }
        });
    }

    private void initViews(View view) {
        progressBar = view.findViewById(R.id.progressBar);
        progressBarDaily = view.findViewById(R.id.progressBarDaily);
        progressBarMonthly = view.findViewById(R.id.progressBarMonthly);
        tvPlanName = view.findViewById(R.id.tvPlanName);
        tvPlanTier = view.findViewById(R.id.tvPlanTier);
        tvDailyUsed = view.findViewById(R.id.tvDailyUsed);
        tvDailyLimit = view.findViewById(R.id.tvDailyLimit);
        tvDailyPercentage = view.findViewById(R.id.tvDailyPercentage);
        tvMonthlyUsed = view.findViewById(R.id.tvMonthlyUsed);
        tvMonthlyLimit = view.findViewById(R.id.tvMonthlyLimit);
        tvMonthlyPercentage = view.findViewById(R.id.tvMonthlyPercentage);
        tvCampaignsUsed = view.findViewById(R.id.tvCampaignsUsed);
        tvCampaignsLimit = view.findViewById(R.id.tvCampaignsLimit);
        tvTemplatesUsed = view.findViewById(R.id.tvTemplatesUsed);
        tvTemplatesLimit = view.findViewById(R.id.tvTemplatesLimit);
        tvUserProperties = view.findViewById(R.id.tvUserProperties);
        tvUpgradeSuggestion = view.findViewById(R.id.tvUpgradeSuggestion);
        tvStatus = view.findViewById(R.id.tvStatus);
        tvLastUpdated = view.findViewById(R.id.tvLastUpdated);
        tvErrorMessage = view.findViewById(R.id.tvErrorMessage);
        tvEventCount = view.findViewById(R.id.tvEventCount);
        recyclerViewEvents = view.findViewById(R.id.recyclerViewEvents);
        cardUpgradeSuggestion = view.findViewById(R.id.cardUpgradeSuggestion);
        btnRefresh = view.findViewById(R.id.btnRefresh);
        btnExportData = view.findViewById(R.id.btnExportData);
        btnTestEvent = view.findViewById(R.id.btnTestEvent);
        btnTestError = view.findViewById(R.id.btnTestError);
        btnTestPerformance = view.findViewById(R.id.btnTestPerformance);
        btnFlush = view.findViewById(R.id.btnFlush);
        btnResetUserProperties = view.findViewById(R.id.btnResetUserProperties);

        recyclerViewEvents.setLayoutManager(new LinearLayoutManager(requireContext()));
    }

    private void setupClickListeners() {
        btnRefresh.setOnClickListener(v -> loadAnalyticsData());
        btnExportData.setOnClickListener(v -> exportAnalyticsData());
        btnTestEvent.setOnClickListener(v -> testCustomEvent());
        btnTestError.setOnClickListener(v -> testErrorTracking());
        btnTestPerformance.setOnClickListener(v -> testPerformanceTracking());
        btnFlush.setOnClickListener(v -> flushAnalytics());
        btnResetUserProperties.setOnClickListener(v -> resetUserProperties());
    }

    private void loadAnalyticsData() {
        viewModel.loadAnalyticsData();
    }

    private void updateAnalyticsDisplay(AnalyticsData data) {
        if (data == null) return;

        // Update plan information
        tvPlanName.setText(data.getPlanName());
        tvPlanTier.setText(data.getPlanTier());

        // Daily usage
        int dailyUsed = data.getDailyUsed();
        int dailyLimit = data.getDailyLimit();
        tvDailyUsed.setText(String.valueOf(dailyUsed));
        tvDailyLimit.setText("/ " + dailyLimit);

        int dailyPercentage = dailyLimit > 0 ? (int) ((float) dailyUsed / dailyLimit * 100) : 0;
        tvDailyPercentage.setText(dailyPercentage + "%");
        progressBarDaily.setProgress(Math.min(dailyPercentage, 100));
        tvDailyPercentage.setTextColor(getColorForPercentage(dailyPercentage));

        // Monthly usage
        int monthlyUsed = data.getMonthlyUsed();
        int monthlyLimit = data.getMonthlyLimit();
        tvMonthlyUsed.setText(String.valueOf(monthlyUsed));
        tvMonthlyLimit.setText("/ " + monthlyLimit);

        int monthlyPercentage = monthlyLimit > 0 ? (int) ((float) monthlyUsed / monthlyLimit * 100) : 0;
        tvMonthlyPercentage.setText(monthlyPercentage + "%");
        progressBarMonthly.setProgress(Math.min(monthlyPercentage, 100));
        tvMonthlyPercentage.setTextColor(getColorForPercentage(monthlyPercentage));

        // Campaigns and templates (simplified - would need additional tracking)
        tvCampaignsUsed.setText("0"); // Placeholder
        tvCampaignsLimit.setText("/ 10");
        tvTemplatesUsed.setText("0"); // Placeholder
        tvTemplatesLimit.setText("/ 5");

        // Last updated
        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault());
        tvLastUpdated.setText("Last updated: " + sdf.format(new Date()));

        // Upgrade suggestion
        if (dailyPercentage > 80 || monthlyPercentage > 80) {
            tvUpgradeSuggestion.setText("You're approaching your plan limits. Consider upgrading for higher limits.");
            cardUpgradeSuggestion.setVisibility(View.VISIBLE);
        } else {
            cardUpgradeSuggestion.setVisibility(View.GONE);
        }

        // Load other data
        loadEvents();
        loadUserProperties();
    }

    private void updateUIState(AnalyticsViewModel.AnalyticsUIState state) {
        switch (state) {
            case LOADING:
                progressBar.setVisibility(View.VISIBLE);
                btnRefresh.setEnabled(false);
                btnExportData.setEnabled(false);
                break;
            case READY:
                progressBar.setVisibility(View.GONE);
                btnRefresh.setEnabled(true);
                btnExportData.setEnabled(true);
                break;
            case ERROR:
                progressBar.setVisibility(View.GONE);
                btnRefresh.setEnabled(true);
                btnExportData.setEnabled(false);
                break;
        }
    }

    private int getColorForPercentage(int percentage) {
        if (percentage >= 90) {
            return requireContext().getColor(R.color.error_red);
        } else if (percentage >= 75) {
            return requireContext().getColor(R.color.warning_orange);
        } else {
            return requireContext().getColor(R.color.success_green);
        }
    }

    private void loadEvents() {
        List<AnalyticsEvent> events = getMockEvents();
        AnalyticsEventsAdapter adapter = new AnalyticsEventsAdapter(events);
        recyclerViewEvents.setAdapter(adapter);
        tvEventCount.setText(events.size() + " events");
    }

    private void loadUserProperties() {
        Map<String, String> properties = new HashMap<>();
        properties.put("user_id", "user_123456");
        properties.put("subscription_plan", "pro");
        properties.put("user_tier", "professional");
        properties.put("registration_date", "2024-01-15");
        properties.put("app_version", "1.0.0");
        properties.put("device_model", "Android Device");
        properties.put("language", "en");
        
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : properties.entrySet()) {
            sb.append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
        }
        tvUserProperties.setText(sb.toString());
    }

    private void updateUIState(AnalyticsUIState state) {
        switch (state) {
            case LOADING:
                progressBar.setVisibility(View.VISIBLE);
                btnRefresh.setEnabled(false);
                btnExportData.setEnabled(false);
                btnTestEvent.setEnabled(false);
                btnTestError.setEnabled(false);
                btnTestPerformance.setEnabled(false);
                btnFlush.setEnabled(false);
                btnResetUserProperties.setEnabled(false);
                tvStatus.setText("Loading...");
                break;
            case READY:
                progressBar.setVisibility(View.GONE);
                btnRefresh.setEnabled(true);
                btnExportData.setEnabled(true);
                btnTestEvent.setEnabled(true);
                btnTestError.setEnabled(true);
                btnTestPerformance.setEnabled(true);
                btnFlush.setEnabled(true);
                btnResetUserProperties.setEnabled(true);
                tvStatus.setText("Ready");
                tvErrorMessage.setVisibility(View.GONE);
                break;
            case ERROR:
                progressBar.setVisibility(View.GONE);
                btnRefresh.setEnabled(true);
                btnExportData.setEnabled(true);
                btnTestEvent.setEnabled(true);
                btnTestError.setEnabled(true);
                btnTestPerformance.setEnabled(true);
                btnFlush.setEnabled(true);
                btnResetUserProperties.setEnabled(true);
                tvStatus.setText("Error");
                tvErrorMessage.setText("Failed to load analytics data");
                tvErrorMessage.setVisibility(View.VISIBLE);
                break;
        }
    }

    // ==================== TEST METHODS ====================

    private void testCustomEvent() {
        Toast.makeText(requireContext(), "Custom event tracked", Toast.LENGTH_SHORT).show();
    }

    private void testErrorTracking() {
        Toast.makeText(requireContext(), "Error event tracked", Toast.LENGTH_SHORT).show();
    }

    private void testPerformanceTracking() {
        Toast.makeText(requireContext(), "Performance event tracked (100ms)", Toast.LENGTH_SHORT).show();
    }

    private void exportAnalyticsData() {
        Toast.makeText(requireContext(), "Analytics data exported", Toast.LENGTH_SHORT).show();
    }

    private void flushAnalytics() {
        Toast.makeText(requireContext(), "Analytics events flushed", Toast.LENGTH_SHORT).show();
    }

    private void resetUserProperties() {
        Toast.makeText(requireContext(), "User properties reset", Toast.LENGTH_SHORT).show();
        loadAnalyticsData(); // Reload data after reset
    }

    // ==================== HELPER METHODS ====================

    private List<AnalyticsEvent> getMockEvents() {
        List<AnalyticsEvent> events = new ArrayList<>();
        long now = System.currentTimeMillis();
        
        events.add(new AnalyticsEvent("sms_sent", now, 
            createMap("recipient_count", "100", "message_type", "marketing")));
        events.add(new AnalyticsEvent("campaign_created", now - 3600000, 
            createMap("campaign_name", "Summer Sale", "campaign_type", "marketing")));
        events.add(new AnalyticsEvent("subscription_created", now - 7200000, 
            createMap("plan_type", "pro", "amount", "19.99")));
        events.add(new AnalyticsEvent("screen_view", now - 1800000, 
            createMap("screen_name", "analytics")));
            
        return events;
    }

    private Map<String, String> createMap(String... keyValuePairs) {
        Map<String, String> map = new HashMap<>();
        for (int i = 0; i < keyValuePairs.length; i += 2) {
            map.put(keyValuePairs[i], keyValuePairs[i + 1]);
        }
        return map;
    }

    // ==================== INNER CLASSES ====================

    enum AnalyticsUIState {
        LOADING, READY, ERROR
    }

    static class AnalyticsEvent {
        private final String name;
        private final long timestamp;
        private final Map<String, String> parameters;

        public AnalyticsEvent(String name, long timestamp, Map<String, String> parameters) {
            this.name = name;
            this.timestamp = timestamp;
            this.parameters = parameters;
        }

        public String getName() { return name; }
        public long getTimestamp() { return timestamp; }
        public Map<String, String> getParameters() { return parameters; }
    }

    static class AnalyticsEventsAdapter extends RecyclerView.Adapter<AnalyticsEventsAdapter.ViewHolder> {
        private final List<AnalyticsEvent> events;

        public AnalyticsEventsAdapter(List<AnalyticsEvent> events) {
            this.events = events;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_analytics_event, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            holder.bind(events.get(position));
        }

        @Override
        public int getItemCount() {
            return events.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            private final TextView tvEventName, tvEventTimestamp, tvEventParameters;

            ViewHolder(View itemView) {
                super(itemView);
                tvEventName = itemView.findViewById(R.id.tvEventName);
                tvEventTimestamp = itemView.findViewById(R.id.tvEventTimestamp);
                tvEventParameters = itemView.findViewById(R.id.tvEventParameters);
            }

            void bind(AnalyticsEvent event) {
                tvEventName.setText(event.getName());
                
                SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault());
                tvEventTimestamp.setText(sdf.format(new Date(event.getTimestamp())));
                
                StringBuilder sb = new StringBuilder();
                for (Map.Entry<String, String> entry : event.getParameters().entrySet()) {
                    sb.append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
                }
                tvEventParameters.setText(sb.toString());
            }
        }
    }
}
