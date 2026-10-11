package com.afriserve.smsmanager.data.export;

import android.content.Context;
import android.os.Environment;
import android.util.Log;

import com.afriserve.smsmanager.AppDatabase;
import com.afriserve.smsmanager.data.entity.CampaignEntity;
import com.afriserve.smsmanager.data.entity.SmsEntity;
import com.afriserve.smsmanager.data.export.CampaignSmsRecord;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Android-safe campaign XLSX exporter. POI remains used by Excel import only. */
public final class CampaignReportExporter {
    private static final String TAG = "CampaignReportExporter";
    private CampaignReportExporter() {}

    public static File export(Context context, long campaignId) throws Exception {
        AppDatabase db = AppDatabase.getInstance(context);
        CampaignEntity campaign = db.campaignDao().getCampaignById(campaignId).blockingGet();
        if (campaign == null) throw new IllegalArgumentException("Campaign not found: " + campaignId);
        List<SmsEntity> rows = db.smsDao().getSmsByCampaign(campaignId).blockingGet();
        if (rows == null) rows = new ArrayList<>();

        File root = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);
        if (root == null) root = new File(context.getFilesDir(), "Documents");
        String date = campaign.campaignDate == null ? "undated" : campaign.campaignDate;
        File folder = new File(root, "Campaign Feedback" + File.separator + date);
        if (!folder.exists() && !folder.mkdirs() && !folder.isDirectory()) throw new IllegalStateException("Unable to create report folder");
        String name = (campaign.name == null ? "campaign" : campaign.name).replaceAll("[^a-zA-Z0-9._-]+", "_");
        String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        File output = new File(folder, name + "_" + date + "_" + stamp + ".xlsx");

        int sent = 0, delivered = 0, failed = 0, pending = 0;
        for (SmsEntity row : rows) {
            if ("DELIVERED".equalsIgnoreCase(row.status)) { delivered++; sent++; }
            else if ("SENT".equalsIgnoreCase(row.status)) sent++;
            else if ("FAILED".equalsIgnoreCase(row.status)) failed++;
            else pending++;
        }
        int notDelivered = Math.max(0, rows.size() - delivered - failed - pending);
        double rate = rows.isEmpty() ? 0d : delivered * 100d / rows.size();
        String id = campaign.campaignUuid == null ? String.valueOf(campaign.id) : campaign.campaignUuid;

        String[][] summary = {
                {"CEMES BULK SMS CAMPAIGN REPORT", ""}, {"Campaign Name", text(campaign.name)},
                {"Campaign ID", id}, {"Campaign Date", text(campaign.campaignDate)},
                {"Campaign Status", text(campaign.status)}, {"Filter", text(campaign.arrearsFilter)},
                {"Started", dateTime(campaign.startedAt)}, {"Closed", dateTime(campaign.closedAt)}, {"", ""},
                {"Metric", "Result"}, {"Total Recipients", String.valueOf(rows.size())}, {"Sent", String.valueOf(sent)},
                {"Delivered", String.valueOf(delivered)}, {"Failed", String.valueOf(failed)}, {"Pending", String.valueOf(pending)},
                {"Not Delivered", String.valueOf(notDelivered)}, {"Delivery Rate", String.format(Locale.US, "%.2f%%", rate)},
                {"", ""}, {"Campaign Message", text(campaign.messageTemplate)}
        };

        List<String[]> customers = new ArrayList<>();
        customers.add(new String[]{"Customer Name", "Phone Number", "Officer/RO", "Amount Requested", "SMS Status", "Sent Time", "Delivered Time", "Failure Reason", "Campaign Name", "Campaign ID"});
        for (SmsEntity row : rows) customers.add(new String[]{text(row.customerName), text(row.phoneNumber), text(row.officerName),
                row.amount == null ? "" : String.format(Locale.US, "%.2f", row.amount), text(row.status), dateTime(row.sentAt), dateTime(row.deliveredAt), text(row.errorMessage), text(campaign.name), id});

