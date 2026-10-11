package com.afriserve.smsmanager.ui.campaign;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.text.TextUtils;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;

import com.afriserve.smsmanager.AppDatabase;
import com.afriserve.smsmanager.data.entity.CampaignEntity;
import com.afriserve.smsmanager.data.entity.SmsEntity;
import com.afriserve.smsmanager.data.export.CampaignReportExporter;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;

/** Premium campaign dashboard with campaign-scoped search, filters and reporting. */
public class CampaignAnalyticsFragment extends Fragment {
    private LinearLayout content, recipientList, messageBody;
    private EditText search;
    private List<SmsEntity> allRows = new ArrayList<>();
    private CampaignEntity campaign;
    private long campaignId;
    private String statusFilter = "ALL";
    private final Handler refreshHandler = new Handler(Looper.getMainLooper());
    private final Runnable refreshTask = this::load;

    public CampaignAnalyticsFragment() { super(); }

    @Nullable @Override public View onCreateView(@NonNull android.view.LayoutInflater inflater, @Nullable android.view.ViewGroup container, @Nullable Bundle state) {
        campaignId = requireArguments().getLong("campaignId", -1);
        ScrollView scroll = new ScrollView(requireContext());
        content = new LinearLayout(requireContext()); content.setOrientation(LinearLayout.VERTICAL); content.setPadding(dp(16), dp(16), dp(16), dp(112));
        scroll.addView(content); load(); return scroll;
    }

    @Override public void onResume() { super.onResume(); refreshHandler.postDelayed(refreshTask, 5000); }
    @Override public void onPause() { refreshHandler.removeCallbacks(refreshTask); super.onPause(); }

    private void load() {
        Executors.newSingleThreadExecutor().execute(() -> {
            AppDatabase db = AppDatabase.getInstance(requireContext());
            CampaignEntity loaded = db.campaignDao().getCampaignById(campaignId).blockingGet();
            List<SmsEntity> rows = db.smsDao().getSmsByCampaign(campaignId).blockingGet();
            if (!isAdded()) return;
            requireActivity().runOnUiThread(() -> { campaign = loaded; allRows = rows == null ? new ArrayList<>() : rows; render(); });
        });
    }

    private void render() {
        content.removeAllViews();
        content.addView(header());
        content.addView(label("DELIVERY SNAPSHOT", 12, Color.LTGRAY), marginParams(2, 16));
        content.addView(kpis(), marginParams(0, 6));
        content.addView(messageCard(), marginParams(0, 14));

        LinearLayout actions = row();
        MaterialButton export = action("Export report"); export.setOnClickListener(v -> export());
        MaterialButton refresh = action("Refresh status"); refresh.setOnClickListener(v -> load());
        actions.addView(export, weightParams(1)); actions.addView(refresh, weightParams(1));
        content.addView(actions, marginParams(0, 14));

        search = new EditText(requireContext()); search.setHint("Search customer, phone, or officer"); search.setTextSize(14); search.setSingleLine(true); search.setPadding(dp(16), 0, dp(16), 0);
        search.addTextChangedListener(new TextWatcher() { public void beforeTextChanged(CharSequence s, int a, int b, int c) {} public void onTextChanged(CharSequence s, int a, int b, int c) { renderRecipients(); } public void afterTextChanged(Editable e) {} });
        content.addView(search, marginParams(0, 4));

        ChipGroup chips = new ChipGroup(requireContext()); chips.setSingleLine(true);
        for (String filter : new String[]{"ALL", "DELIVERED", "SENT", "PENDING", "FAILED"}) {
            Chip chip = new Chip(requireContext()); chip.setText(filter); chip.setCheckable(true); chip.setChecked(filter.equals(statusFilter));
            chip.setOnClickListener(v -> { statusFilter = filter; renderRecipients(); for (int i = 0; i < chips.getChildCount(); i++) ((Chip) chips.getChildAt(i)).setChecked(((Chip) chips.getChildAt(i)).getText().toString().equals(statusFilter)); }); chips.addView(chip);
        }
        android.widget.HorizontalScrollView chipScroll = new android.widget.HorizontalScrollView(requireContext());
        chipScroll.setHorizontalScrollBarEnabled(false);
        chipScroll.addView(chips, new android.widget.HorizontalScrollView.LayoutParams(-2, -2));
        content.addView(chipScroll, marginParams(0, 8));
        TextView heading = label("Recipient delivery and follow-up", 19, Color.WHITE); content.addView(heading, marginParams(0, 12));
        recipientList = new LinearLayout(requireContext()); recipientList.setOrientation(LinearLayout.VERTICAL); content.addView(recipientList); renderRecipients();
    }

