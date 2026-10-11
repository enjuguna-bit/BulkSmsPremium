package com.afriserve.smsmanager;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.NavOptions;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.afriserve.smsmanager.models.SmsModel;
import com.afriserve.smsmanager.ui.dashboard.DashboardViewModel;
import com.afriserve.smsmanager.ui.dashboard.SmsStats;
import com.google.android.material.snackbar.Snackbar;

import com.afriserve.smsmanager.AppConfig;
import dagger.hilt.android.AndroidEntryPoint;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Modern Dashboard Fragment with analytics and quick actions
 */
@AndroidEntryPoint
public class DashboardFragment extends Fragment {
    private SwipeRefreshLayout swipeRefresh;
    private TextView txtSmsSent, txtDeliveryRate, txtDeliveryStatus, txtFailed,
                     txtQueued, txtSmsTrend, txtNoActivity, txtDashboardUpdated;
    private ImageView imgTrendIndicator;
    private RecyclerView recyclerActivity;
    private CardView actionBulkSms, actionSingleSms, actionInbox, actionSettings;
    private View actionCampaignHistory;

    private ActivityAdapter activityAdapter;
    private final List<SmsModel> activityList = new ArrayList<>();
    private DashboardViewModel viewModel;
    private long lastRefreshMs = 0L;
    private final Handler liveHandler = new Handler(Looper.getMainLooper());
    private final Runnable liveRefresh = new Runnable() {
        @Override public void run() {
            if (isAdded()) {
                refreshDashboard();
                liveHandler.postDelayed(this, 30_000L);
            }
        }
    };

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, 
                           @Nullable ViewGroup container, 
                           @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_dashboard, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(this).get(DashboardViewModel.class);
        initViews(view);
        setupQuickActions();
        setupActivityList();
        setupObservers();
        loadInitialData();

