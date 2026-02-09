package com.afriserve.smsmanager;

import android.database.Cursor;
import android.os.Bundle;
import android.provider.Telephony;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;
import java.util.Locale;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.lifecycle.ViewModel;

import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.NavOptions;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.afriserve.smsmanager.models.SmsModel;
import com.afriserve.smsmanager.ui.dashboard.DashboardViewModel;
import com.google.android.material.snackbar.Snackbar;

import dagger.hilt.android.AndroidEntryPoint;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
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
        // Use Activity's default factory (Hilt provides it for Activities) as a workaround
        // when Fragment's default factory isn't properly overridden in some release builds.
        viewModel = new ViewModelProvider(this, requireActivity().getDefaultViewModelProviderFactory()).get(DashboardViewModel.class);
        initViews(view);
        setupQuickActions();
        setupActivityList();
        setupObservers();
        loadInitialData();

        swipeRefresh.setOnRefreshListener(() -> viewModel.loadDashboardData());
    }

    private void setupObservers() {
        viewModel.getDashboardStats().observe(getViewLifecycleOwner(), this::updateUI);
        viewModel.getIsLoading().observe(getViewLifecycleOwner(), isLoading -> {
            swipeRefresh.setRefreshing(isLoading);
        });
        viewModel.getError().observe(getViewLifecycleOwner(), error -> {
            if (error != null) {
                showError(error);
            }
        });
    }

    private void updateUI(com.afriserve.smsmanager.ui.dashboard.DashboardStats stats) {
        if (stats == null) return;

        // Update SMS stats
        txtSmsSent.setText(String.valueOf(stats.getSmsStats().getTotalSent()));
        txtDeliveryRate.setText(String.format("%.1f%%", stats.getSmsStats().getDeliveryRate()));
        txtDeliveryStatus.setText(stats.getSmsStats().getDeliveryStatus());
        txtFailed.setText(String.valueOf(stats.getSmsStats().getTotalFailed()));
        txtQueued.setText(String.valueOf(stats.getSmsStats().getTotalQueued()));
        txtSmsTrend.setText("Trend: " + stats.getSmsStats().getTrend());

        // Update activity list
        activityList.clear();
        if (stats.getRecentActivity() != null) {
            activityList.addAll(stats.getRecentActivity());
        }
        activityAdapter.notifyDataSetChanged();
        txtNoActivity.setVisibility(activityList.isEmpty() ? View.VISIBLE : View.GONE);
        recyclerActivity.setVisibility(activityList.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void loadInitialData() {
        viewModel.loadDashboardData();
        viewModel.loadRecentActivity();
    }

    private void initViews(View view) {
        swipeRefresh = view.findViewById(R.id.swipeRefresh);
        txtSmsSent = view.findViewById(R.id.txtSmsSent);
        txtDeliveryRate = view.findViewById(R.id.txtDeliveryRate);
        txtDeliveryStatus = view.findViewById(R.id.txtDeliveryStatus);
        txtFailed = view.findViewById(R.id.txtFailed);
        txtQueued = view.findViewById(R.id.txtQueued);
        txtSmsTrend = view.findViewById(R.id.txtSmsTrend);
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
            NavController navController = Navigation.findNavController(requireActivity(), R.id.nav_host_fragment_container);
            if (navController.getCurrentDestination() != null
                    && navController.getCurrentDestination().getId() == destination) {
                return;
            }
            NavOptions options = new NavOptions.Builder()
                    .setLaunchSingleTop(true)
                    .build();
            navController.navigate(destination, null, options);
        } catch (Exception e) {
            Toast.makeText(requireContext(), "Navigation error", Toast.LENGTH_SHORT).show();
        }
    }

    private void setupActivityList() {
        activityAdapter = new ActivityAdapter(activityList);
        recyclerActivity.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerActivity.setAdapter(activityAdapter);
    }

    private void updateStatsDisplay(com.afriserve.smsmanager.ui.dashboard.DashboardStats stats) {
        if (stats == null) return;

        // Update SMS stats
        txtSmsSent.setText(String.valueOf(stats.getSmsStats().getTotalSent()));
        txtDeliveryRate.setText(String.format(Locale.getDefault(), "%.1f%%", stats.getSmsStats().getDeliveryRate()));
        txtDeliveryStatus.setText(stats.getSmsStats().getDeliveryStatus());
        txtFailed.setText(String.valueOf(stats.getSmsStats().getTotalFailed()));
        txtQueued.setText(String.valueOf(stats.getSmsStats().getTotalQueued()));
        txtSmsTrend.setText("SMS Trend: " + stats.getSmsStats().getTrend());

        // Update activity list if available
        if (stats.getRecentActivity() != null) {
            activityList.clear();
            activityList.addAll(stats.getRecentActivity());
        }
        activityAdapter.notifyDataSetChanged();
        txtNoActivity.setVisibility(activityList.isEmpty() ? View.VISIBLE : View.GONE);
        recyclerActivity.setVisibility(activityList.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void showError(String message) {
        Snackbar.make(requireView(), message, Snackbar.LENGTH_LONG).show();
    }

    @Override
    public void onResume() {
        super.onResume();
        viewModel.loadDashboardData();
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
