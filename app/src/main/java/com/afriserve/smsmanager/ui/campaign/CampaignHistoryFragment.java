package com.afriserve.smsmanager.ui.campaign;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.text.TextUtils;
import android.os.Handler;
import android.os.Looper;
import android.net.Uri;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;
import androidx.core.content.FileProvider;

import com.afriserve.smsmanager.AppDatabase;
import com.afriserve.smsmanager.R;
import com.afriserve.smsmanager.data.entity.CampaignEntity;
import com.afriserve.smsmanager.data.export.CampaignReportExporter;
import com.afriserve.smsmanager.data.export.CampaignSmsRecord;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

/** Premium campaign history landing page. */
public class CampaignHistoryFragment extends Fragment {
    private LinearLayout content, campaignList;
    private EditText search;
    private List<CampaignEntity> campaigns = new ArrayList<>();
    private List<CampaignSmsRecord> searchResults = new ArrayList<>();
    private String statusFilter = "ALL";
    private ExecutorService ioExecutor;
    private final Handler searchHandler = new Handler(Looper.getMainLooper());
    private Runnable pendingSearch;
    private Future<?> activeSearch;
    private int searchGeneration;

    public CampaignHistoryFragment() { super(); }

    @Nullable @Override public View onCreateView(@NonNull android.view.LayoutInflater inflater,
            @Nullable android.view.ViewGroup container, @Nullable Bundle state) {
        ioExecutor = Executors.newSingleThreadExecutor();
        ScrollView scroll = new ScrollView(requireContext());
        content = column();
        // Leave room for the persistent bottom navigation while scrolling.
        content.setPadding(dp(16), dp(16), dp(16), dp(112));
        scroll.addView(content); load(); return scroll;
    }

    private void load() {
        android.content.Context context = requireContext().getApplicationContext();
        ioExecutor.execute(() -> {
            List<CampaignEntity> loaded = AppDatabase.getInstance(context).campaignDao().getRecentCampaigns(200).blockingGet();
            if (!isAdded()) return;
            requireActivity().runOnUiThread(() -> { campaigns = loaded == null ? new ArrayList<>() : loaded; render(); });
        });
    }

    private void render() {
        content.removeAllViews();
        content.addView(hero()); content.addView(summary(), margins(0, 14));
        MaterialButton followUp = new MaterialButton(requireContext());
        followUp.setText("Export undelivered follow-up list");
        followUp.setAllCaps(false);
        followUp.setOnClickListener(v -> downloadFollowUpReport());
        content.addView(followUp, margins(0, 14));

        search = new EditText(requireContext());
        search.setHint("Search campaigns, customers, or phone");
        search.setTextSize(14);
        search.setSingleLine(true);
        search.setPadding(dp(16), 0, dp(16), 0);
        search.addTextChangedListener(new android.text.TextWatcher() { public void beforeTextChanged(CharSequence s,int a,int b,int c){} public void onTextChanged(CharSequence s,int a,int b,int c){scheduleSearch();} public void afterTextChanged(android.text.Editable e){} });
        content.addView(search, margins(0, 14));
        ChipGroup filters = new ChipGroup(requireContext()); filters.setSingleLine(true);
        for (String filter : new String[]{"ALL", "ACTIVE", "COMPLETED", "PAUSED", "CLOSED"}) {
            Chip chip = new Chip(requireContext()); chip.setText(filter); chip.setCheckable(true); chip.setChecked(filter.equals(statusFilter));
            chip.setOnClickListener(v -> { statusFilter = filter; for (int i=0;i<filters.getChildCount();i++) { Chip item=(Chip)filters.getChildAt(i); item.setChecked(item.getText().toString().equals(statusFilter)); } scheduleSearch(); });
            filters.addView(chip);
        }
        android.widget.HorizontalScrollView filterScroll = new android.widget.HorizontalScrollView(requireContext());
        filterScroll.setHorizontalScrollBarEnabled(false);
        filterScroll.addView(filters, new android.widget.HorizontalScrollView.LayoutParams(-2, -2));
        content.addView(filterScroll, margins(0, 8));
        TextView heading = text("Recent campaigns", 19, Color.WHITE); heading.setTypeface(Typeface.DEFAULT, Typeface.BOLD); content.addView(heading, margins(0,14));
        campaignList = column(); content.addView(campaignList); renderCampaigns();
    }

