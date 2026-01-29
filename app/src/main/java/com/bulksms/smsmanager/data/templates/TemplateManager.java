package com.bulksms.smsmanager.data.templates;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Template Management System for SMS templates
 * Provides CRUD operations with persistence and analytics
 */
@Singleton
public class TemplateManager {
    private static final String TAG = "TemplateManager";
    private static final String PREFS_NAME = "bulk_sms_templates";
    private static final String KEY_TEMPLATES = "templates";
    private static final String KEY_RECENT_TEMPLATES = "recent_templates";
    private static final String KEY_FAVORITE_TEMPLATES = "favorite_templates";
    private static final String KEY_TEMPLATE_STATS = "template_stats";
    private static final int MAX_RECENT_TEMPLATES = 10;
    private static final int MAX_HISTORY_ENTRIES = 50;
    
    private final SharedPreferences preferences;
    private final Gson gson;
    private final ExecutorService executor;
    private List<SmsTemplate> templates;
    private List<String> recentTemplates;
    private List<String> favoriteTemplates;
    private TemplateUsageStats usageStats;
    
    @Inject
    public TemplateManager(Context context) {
        this.preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.gson = new Gson();
        this.executor = Executors.newSingleThreadExecutor();
        
        loadData();
    }
    
    /**
     * SMS Template data class
     */
    public static class SmsTemplate {
        public String id;
        public String name;
        public String content;
        public String category;
        public List<String> variables;
        public long createdAt;
        public long lastUsed;
        public int usageCount;
        public boolean isFavorite;
        public boolean isSystem;
        public String description;
        
        public SmsTemplate() {
            this.id = "template_" + System.currentTimeMillis() + "_" + (int)(Math.random() * 1000);
            this.createdAt = System.currentTimeMillis();
            this.lastUsed = 0;
            this.usageCount = 0;
            this.isFavorite = false;
            this.isSystem = false;
            this.variables = new ArrayList<>();
        }
        
        public SmsTemplate(String name, String content, String category) {
            this();
            this.name = name;
            this.content = content;
            this.category = category;
            this.extractVariables();
        }
        
        /**
         * Extract variables from template content
         */
        private void extractVariables() {
            variables.clear();
            String[] parts = content.split("\\{");
            for (int i = 1; i < parts.length; i++) {
                int endIndex = parts[i].indexOf('}');
                if (endIndex > 0) {
                    String variable = parts[i].substring(0, endIndex).trim();
                    if (!variables.contains(variable)) {
                        variables.add(variable);
                    }
                }
            }
        }
        
        /**
         * Format template with given variables
         */
        public String format(java.util.Map<String, String> variables) {
            String result = content;
            for (java.util.Map.Entry<String, String> entry : variables.entrySet()) {
                result = result.replace("{" + entry.getKey() + "}", entry.getValue());
            }
            return result;
        }
        
        /**
         * Get all variables used in this template
         */
        public List<String> getVariables() {
            return new ArrayList<>(variables);
        }
        
        /**
         * Check if template uses a specific variable
         */
        public boolean usesVariable(String variable) {
            return variables.contains(variable);
        }
    }
    
    /**
     * Template usage statistics
     */
    public static class TemplateUsageStats {
        public long totalUsage;
        public long lastReset;
        public java.util.Map<String, Integer> templateUsageCount;
        public java.util.Map<String, Long> templateLastUsed;
        
        public TemplateUsageStats() {
            this.totalUsage = 0;
            this.lastReset = System.currentTimeMillis();
            this.templateUsageCount = new java.util.HashMap<>();
            this.templateLastUsed = new java.util.HashMap<>();
        }
    }
    