        swipeRefresh.setOnRefreshListener(this::refreshDashboard);
    }

    private void refreshDashboard() {
        viewModel.loadDashboardData();
    }

    private void setupObservers() {
        viewModel.getDashboardStats().observe(getViewLifecycleOwner(), this::updateUI);
        viewModel.getIsLoading().observe(getViewLifecycleOwner(), isLoading -> {
            swipeRefresh.setRefreshing(isLoading);
        });
        viewModel.getDashboardState().observe(getViewLifecycleOwner(), state ->
                applyDashboardState(state, viewModel.getStateMessage().getValue()));
        viewModel.getStateMessage().observe(getViewLifecycleOwner(), message ->
                applyDashboardState(viewModel.getDashboardState().getValue(), message));
        viewModel.getError().observe(getViewLifecycleOwner(), error -> {
            if (error != null) {
                showError(error);
            }
        });
    }

    private void updateUI(com.afriserve.smsmanager.ui.dashboard.DashboardStats stats) {
        if (stats == null) return;

        animateText(txtSmsSent, String.valueOf(stats.getSmsStats().getTotalSent()));
        animateText(txtDeliveryRate, String.format(Locale.getDefault(), "%.1f%%", stats.getSmsStats().getDeliveryRate()));
        animateText(txtFailed, String.valueOf(stats.getSmsStats().getTotalFailed()));
        animateText(txtQueued, String.valueOf(stats.getSmsStats().getTotalQueued()));
        if (txtDashboardUpdated != null) {
            txtDashboardUpdated.setText("● Live · Updated just now");
            txtDashboardUpdated.setTextColor(ContextCompat.getColor(requireContext(), R.color.status_success));
        }
        updateDeliveryStatus(stats.getSmsStats().getDeliveryStatus());
        updateTrendDisplay(stats.getSmsStats());

        activityList.clear();
        if (stats.getRecentActivity() != null) {
            activityList.addAll(stats.getRecentActivity());
        }
        activityAdapter.notifyDataSetChanged();
        updateActivityStateVisibility();
    }

    private void loadInitialData() {
        viewModel.loadDashboardData();
    }

    private void initViews(View view) {
        swipeRefresh = view.findViewById(R.id.swipeRefresh);
        txtSmsSent = view.findViewById(R.id.txtSmsSent);
        txtDeliveryRate = view.findViewById(R.id.txtDeliveryRate);
        txtDeliveryStatus = view.findViewById(R.id.txtDeliveryStatus);
        txtFailed = view.findViewById(R.id.txtFailed);
        txtQueued = view.findViewById(R.id.txtQueued);
        txtSmsTrend = view.findViewById(R.id.txtSmsTrend);
        imgTrendIndicator = view.findViewById(R.id.imgTrendIndicator);
        txtNoActivity = view.findViewById(R.id.txtNoActivity);
        txtDashboardUpdated = view.findViewById(R.id.txtDashboardUpdated);
        recyclerActivity = view.findViewById(R.id.recyclerActivity);
        actionBulkSms = view.findViewById(R.id.actionBulkSms);
        actionSingleSms = view.findViewById(R.id.actionSingleSms);
        actionInbox = view.findViewById(R.id.actionInbox);
        actionSettings = view.findViewById(R.id.actionSettings);
        actionCampaignHistory = view.findViewById(R.id.actionCampaignHistory);
    }

    private void setupQuickActions() {
        actionBulkSms.setOnClickListener(v -> navigateTo(R.id.nav_bulk));
        actionSingleSms.setOnClickListener(v -> navigateTo(R.id.nav_send));
        actionInbox.setOnClickListener(v -> navigateTo(R.id.nav_inbox));
        actionSettings.setOnClickListener(v -> navigateTo(R.id.nav_settings));
        actionCampaignHistory.setOnClickListener(v -> navigateTo(R.id.nav_campaign_history));
    }

    private void navigateTo(int destination) {
        try {
            NavController navController = NavHostFragment.findNavController(this);
            if (navController.getCurrentDestination() != null
                    && navController.getCurrentDestination().getId() == destination) {
                return;
            }
            NavOptions options = new NavOptions.Builder()
                    .setLaunchSingleTop(true)
                    .build();
            navController.navigate(destination, null, options);
        } catch (Exception e) {
            Toast.makeText(requireContext(), getString(R.string.dashboard_navigation_error), Toast.LENGTH_SHORT).show();
        }
    }

    private void setupActivityList() {
        activityAdapter = new ActivityAdapter(activityList);
        recyclerActivity.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerActivity.setAdapter(activityAdapter);
    }

    private void updateDeliveryStatus(SmsStats.DeliveryStatus status) {
        SmsStats currentStats = viewModel.getDashboardStats().getValue() != null
                ? viewModel.getDashboardStats().getValue().getSmsStats() : null;
        if (currentStats != null && currentStats.getTotalSent() == 0 && currentStats.getTotalQueued() == 0) {
            txtDeliveryStatus.setText("No activity yet");
            txtDeliveryStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.status_muted));
            return;
        }
        int textColor;
        int labelRes;
        switch (status) {
            case EXCELLENT:
                textColor = ContextCompat.getColor(requireContext(), R.color.status_success);
                labelRes = R.string.dashboard_delivery_status_excellent;
                break;
            case GOOD:
                textColor = ContextCompat.getColor(requireContext(), R.color.status_info);
                labelRes = R.string.dashboard_delivery_status_good;
                break;
            case FAIR:
                textColor = ContextCompat.getColor(requireContext(), R.color.status_warning);
                labelRes = R.string.dashboard_delivery_status_fair;
                break;
            case POOR:
            default:
                textColor = ContextCompat.getColor(requireContext(), R.color.color_error);
                labelRes = R.string.dashboard_delivery_status_poor;
                break;
        }
        txtDeliveryStatus.setText(labelRes);
        txtDeliveryStatus.setTextColor(textColor);
    }

    private void updateTrendDisplay(SmsStats stats) {
        if (stats.getTotalSent() == 0 && stats.getTotalQueued() == 0) {
            imgTrendIndicator.setVisibility(View.INVISIBLE);
            txtSmsTrend.setText("Send your first SMS to see a trend");
            txtSmsTrend.setTextColor(ContextCompat.getColor(requireContext(), R.color.status_muted));
            return;
        }
        imgTrendIndicator.setVisibility(View.VISIBLE);
        float absTrend = Math.abs(stats.getTrendPercentage());
        int trendColor;
        String signPrefix;
        int contentDescription;

        switch (stats.getTrendDirection()) {
            case UP:
                trendColor = ContextCompat.getColor(requireContext(), R.color.status_success);
                signPrefix = "+";
                imgTrendIndicator.setRotation(0f);
                contentDescription = R.string.dashboard_trend_up;
                break;
            case DOWN:
                trendColor = ContextCompat.getColor(requireContext(), R.color.color_error);
                signPrefix = "-";
                imgTrendIndicator.setRotation(180f);
                contentDescription = R.string.dashboard_trend_down;
                break;
            case STABLE:
            default:
                trendColor = ContextCompat.getColor(requireContext(), R.color.status_muted);
                signPrefix = "";
                imgTrendIndicator.setRotation(90f);
                contentDescription = R.string.dashboard_trend_stable;
                break;
        }

        imgTrendIndicator.setColorFilter(trendColor);
        imgTrendIndicator.setContentDescription(getString(contentDescription));
        txtSmsTrend.setTextColor(trendColor);
        txtSmsTrend.setText(getString(R.string.dashboard_trend_value_format, signPrefix, absTrend));
    }

    private void applyDashboardState(@Nullable DashboardViewModel.DashboardState state, @Nullable String message) {
        if (state == null) {
            return;
        }

        switch (state) {
            case CONTENT:
                updateActivityStateVisibility();
                break;
            case EMPTY:
                txtNoActivity.setText(message != null ? message : getString(R.string.dashboard_state_empty));
                txtNoActivity.setVisibility(View.VISIBLE);
                recyclerActivity.setVisibility(View.GONE);
                break;
            case PERMISSION_REQUIRED:
                txtNoActivity.setText(message != null ? message : getString(R.string.dashboard_state_permission_required));
                txtNoActivity.setVisibility(View.VISIBLE);
                recyclerActivity.setVisibility(View.GONE);
                break;
            case ERROR:
                txtNoActivity.setText(message != null ? message : getString(R.string.dashboard_state_error));
                txtNoActivity.setVisibility(View.VISIBLE);
                recyclerActivity.setVisibility(View.GONE);
                break;
        }
    }

    private void updateActivityStateVisibility() {
        if (activityList.isEmpty()) {
            txtNoActivity.setText(R.string.dashboard_no_activity);
            txtNoActivity.setVisibility(View.VISIBLE);
            recyclerActivity.setVisibility(View.GONE);
        } else {
            txtNoActivity.setVisibility(View.GONE);
            recyclerActivity.setVisibility(View.VISIBLE);
        }
    }

    private void showError(String message) {
        Snackbar.make(requireView(), message, Snackbar.LENGTH_LONG).show();
    }

    @Override
    public void onResume() {
        super.onResume();
        long now = System.currentTimeMillis();
        if (now - lastRefreshMs >= AppConfig.Polling.DASHBOARD_REFRESH_THROTTLE_MS) {
            lastRefreshMs = now;
            refreshDashboard();
        }
        liveHandler.removeCallbacks(liveRefresh);
        liveHandler.postDelayed(liveRefresh, 30_000L);
    }

    @Override
    public void onPause() {
        liveHandler.removeCallbacks(liveRefresh);
        super.onPause();
    }

    private void animateText(TextView target, String value) {
        if (target == null) return;
        target.animate().alpha(0.35f).setDuration(110L).withEndAction(() -> {
            target.setText(value);
            target.animate().alpha(1f).setDuration(180L).start();
        }).start();
    }


    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // Null all view references so the GC can collect the View tree while
        // this Fragment instance stays on the back stack.
        swipeRefresh   = null;
        txtSmsSent     = null;
        txtDeliveryRate = null;
        txtDeliveryStatus = null;
        txtFailed      = null;
        txtQueued      = null;
        txtDashboardUpdated = null;
        imgTrendIndicator = null;
        actionBulkSms  = null;
        actionSingleSms = null;
        actionInbox    = null;
        actionSettings = null;
        actionCampaignHistory = null;
        // Release the adapter's RecyclerView reference.
        if (recyclerActivity != null) {
            recyclerActivity.setAdapter(null);
        }
        recyclerActivity = null;
        activityAdapter  = null;
        // viewModel survives rotation — do not null it here.
    }
        // ==================== INNER CLASSES ====================

    private static class ActivityAdapter extends RecyclerView.Adapter<ActivityAdapter.ViewHolder> {
        private final List<SmsModel> items;

        public ActivityAdapter(List<SmsModel> items) {
            this.items = items;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_activity, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            SmsModel item = items.get(position);
            holder.txtPhone.setText(item.getAddress());
            holder.txtBody.setText(item.getBody());
            holder.txtTime.setText(item.getDate());
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView txtPhone, txtBody, txtTime;

            ViewHolder(View itemView) {
                super(itemView);
                txtPhone = itemView.findViewById(R.id.text_sender);
                txtBody = itemView.findViewById(R.id.text_preview);
                txtTime = itemView.findViewById(R.id.text_time);
            }
        }
    }
}