    private View hero() {
        MaterialCardView card = new MaterialCardView(requireContext()); card.setRadius(dp(22)); card.setCardBackgroundColor(Color.rgb(24,65,105)); card.setContentPadding(dp(20),dp(20),dp(20),dp(20));
        LinearLayout box = column(); TextView eyebrow = text("CAMPAIGN LIBRARY",12,Color.rgb(190,220,247)); eyebrow.setTypeface(Typeface.DEFAULT,Typeface.BOLD); box.addView(eyebrow);
        TextView title = text("Campaign history",27,Color.WHITE); title.setTypeface(Typeface.DEFAULT,Typeface.BOLD); box.addView(title,margins(0,3));
        box.addView(text("Find a campaign quickly, check its status, or open its delivery report.",14,Color.WHITE),margins(0,5)); card.addView(box); return card;
    }

    private View summary() {
        int active=0, recipients=0, delivered=0; for (CampaignEntity c:campaigns) { if (CampaignEntity.STATUS_ACTIVE.equalsIgnoreCase(c.status)) active++; recipients+=c.recipientCount; delivered+=c.deliveredCount; }
        LinearLayout rows=column(); rows.addView(statRow(new String[]{"CAMPAIGNS",String.valueOf(campaigns.size())},new String[]{"ACTIVE",String.valueOf(active)})); rows.addView(statRow(new String[]{"RECIPIENTS",String.valueOf(recipients)},new String[]{"DELIVERED",String.valueOf(delivered)}),margins(0,8)); return rows;
    }
    private LinearLayout statRow(String[] left,String[] right) { LinearLayout row=row(); row.addView(statCard(left[0],left[1]),weight(1)); row.addView(statCard(right[0],right[1]),weight(1)); return row; }
    private View statCard(String caption,String value) { MaterialCardView card=new MaterialCardView(requireContext()); card.setRadius(dp(16)); card.setCardElevation(dp(1)); card.setContentPadding(dp(14),dp(11),dp(14),dp(11)); LinearLayout box=column(); box.addView(text(caption,11,Color.LTGRAY)); TextView v=text(value,23,Color.rgb(170,195,255)); v.setTypeface(Typeface.DEFAULT,Typeface.BOLD); box.addView(v,margins(0,2)); card.addView(box); return card; }

    private void renderCampaigns() {
        if (campaignList==null) return; campaignList.removeAllViews(); String query=search==null?"":search.getText().toString().trim().toLowerCase(Locale.ROOT);
        if (!query.isEmpty()) {
            scheduleSearch();
            return;
        }
        for (CampaignEntity c:campaigns) { String status=safe(c.status).toUpperCase(Locale.ROOT); String hay=(safe(c.name)+" "+safe(c.sourceFileName)+" "+safe(c.campaignDate)).toLowerCase(Locale.ROOT); if (!"ALL".equals(statusFilter)&&!statusFilter.equals(status)) continue; if (!query.isEmpty()&&!hay.contains(query)) continue; campaignList.addView(campaignCard(c),margins(0,8)); }
        if (campaignList.getChildCount()==0) { TextView empty=text(campaigns.isEmpty()?"No campaigns yet. Your completed campaigns will appear here.":"No campaigns match your search.",14,Color.LTGRAY); empty.setGravity(Gravity.CENTER); empty.setPadding(dp(12),dp(28),dp(12),dp(28)); campaignList.addView(empty); }
    }