    private View header() {
        MaterialCardView card = new MaterialCardView(requireContext()); card.setRadius(dp(22)); card.setCardBackgroundColor(Color.rgb(61, 42, 99)); card.setContentPadding(dp(20), dp(18), dp(20), dp(18));
        LinearLayout box = column();
        TextView eyebrow = label("CAMPAIGN DETAILS", 12, Color.rgb(224, 210, 255)); eyebrow.setTypeface(Typeface.DEFAULT, Typeface.BOLD); box.addView(eyebrow);
        TextView title = label(campaign == null || campaign.name == null ? "Campaign details" : campaign.name, 25, Color.WHITE); title.setTypeface(Typeface.DEFAULT, Typeface.BOLD); box.addView(title, marginParams(0, 3));
        TextView meta = label("Delivery performance and customer follow-up\n" + (campaign == null ? "" : "ID: " + campaignId + "  •  " + text(campaign.campaignDate) + "  •  " + text(campaign.status)), 13, Color.WHITE); box.addView(meta);
        card.addView(box); return card;
    }

    private View kpis() {
        int total = allRows.size(), sent = 0, delivered = 0, failed = 0, pending = 0;
        for (SmsEntity r : allRows) { if ("DELIVERED".equalsIgnoreCase(r.status)) { delivered++; sent++; } else if ("SENT".equalsIgnoreCase(r.status)) sent++; else if ("FAILED".equalsIgnoreCase(r.status)) failed++; else pending++; }
        LinearLayout box = column(); box.addView(kpiRow(new String[]{"RECIPIENTS", String.valueOf(total)}, new String[]{"SENT", String.valueOf(sent)}));
        box.addView(kpiRow(new String[]{"DELIVERED", String.valueOf(delivered)}, new String[]{"FAILED", String.valueOf(failed)}), marginParams(0, 8));
        box.addView(kpiRow(new String[]{"PENDING", String.valueOf(pending)}, new String[]{"DELIVERY RATE", String.format(java.util.Locale.US, "%.2f%%", total == 0 ? 0 : delivered * 100d / total)}), marginParams(0, 8)); return box;
    }

    private LinearLayout kpiRow(String[] left, String[] right) { LinearLayout row = row(); row.addView(kpi(left[0], left[1]), weightParams(1)); row.addView(kpi(right[0], right[1]), weightParams(1)); return row; }
    private View kpi(String caption, String value) { MaterialCardView card = new MaterialCardView(requireContext()); card.setRadius(dp(16)); card.setCardElevation(dp(1)); card.setContentPadding(dp(14), dp(10), dp(14), dp(10)); LinearLayout box = column(); box.addView(label(caption, 11, Color.LTGRAY)); TextView v = label(value, 23, Color.rgb(170, 195, 255)); v.setTypeface(Typeface.DEFAULT, Typeface.BOLD); box.addView(v); card.addView(box); return card; }

    private View messageCard() {
        MaterialCardView card = new MaterialCardView(requireContext()); card.setRadius(dp(16)); card.setContentPadding(dp(16), dp(12), dp(16), dp(12)); LinearLayout box = column();
        LinearLayout top = row(); TextView title = label("Campaign message", 17, Color.rgb(35, 49, 70)); top.addView(title, weightParams(1)); Button toggle = new Button(requireContext()); toggle.setText("View message"); top.addView(toggle); box.addView(top);
        messageBody = column(); messageBody.setVisibility(View.GONE); TextView body = label(campaign == null ? "" : text(campaign.messageTemplate), 14, Color.DKGRAY); body.setPadding(0, dp(12), 0, 0); messageBody.addView(body); box.addView(messageBody); toggle.setOnClickListener(v -> { boolean show = messageBody.getVisibility() != View.VISIBLE; messageBody.setVisibility(show ? View.VISIBLE : View.GONE); toggle.setText(show ? "Hide message" : "View message"); }); card.addView(box); return card;
    }