        Map<String, OfficerTotals> byOfficer = new LinkedHashMap<>();
        for (SmsEntity row : rows) {
            String officer = row.officerName == null || row.officerName.trim().isEmpty() ? "Unassigned" : row.officerName.trim();
            OfficerTotals t = byOfficer.get(officer);
            if (t == null) { t = new OfficerTotals(); byOfficer.put(officer, t); }
            t.recipients++;
            if ("SENT".equalsIgnoreCase(row.status) || "DELIVERED".equalsIgnoreCase(row.status)) t.sent++;
            if ("DELIVERED".equalsIgnoreCase(row.status)) t.delivered++;
            if ("FAILED".equalsIgnoreCase(row.status)) t.failed++;
            if (row.status == null || "PENDING".equalsIgnoreCase(row.status)) t.pending++;
        }
        List<String[]> officers = new ArrayList<>();
        officers.add(new String[]{"Officer", "Recipients", "Sent", "Delivered", "Failed", "Pending", "Delivery %"});
        for (Map.Entry<String, OfficerTotals> e : byOfficer.entrySet()) {
            OfficerTotals t = e.getValue();
            officers.add(new String[]{e.getKey(), String.valueOf(t.recipients), String.valueOf(t.sent), String.valueOf(t.delivered), String.valueOf(t.failed), String.valueOf(t.pending), String.format(Locale.US, "%.2f%%", t.recipients == 0 ? 0 : t.delivered * 100d / t.recipients)});
        }