    /** Debounce phone/name searches so SQLite is not queried for every keystroke. */
    private void scheduleSearch() {
        if (campaignList == null || search == null || ioExecutor == null) return;
        if (pendingSearch != null) searchHandler.removeCallbacks(pendingSearch);
        if (activeSearch != null) activeSearch.cancel(true);

        String query = search.getText().toString().trim().toLowerCase(Locale.ROOT);
        int generation = ++searchGeneration;
        if (query.isEmpty()) {
            renderCampaigns();
            return;
        }

        campaignList.removeAllViews();
        campaignList.addView(text("Searching campaign history…", 14, Color.LTGRAY), margins(0, 12));
        pendingSearch = () -> {
            activeSearch = ioExecutor.submit(() -> {
                List<CampaignSmsRecord> loaded = AppDatabase.getInstance(requireContext().getApplicationContext())
                        .smsDao().searchCampaignHistory(query).blockingGet();
                if (!isAdded() || generation != searchGeneration) return;
                requireActivity().runOnUiThread(() -> {
                    if (generation != searchGeneration) return;
                    searchResults = loaded == null ? new ArrayList<>() : loaded;
                    renderSearchResults();
                });
            });
        };
        searchHandler.postDelayed(pendingSearch, 350);
    }

    private void renderSearchResults() {
        if (campaignList == null) return;
        campaignList.removeAllViews();
        TextView heading = text(searchResults.size() + " matching SMS records across all campaigns", 15, Color.WHITE);
        heading.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        campaignList.addView(heading, margins(0, 2));
        for (CampaignSmsRecord record : searchResults) campaignList.addView(searchResultCard(record), margins(0, 8));
        if (searchResults.isEmpty()) campaignList.addView(text("No customer history found for that name or phone number.",14,Color.LTGRAY),margins(0,12));
    }

    private View searchResultCard(CampaignSmsRecord record) {
        MaterialCardView card = new MaterialCardView(requireContext());
        card.setRadius(dp(16)); card.setContentPadding(dp(15),dp(12),dp(15),dp(12));
        LinearLayout box = column();
        TextView name = text(safe(record.customerName).isEmpty() ? "Unknown customer" : record.customerName, 16, Color.WHITE); name.setTypeface(Typeface.DEFAULT,Typeface.BOLD); box.addView(name);
        box.addView(text(safe(record.phoneNumber) + "  •  " + safe(record.campaignName) + "  •  " + safe(record.campaignDate),13,Color.LTGRAY),margins(0,5));
        String amount = record.amount == null ? "" : String.format(Locale.US,"Amount: %.2f  •  ",record.amount);
        String officer = safe(record.officerName).isEmpty() ? "Unassigned" : record.officerName;
        box.addView(text(amount + "Officer/RO: " + officer,13,Color.LTGRAY),margins(0,4));
        TextView status = text(safe(record.smsStatus).toUpperCase(Locale.ROOT),12, "DELIVERED".equalsIgnoreCase(record.smsStatus) ? Color.rgb(20,125,75) : Color.rgb(190,75,45)); status.setTypeface(Typeface.DEFAULT,Typeface.BOLD); box.addView(status,margins(0,4));
        box.addView(text(safe(record.message),12,Color.LTGRAY),margins(0,3));
        card.addView(box);
        card.setOnClickListener(v -> { Bundle args=new Bundle(); args.putLong("campaignId",record.campaignId); Navigation.findNavController(v).navigate(R.id.nav_campaign_analytics,args); });
        return card;
    }

    private void downloadFollowUpReport() {
        ioExecutor.execute(() -> {
            try {
                java.io.File file = CampaignReportExporter.exportUndeliveredFollowUp(requireContext().getApplicationContext());
                if (isAdded()) requireActivity().runOnUiThread(() -> shareReport(file));
            } catch (Exception error) {
                if (isAdded()) requireActivity().runOnUiThread(() -> Toast.makeText(requireContext(), "Unable to create follow-up report: " + error.getMessage(), Toast.LENGTH_LONG).show());
            }
        });
    }

    private void shareReport(java.io.File file) {
        Uri uri = FileProvider.getUriForFile(requireContext(), requireContext().getPackageName() + ".fileprovider", file);
        Intent intent = new Intent(Intent.ACTION_SEND); intent.setType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"); intent.putExtra(Intent.EXTRA_STREAM, uri); intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(intent, "Share SMS follow-up report"));
    }

