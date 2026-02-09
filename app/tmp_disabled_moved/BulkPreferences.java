package com.bulksms.smsmanager;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

/**
 * Modern Preferences Manager for Bulk SMS settings
 */
public class BulkPreferences {
    private static final String PREFS_NAME = "bulk_sms_prefs";
    private static final String KEY_MODE = "last_mode";
    private static final String KEY_TEMPLATE = "last_template";
    private static final String KEY_RECENTS = "recent_templates";
    private static final String KEY_SEND_SPEED = "send_speed";
    private static final String KEY_SIM_SLOT = "sim_slot";
    
    private static final String DEFAULT_TEMPLATE = 
        "Hello {name}, your arrears are KES {amount}. Pay via Paybill 247777.";
    private static final int DEFAULT_SEND_SPEED = 400;
    private static final int DEFAULT_SIM_SLOT = 0;
    
    private final SharedPreferences prefs;
    private final Gson gson;

    public BulkPreferences(Context context) {
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.gson = new Gson();
    }

    // Template methods
    public String getTemplate() {
        return prefs.getString(KEY_TEMPLATE, DEFAULT_TEMPLATE);
    }

    public void setTemplate(String template) {
        prefs.edit().putString(KEY_TEMPLATE, template).apply();
    }

    // Recents methods
    public List<String> getRecents() {
        String json = prefs.getString(KEY_RECENTS, "[]");
        try {
            Type listType = new TypeToken<List<String>>(){}.getType();
            return gson.fromJson(json, listType);
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    public void setRecents(List<String> recents) {
        String json = gson.toJson(recents.subList(0, Math.min(recents.size(), 10)));
        prefs.edit().putString(KEY_RECENTS, json).apply();
    }

    public void addRecent(String template) {
        List<String> recents = getRecents();
        recents.remove(template);
        recents.add(0, template);
        setRecents(recents);
    }

    public void clearRecents() {
        prefs.edit().putString(KEY_RECENTS, "[]").apply();
    }

    // Send speed methods
    public int getSendSpeed() {
        return prefs.getInt(KEY_SEND_SPEED, DEFAULT_SEND_SPEED);
    }

    public void setSendSpeed(int sendSpeed) {
        prefs.edit().putInt(KEY_SEND_SPEED, sendSpeed).apply();
    }

    // SIM slot methods
    public int getSimSlot() {
        return prefs.getInt(KEY_SIM_SLOT, DEFAULT_SIM_SLOT);
    }

    public void setSimSlot(int simSlot) {
        prefs.edit().putInt(KEY_SIM_SLOT, simSlot).apply();
    }

    // Mode methods
    public String getMode() {
        return prefs.getString(KEY_MODE, "excel");
    }

    public void setMode(String mode) {
        prefs.edit().putString(KEY_MODE, mode).apply();
    }
}