        Log.i(TAG, "Writing campaign report to " + output);
        try (FileOutputStream stream = new FileOutputStream(output)) { writeWorkbook(stream, summary, customers, officers); }
        if (!output.isFile() || output.length() == 0) throw new IllegalStateException("Report was not written");
        return output;
    }

    /** Export every campaign SMS that has not reached DELIVERED status. */
    public static File exportUndeliveredFollowUp(Context context) throws Exception {
        AppDatabase db = AppDatabase.getInstance(context);
        List<CampaignSmsRecord> rows = db.smsDao().getUndeliveredCampaignHistory().blockingGet();
        if (rows == null) rows = new ArrayList<>();

        File root = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);
        if (root == null) root = new File(context.getFilesDir(), "Documents");
        File folder = new File(root, "Campaign Feedback" + File.separator + "Global Follow Up");
        if (!folder.exists() && !folder.mkdirs() && !folder.isDirectory()) throw new IllegalStateException("Unable to create report folder");
        String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        File output = new File(folder, "undelivered_sms_follow_up_" + stamp + ".xlsx");

        String[][] summary = {
                {"SMS FOLLOW-UP REPORT", ""},
                {"Scope", "All campaigns"},
                {"Included records", String.valueOf(rows.size())},
                {"Rule", "Only records whose status is not DELIVERED"}
        };
        List<String[]> customers = new ArrayList<>();
        customers.add(new String[]{"Customer Name", "Phone Number", "Officer/RO", "Amount Requested", "Campaign Name", "Campaign Date", "SMS Message", "SMS Status", "Failure/Non-delivery Reason", "Sent Time", "Delivered Time", "Arrears Days", "Source Row"});
        for (CampaignSmsRecord row : rows) {
            customers.add(new String[]{text(row.customerName), text(row.phoneNumber), text(row.officerName),
                    row.amount == null ? "" : String.format(Locale.US, "%.2f", row.amount), text(row.campaignName),
                    text(row.campaignDate), text(row.message), text(row.smsStatus), text(row.errorMessage),
                    dateTime(row.sentAt), dateTime(row.deliveredAt), row.arrearsDays == null ? "" : String.valueOf(row.arrearsDays),
                    row.sourceRowIndex == null ? "" : String.valueOf(row.sourceRowIndex)});
        }

        Map<String, OfficerTotals> byOfficer = new LinkedHashMap<>();
        for (CampaignSmsRecord row : rows) {
            String officer = row.officerName == null || row.officerName.trim().isEmpty() ? "Unassigned" : row.officerName.trim();
            OfficerTotals totals = byOfficer.get(officer);
            if (totals == null) { totals = new OfficerTotals(); byOfficer.put(officer, totals); }
            totals.recipients++;
            if ("FAILED".equalsIgnoreCase(row.smsStatus)) totals.failed++;
            else totals.pending++;
        }
        List<String[]> officers = new ArrayList<>();
        officers.add(new String[]{"Officer/RO", "Undelivered", "Failed", "Pending", "Sent", "Delivered", "Follow-up Needed"});
        for (Map.Entry<String, OfficerTotals> entry : byOfficer.entrySet()) {
            OfficerTotals totals = entry.getValue();
            officers.add(new String[]{entry.getKey(), String.valueOf(totals.recipients), String.valueOf(totals.failed),
                    String.valueOf(totals.pending), "0", "0", "YES"});
        }

        Log.i(TAG, "Writing global undelivered follow-up report to " + output);
        try (FileOutputStream stream = new FileOutputStream(output)) { writeWorkbook(stream, summary, customers, officers); }
        if (!output.isFile() || output.length() == 0) throw new IllegalStateException("Report was not written");
        return output;
    }

    private static void writeWorkbook(FileOutputStream out, String[][] summary, List<String[]> customers, List<String[]> officers) throws Exception {
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            put(zip, "[Content_Types].xml", contentTypes()); put(zip, "_rels/.rels", rootRels());
            put(zip, "xl/workbook.xml", workbook()); put(zip, "xl/_rels/workbook.xml.rels", workbookRels()); put(zip, "xl/styles.xml", styles());
            put(zip, "xl/worksheets/sheet1.xml", sheet(summary, summary.length == 0 ? 2 : summary[0].length)); put(zip, "xl/worksheets/sheet2.xml", sheet(customers.toArray(new String[0][]), customers.isEmpty() ? 1 : customers.get(0).length)); put(zip, "xl/worksheets/sheet3.xml", sheet(officers.toArray(new String[0][]), officers.isEmpty() ? 1 : officers.get(0).length));
        }
    }

    private static String sheet(String[][] rows, int columns) {
        StringBuilder x = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?><worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetViews><sheetView workbookViewId=\"0\"><pane ySplit=\"1\" topLeftCell=\"A2\" activePane=\"bottomLeft\" state=\"frozen\"/></sheetView></sheetViews><cols>");
        for (int i = 1; i <= columns; i++) x.append("<col min=\"").append(i).append("\" max=\"").append(i).append("\" width=\"").append(i == 1 ? 24 : 18).append("\" customWidth=\"1\"/>");
        x.append("</cols><sheetData>");
        for (int r = 0; r < rows.length; r++) { x.append("<row r=\"").append(r + 1).append("\">"); for (int c = 0; c < rows[r].length; c++) x.append(cell(c + 1, r + 1, rows[r][c], r == 0 ? 1 : 0)); x.append("</row>"); }
        x.append("</sheetData>"); if (rows.length > 1 && columns > 2) x.append("<autoFilter ref=\"A1:").append(col(columns)).append(rows.length).append("\"/>"); x.append("</worksheet>"); return x.toString();
    }
    private static String cell(int c, int r, String value, int style) { return "<c r=\"" + col(c) + r + "\" s=\"" + style + "\" t=\"inlineStr\"><is><t xml:space=\"preserve\">" + xml(value) + "</t></is></c>"; }
    private static String col(int n) { StringBuilder b = new StringBuilder(); while (n > 0) { int v = (n - 1) % 26; b.insert(0, (char)('A' + v)); n = (n - 1) / 26; } return b.toString(); }
    private static void put(ZipOutputStream z, String name, String data) throws Exception { z.putNextEntry(new ZipEntry(name)); z.write(data.getBytes(StandardCharsets.UTF_8)); z.closeEntry(); }
    private static String contentTypes() { return "<?xml version=\"1.0\"?><Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default Extension=\"xml\" ContentType=\"application/xml\"/><Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/><Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/><Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/><Override PartName=\"/xl/worksheets/sheet2.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/><Override PartName=\"/xl/worksheets/sheet3.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/></Types>"; }
    private static String rootRels() { return "<?xml version=\"1.0\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/></Relationships>"; }
    private static String workbook() { return "<?xml version=\"1.0\"?><workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"><sheets><sheet name=\"Campaign Summary\" sheetId=\"1\" r:id=\"rId1\"/><sheet name=\"Customer Results\" sheetId=\"2\" r:id=\"rId2\"/><sheet name=\"Officer Summary\" sheetId=\"3\" r:id=\"rId3\"/></sheets></workbook>"; }
    private static String workbookRels() { return "<?xml version=\"1.0\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/><Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet2.xml\"/><Relationship Id=\"rId3\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet3.xml\"/><Relationship Id=\"rId4\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/></Relationships>"; }
    private static String styles() { return "<?xml version=\"1.0\"?><styleSheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><fonts count=\"2\"><font><sz val=\"11\"/><name val=\"Aptos\"/></font><font><b/><sz val=\"12\"/><name val=\"Aptos Display\"/></font></fonts><fills count=\"2\"><fill><patternFill patternType=\"none\"/></fill><fill><patternFill patternType=\"solid\"><fgColor rgb=\"17365D\"/></patternFill></fill></fills><borders count=\"1\"><border><left/><right/><top/><bottom/><diagonal/></border></borders><cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs><cellXfs count=\"2\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/><xf numFmtId=\"0\" fontId=\"1\" fillId=\"1\" borderId=\"0\" applyFont=\"1\" applyFill=\"1\"><alignment horizontal=\"center\" wrapText=\"1\"/></xf></cellXfs></styleSheet>"; }
    private static String xml(String value) { if (value == null) return ""; return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;").replace("\u0000", ""); }
    private static String text(String value) { return value == null ? "" : value; }
    private static String dateTime(Long value) { return value == null ? "" : new SimpleDateFormat("dd MMM yyyy HH:mm", Locale.US).format(new Date(value)); }
    private static final class OfficerTotals { int recipients, sent, delivered, failed, pending; }
}