    /**
     * Load all data from preferences
     */
    private void loadData() {
        executor.execute(() -> {
            try {
                // Load templates
                String templatesJson = preferences.getString(KEY_TEMPLATES, null);
                if (templatesJson != null) {
                    Type listType = new TypeToken<List<SmsTemplate>>(){}.getType();
                    templates = gson.fromJson(templatesJson, listType);
                } else {
                    templates = new ArrayList<>();
                    loadSystemTemplates();
                }
                
                // Load recent templates
                String recentJson = preferences.getString(KEY_RECENT_TEMPLATES, null);
                if (recentJson != null) {
                    Type listType = new TypeToken<List<String>>(){}.getType();
                    recentTemplates = gson.fromJson(recentJson, listType);
                } else {
                    recentTemplates = new ArrayList<>();
                }
                
                // Load favorite templates
                String favoritesJson = preferences.getString(KEY_FAVORITE_TEMPLATES, null);
                if (favoritesJson != null) {
                    Type listType = new TypeToken<List<String>>(){}.getType();
                    favoriteTemplates = gson.fromJson(favoritesJson, listType);
                } else {
                    favoriteTemplates = new ArrayList<>();
                }
                
                // Load usage stats
                String statsJson = preferences.getString(KEY_TEMPLATE_STATS, null);
                if (statsJson != null) {
                    usageStats = gson.fromJson(statsJson, TemplateUsageStats.class);
                } else {
                    usageStats = new TemplateUsageStats();
                }
                
                Log.d(TAG, "Loaded " + templates.size() + " templates");
                
            } catch (Exception e) {
                Log.e(TAG, "Failed to load template data", e);
                initializeEmptyData();
            }
        });
    }
    
    /**
     * Initialize empty data structures
     */
    private void initializeEmptyData() {
        templates = new ArrayList<>();
        recentTemplates = new ArrayList<>();
        favoriteTemplates = new ArrayList<>();
        usageStats = new TemplateUsageStats();
        loadSystemTemplates();
    }
    
    /**
     * Load system default templates
     */
    private void loadSystemTemplates() {
        templates.add(createSystemTemplate("Welcome Message", 
            "Hello {name}, welcome to our service! Your account has been created successfully.", 
            "Welcome"));
        
        templates.add(createSystemTemplate("Payment Reminder", 
            "Dear {name}, this is a reminder that your payment of KES {amount} is due by {due_date}. Please pay promptly.", 
            "Payment"));
        
        templates.add(createSystemTemplate("Appointment Confirmation", 
            "Hello {name}, your appointment is confirmed for {date} at {time}. Location: {location}.", 
            "Appointment"));
        
        templates.add(createSystemTemplate("Marketing Campaign", 
            "Hi {name}, check out our special offer! Get {discount}% off on {product}. Valid until {expiry_date}.", 
            "Marketing"));
        
        templates.add(createSystemTemplate("Delivery Notification", 
            "Your order #{order_id} has been delivered. Thank you for shopping with us!", 
            "Delivery"));
        
        templates.add(createSystemTemplate("Arrears Notification", 
            "Hello {name}, your account has arrears of KES {amount}. Please contact us to arrange payment.", 
            "Arrears"));
    }
    
    /**
     * Create a system template
     */
    private SmsTemplate createSystemTemplate(String name, String content, String category) {
        SmsTemplate template = new SmsTemplate(name, content, category);
        template.isSystem = true;
        template.description = "System template - " + category;
        return template;
    }
    
    /**
     * Save all data to preferences
     */
    private void saveData() {
        executor.execute(() -> {
            try {
                // Save templates
                String templatesJson = gson.toJson(templates);
                preferences.edit()
                    .putString(KEY_TEMPLATES, templatesJson)
                    .apply();
                
                // Save recent templates
                String recentJson = gson.toJson(recentTemplates);
                preferences.edit()
                    .putString(KEY_RECENT_TEMPLATES, recentJson)
                    .apply();
                
                // Save favorite templates
                String favoritesJson = gson.toJson(favoriteTemplates);
                preferences.edit()
                    .putString(KEY_FAVORITE_TEMPLATES, favoritesJson)
                    .apply();
                
                // Save usage stats
                String statsJson = gson.toJson(usageStats);
                preferences.edit()
                    .putString(KEY_TEMPLATE_STATS, statsJson)
                    .apply();
                
                Log.d(TAG, "Template data saved");
                
            } catch (Exception e) {
                Log.e(TAG, "Failed to save template data", e);
            }
        });
    }
    
