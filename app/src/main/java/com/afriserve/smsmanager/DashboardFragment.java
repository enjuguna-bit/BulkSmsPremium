package com.afriserve.smsmanager;

import android.os.Bundle;
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
                     txtQueued, txtSmsTrend, txtNoActivity;
    private ImageView imgTrendIndicator;
    private RecyclerView recyclerActivity;
    private CardView actionBulkSms, actionSingleSms, actionInbox, actionSettings;

    private ActivityAdapter activityAdapter;
    private final List<SmsModel> activityList = new ArrayList<>();
    private DashboardViewModel viewModel;

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

        txtSmsSent.setText(String.valueOf(stats.getSmsStats().getTotalSent()));
        txtDeliveryRate.setText(String.format(Locale.getDefault(), "%.1f%%", stats.getSmsStats().getDeliveryRate()));
        txtFailed.setText(String.valueOf(stats.getSmsStats().getTotalFailed()));
        txtQueued.setText(String.valueOf(stats.getSmsStats().getTotalQueued()));
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
        recyclerActivity = view.findViewById(R.id.recyclerActivity);
        actionBulkSms = view.findViewById(R.id.actionBulkSms);
        actionSingleSms = view.findViewById(R.id.actionSingleSms);
        actionInbox = view.findViewById(R.id.actionInbox);
        actionSettings = view.findViewById(R.id.actionSettings);
    }

    private void setupQuickActions() {
        actionBulkSms.setOnClickListener(v -> navigateTo(R.id.nav_bulk));
        actionSingleSms.setOnClickListener(v -> navigateTo(R.id.nav_send));
        actionInbox.setOnClickListener(v -> navigateTo(R.id.nav_inbox));
        actionSettings.setOnClickListener(v -> navigateTo(R.id.nav_settings));
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
        refreshDashboard();
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