    private View campaignCard(CampaignEntity c) {
        MaterialCardView card=new MaterialCardView(requireContext()); card.setRadius(dp(17)); card.setClickable(true); card.setFocusable(true); card.setContentPadding(dp(16),dp(14),dp(16),dp(14)); LinearLayout box=column();
        LinearLayout titleRow=row(); TextView title=text(safe(c.name).isEmpty()?"Unnamed campaign":c.name,17,Color.WHITE); title.setEllipsize(TextUtils.TruncateAt.END); title.setMaxLines(1); title.setTypeface(Typeface.DEFAULT,Typeface.BOLD); titleRow.addView(title,weight(1)); TextView status=text(safe(c.status).toUpperCase(Locale.ROOT),11,statusColor(c.status)); status.setTypeface(Typeface.DEFAULT,Typeface.BOLD); titleRow.addView(status); box.addView(titleRow);
        String date=safe(c.campaignDate).isEmpty()?"Date not recorded":c.campaignDate; String source=safe(c.sourceFileName).isEmpty()?"Manual recipients":c.sourceFileName; TextView metadata=text(date+"  •  "+source,13,Color.LTGRAY); metadata.setEllipsize(TextUtils.TruncateAt.END); metadata.setMaxLines(1); box.addView(metadata,margins(0,6));
        int total=Math.max(0,c.recipientCount), delivered=Math.max(0,c.deliveredCount), progress=total==0?0:Math.min(100,delivered*100/total); ProgressBar bar=new ProgressBar(requireContext(),null,android.R.attr.progressBarStyleHorizontal); bar.setMax(100); bar.setProgress(progress); box.addView(bar,margins(0,7));
        box.addView(text(delivered+" delivered  •  "+c.sentCount+" sent  •  "+c.failedCount+" failed  •  "+total+" recipients",13,Color.LTGRAY),margins(0,5));
        TextView details=text("Tap to view campaign details  ›",12,Color.rgb(170,195,255)); details.setGravity(Gravity.END); box.addView(details,margins(0,7)); card.addView(box);
        card.setOnClickListener(v->{ Bundle args=new Bundle(); args.putLong("campaignId",c.id); Navigation.findNavController(v).navigate(R.id.nav_campaign_analytics,args); }); return card;
    }

    private int statusColor(String status) { if (CampaignEntity.STATUS_COMPLETED.equalsIgnoreCase(status)||CampaignEntity.STATUS_CLOSED.equalsIgnoreCase(status)) return Color.rgb(20,125,75); if (CampaignEntity.STATUS_ACTIVE.equalsIgnoreCase(status)) return Color.rgb(24,105,180); return Color.rgb(190,105,20); }
    private LinearLayout row(){LinearLayout v=new LinearLayout(requireContext());v.setOrientation(LinearLayout.HORIZONTAL);v.setGravity(Gravity.CENTER_VERTICAL);return v;}
    private LinearLayout column(){LinearLayout v=new LinearLayout(requireContext());v.setOrientation(LinearLayout.VERTICAL);return v;}
    private TextView text(String value,float size,int color){TextView v=new TextView(requireContext());v.setText(value);v.setTextSize(size);v.setTextColor(color);return v;}
    private LinearLayout.LayoutParams margins(int left,int top){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.leftMargin=dp(left);p.rightMargin=dp(left);p.topMargin=dp(top);return p;}
    private LinearLayout.LayoutParams weight(float value){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-2,value);p.rightMargin=dp(8);return p;}
    private int dp(int value){return (int)(value*getResources().getDisplayMetrics().density+.5f);}
    private String safe(String value){return value==null?"":value.trim();}

    @Override public void onDestroyView() {
        if (pendingSearch != null) searchHandler.removeCallbacks(pendingSearch);
        if (activeSearch != null) activeSearch.cancel(true);
        searchGeneration++;
        if (ioExecutor != null) {
            ioExecutor.shutdownNow();
            ioExecutor = null;
        }
        super.onDestroyView();
    }
}