    /**
     * Get all templates
     */
    public List<SmsTemplate> getTemplates() {
        return new ArrayList<>(templates);
    }
    
    /**
     * Get templates by category
     */
    public List<SmsTemplate> getTemplatesByCategory(String category) {
        List<SmsTemplate> result = new ArrayList<>();
        for (SmsTemplate template : templates) {
            if (category.equals(template.category)) {
                result.add(template);
            }
        }
        return result;
    }
    
    /**
     * Get favorite templates
     */
    public List<SmsTemplate> getFavoriteTemplates() {
        List<SmsTemplate> result = new ArrayList<>();
        for (String templateId : favoriteTemplates) {
            SmsTemplate template = getTemplateById(templateId);
            if (template != null) {
                result.add(template);
            }
        }
        return result;
    }
    
    /**
     * Get recent templates
     */
    public List<SmsTemplate> getRecentTemplates() {
        List<SmsTemplate> result = new ArrayList<>();
        for (String templateId : recentTemplates) {
            SmsTemplate template = getTemplateById(templateId);
            if (template != null) {
                result.add(template);
            }
        }
        return result;
    }
    
    /**
     * Get template by ID
     */
    public SmsTemplate getTemplateById(String templateId) {
        for (SmsTemplate template : templates) {
            if (template.id.equals(templateId)) {
                return template;
            }
        }
        return null;
    }
    
    /**
     * Search templates
     */
    public List<SmsTemplate> searchTemplates(String query) {
        List<SmsTemplate> result = new ArrayList<>();
        String lowerQuery = query.toLowerCase();
        
        for (SmsTemplate template : templates) {
            if (template.name.toLowerCase().contains(lowerQuery) ||
                template.content.toLowerCase().contains(lowerQuery) ||
                template.category.toLowerCase().contains(lowerQuery)) {
                result.add(template);
            }
        }
        
        return result;
    }
    
    /**
     * Save template
     */
    public void saveTemplate(SmsTemplate template) {
        if (template == null) return;
        
        // Extract variables from content
        template.extractVariables();
        
        // Check if template already exists
        for (int i = 0; i < templates.size(); i++) {
            if (templates.get(i).id.equals(template.id)) {
                templates.set(i, template);
                saveData();
                return;
            }
        }
        
        // Add new template
        templates.add(template);
        saveData();
    }
    
    /**
     * Delete template
     */
    public void deleteTemplate(String templateId) {
        for (int i = 0; i < templates.size(); i++) {
            if (templates.get(i).id.equals(templateId)) {
                SmsTemplate template = templates.get(i);
                
                // Don't delete system templates
                if (template.isSystem) {
                    Log.w(TAG, "Cannot delete system template: " + template.name);
                    return;
                }
                
                templates.remove(i);
                
                // Remove from favorites and recent
                favoriteTemplates.remove(templateId);
                recentTemplates.remove(templateId);
                
                saveData();
                return;
            }
        }
    }
    
    /**
     * Toggle favorite status
     */
    public void toggleFavorite(String templateId) {
        SmsTemplate template = getTemplateById(templateId);
        if (template != null) {
            template.isFavorite = !template.isFavorite;
            
            if (template.isFavorite) {
                if (!favoriteTemplates.contains(templateId)) {
                    favoriteTemplates.add(templateId);
                }
            } else {
                favoriteTemplates.remove(templateId);
            }
            
            saveData();
        }
    }
    
    /**
     * Record template usage
     */
    public void recordUsage(String templateId) {
        SmsTemplate template = getTemplateById(templateId);
        if (template != null) {
            template.lastUsed = System.currentTimeMillis();
            template.usageCount++;
            
            // Update recent templates
            recentTemplates.remove(templateId);
            recentTemplates.add(0, templateId);
            
            // Limit recent templates
            while (recentTemplates.size() > MAX_RECENT_TEMPLATES) {
                recentTemplates.remove(recentTemplates.size() - 1);
            }
            
            // Update usage stats
            usageStats.totalUsage++;
            usageStats.templateUsageCount.put(templateId, template.usageCount);
            usageStats.templateLastUsed.put(templateId, template.lastUsed);
            
            saveData();
        }
    }
    