    private void renderRecipients() {
        if (recipientList == null) return; recipientList.removeAllViews(); String query = search == null ? "" : search.getText().toString().toLowerCase();
        for (SmsEntity r : allRows) { String status = text(r.status).toUpperCase(); String hay = (text(r.customerName) + " " + text(r.phoneNumber) + " " + text(r.officerName)).toLowerCase(); if (!"ALL".equals(statusFilter) && !statusFilter.equals(status)) continue; if (!query.isEmpty() && !hay.contains(query)) continue; recipientList.addView(recipient(r), marginParams(0, 7)); }
        if (recipientList.getChildCount() == 0) recipientList.addView(label("No matching recipients", 14, Color.GRAY));
    }

    private View recipient(SmsEntity r) {
        MaterialCardView card = new MaterialCardView(requireContext()); card.setRadius(dp(14)); card.setContentPadding(dp(14), dp(10), dp(14), dp(10)); LinearLayout box = column();
        TextView name = label(text(r.customerName).isEmpty() ? "Unknown customer" : r.customerName, 16, Color.WHITE); name.setEllipsize(TextUtils.TruncateAt.END); name.setMaxLines(1); name.setTypeface(Typeface.DEFAULT, Typeface.BOLD); box.addView(name);
        box.addView(label(text(r.phoneNumber) + (text(r.officerName).isEmpty() ? "" : "\nOfficer: " + r.officerName), 13, Color.LTGRAY));
        TextView status = label(text(r.status).toUpperCase(), 12, "DELIVERED".equalsIgnoreCase(r.status) ? Color.rgb(20, 125, 75) : Color.rgb(190, 105, 20)); status.setTypeface(Typeface.DEFAULT, Typeface.BOLD); box.addView(status); card.addView(box); return card;
    }

    private void export() { Executors.newSingleThreadExecutor().execute(() -> { try { File file = CampaignReportExporter.export(requireContext(), campaignId); if (isAdded()) requireActivity().runOnUiThread(() -> share(file)); } catch (Exception e) { if (isAdded()) requireActivity().runOnUiThread(() -> Toast.makeText(requireContext(), "Unable to export campaign report: " + e.getMessage(), Toast.LENGTH_LONG).show()); } }); }
    private void share(File file) { Uri uri = FileProvider.getUriForFile(requireContext(), requireContext().getPackageName() + ".fileprovider", file); Intent i = new Intent(Intent.ACTION_SEND); i.setType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"); i.putExtra(Intent.EXTRA_STREAM, uri); i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION); startActivity(Intent.createChooser(i, "Share campaign report")); }
    private MaterialButton action(String text) { MaterialButton b = new MaterialButton(requireContext()); b.setText(text); b.setAllCaps(false); return b; }
    private LinearLayout row() { LinearLayout l = new LinearLayout(requireContext()); l.setOrientation(LinearLayout.HORIZONTAL); l.setGravity(Gravity.CENTER_VERTICAL); return l; }
    private LinearLayout column() { LinearLayout l = new LinearLayout(requireContext()); l.setOrientation(LinearLayout.VERTICAL); return l; }
    private TextView label(String s, float size, int color) { TextView t = new TextView(requireContext()); t.setText(s); t.setTextSize(size); t.setTextColor(color); return t; }
    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density + .5f); }
    private LinearLayout.LayoutParams marginParams(int left, int top) { LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2); p.leftMargin = dp(left); p.topMargin = dp(top); p.rightMargin = dp(left); return p; }
    private LinearLayout.LayoutParams weightParams(float weight) { LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -2, weight); p.rightMargin = dp(6); return p; }
    private String text(String s) { return s == null ? "" : s; }
}
