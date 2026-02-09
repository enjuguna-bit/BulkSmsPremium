package com.bulksms.smsmanager.databinding;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.appcompat.widget.SearchView;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

public class FragmentInboxBindingManual {
    
    public final SwipeRefreshLayout swipeRefresh;
    public final SearchView searchView;
    public final ChipGroup chipGroupFilters;
    public final Chip chipAll;
    public final Chip chipInbox;
    public final Chip chipSent;
    public final Chip chipUnread;
    public final TextView tvTotalCount;
    public final TextView tvUnreadCount;
    public final RecyclerView recyclerView;
    public final ProgressBar progressBar;
    public final View emptyView;
    
    private FragmentInboxBindingManual(SwipeRefreshLayout swipeRefresh, ProgressBar progressBar, SearchView searchView, 
                                ChipGroup chipGroupFilters, Chip chipAll, Chip chipInbox, 
                                Chip chipSent, Chip chipUnread, TextView tvTotalCount, 
                                TextView tvUnreadCount, RecyclerView recyclerView, 
                                ProgressBar progressBarMain, View emptyView) {
        this.swipeRefresh = swipeRefresh;
        this.searchView = searchView;
        this.chipGroupFilters = chipGroupFilters;
        this.chipAll = chipAll;
        this.chipInbox = chipInbox;
        this.chipSent = chipSent;
        this.chipUnread = chipUnread;
        this.tvTotalCount = tvTotalCount;
        this.tvUnreadCount = tvUnreadCount;
        this.recyclerView = recyclerView;
        this.progressBar = progressBarMain;
        this.emptyView = emptyView;
    }
    
    public static FragmentInboxBindingManual inflate(LayoutInflater inflater, ViewGroup parent, boolean attachToParent) {
        View root = inflater.inflate(com.bulksms.smsmanager.R.layout.fragment_inbox, parent, false);
        if (attachToParent) {
            parent.addView(root);
        }
        
        SwipeRefreshLayout swipeRefresh = root.findViewById(com.bulksms.smsmanager.R.id.swipeRefresh);
        SearchView searchView = root.findViewById(com.bulksms.smsmanager.R.id.searchView);
        ChipGroup chipGroupFilters = root.findViewById(com.bulksms.smsmanager.R.id.chipGroupFilters);
        Chip chipAll = root.findViewById(com.bulksms.smsmanager.R.id.chipAll);
        Chip chipInbox = root.findViewById(com.bulksms.smsmanager.R.id.chipInbox);
        Chip chipSent = root.findViewById(com.bulksms.smsmanager.R.id.chipSent);
        Chip chipUnread = root.findViewById(com.bulksms.smsmanager.R.id.chipUnread);
        TextView tvTotalCount = root.findViewById(com.bulksms.smsmanager.R.id.tvTotalCount);
        TextView tvUnreadCount = root.findViewById(com.bulksms.smsmanager.R.id.tvUnreadCount);
        RecyclerView recyclerView = root.findViewById(com.bulksms.smsmanager.R.id.recyclerView);
        ProgressBar progressBar = root.findViewById(com.bulksms.smsmanager.R.id.progressBar);
        View emptyView = root.findViewById(com.bulksms.smsmanager.R.id.emptyView);
        
        return new FragmentInboxBindingManual(swipeRefresh, progressBar, searchView, chipGroupFilters, chipAll, 
                                      chipInbox, chipSent, chipUnread, tvTotalCount, tvUnreadCount, 
                                      recyclerView, progressBar, emptyView);
    }
    
    public View getRoot() {
        return swipeRefresh;
    }
}