    /**
     * Get template categories
     */
    public List<String> getCategories() {
        java.util.Set<String> categories = new java.util.HashSet<>();
        for (SmsTemplate template : templates) {
            categories.add(template.category);
        }
        List<String> result = new ArrayList<>(categories);
        Collections.sort(result);
        return result;
    }
    
    /**
     * Get popular templates (by usage count)
     */
    public List<SmsTemplate> getPopularTemplates(int limit) {
        List<SmsTemplate> result = new ArrayList<>(templates);
        Collections.sort(result, new Comparator<SmsTemplate>() {
            @Override
            public int compare(SmsTemplate t1, SmsTemplate t2) {
                return Integer.compare(t2.usageCount, t1.usageCount);
            }
        });
        
        return result.subList(0, Math.min(limit, result.size()));
    }
    
    /**
     * Get usage statistics
     */
    public TemplateUsageStats getUsageStats() {
        return usageStats;
    }
    
    /**
     * Reset usage statistics
     */
    public void resetUsageStats() {
        usageStats = new TemplateUsageStats();
        
        // Reset template usage counts
        for (SmsTemplate template : templates) {
            template.usageCount = 0;
            template.lastUsed = 0;
        }
        
        saveData();
    }
    
    /**
     * Export templates to JSON
     */
    public String exportTemplates() {
        try {
            List<SmsTemplate> exportTemplates = new ArrayList<>();
            for (SmsTemplate template : templates) {
                if (!template.isSystem) {
                    exportTemplates.add(template);
                }
            }
            return gson.toJson(exportTemplates);
        } catch (Exception e) {
            Log.e(TAG, "Failed to export templates", e);
            return null;
        }
    }
    
    /**
     * Import templates from JSON
     */
    public boolean importTemplates(String json) {
        try {
            Type listType = new TypeToken<List<SmsTemplate>>(){}.getType();
            List<SmsTemplate> importedTemplates = gson.fromJson(json, listType);
            
            if (importedTemplates != null) {
                for (SmsTemplate template : importedTemplates) {
                    // Generate new ID to avoid conflicts
                    template.id = "template_" + System.currentTimeMillis() + "_" + (int)(Math.random() * 1000);
                    template.isSystem = false;
                    template.createdAt = System.currentTimeMillis();
                    template.extractVariables();
                    templates.add(template);
                }
                
                saveData();
                return true;
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to import templates", e);
        }
        
        return false;
    }
    
    /**
     * Get template suggestions based on content
     */
    public List<SmsTemplate> getTemplateSuggestions(String content) {
        List<SmsTemplate> suggestions = new ArrayList<>();
        String lowerContent = content.toLowerCase();
        
        // Extract variables from content
        java.util.Set<String> contentVariables = new java.util.HashSet<>();
        String[] parts = content.split("\\{");
        for (int i = 1; i < parts.length; i++) {
            int endIndex = parts[i].indexOf('}');
            if (endIndex > 0) {
                String variable = parts[i].substring(0, endIndex).trim();
                contentVariables.add(variable);
            }
        }
        
        // Find templates with similar variables
        for (SmsTemplate template : templates) {
            int matchCount = 0;
            for (String variable : template.variables) {
                if (contentVariables.contains(variable)) {
                    matchCount++;
                }
            }
            
            // Suggest if at least 50% of variables match
            if (template.variables.size() > 0 && 
                (double) matchCount / template.variables.size() >= 0.5) {
                suggestions.add(template);
            }
        }
        
        // Sort by usage count
        Collections.sort(suggestions, new Comparator<SmsTemplate>() {
            @Override
            public int compare(SmsTemplate t1, SmsTemplate t2) {
                return Integer.compare(t2.usageCount, t1.usageCount);
            }
        });
        
        return suggestions.subList(0, Math.min(5, suggestions.size()));
    }
}
