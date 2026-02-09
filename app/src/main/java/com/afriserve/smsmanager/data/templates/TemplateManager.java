package com.afriserve.smsmanager.data.templates;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import dagger.hilt.android.qualifiers.ApplicationContext;

import com.afriserve.smsmanager.data.dao.TemplateDao;
import com.afriserve.smsmanager.data.entity.TemplateEntity;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
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
    // Legacy keys used for one-time migration from SharedPreferences.
    private static final String KEY_TEMPLATES = "templates";
    private static final String KEY_RECENT_TEMPLATES = "recent_templates";
    private static final String KEY_FAVORITE_TEMPLATES = "favorite_templates";
    private static final String KEY_TEMPLATE_STATS = "template_stats";
    private static final int MAX_RECENT_TEMPLATES = 10;
    
    private final TemplateDao templateDao;
    private final SharedPreferences preferences;
    private final Gson gson;
    private final ExecutorService executor;
    private final Object lock = new Object();
    private List<SmsTemplate> templates = new ArrayList<>();
    private List<String> recentTemplates = new ArrayList<>();
    private List<String> favoriteTemplates = new ArrayList<>();
    private TemplateUsageStats usageStats = new TemplateUsageStats();
    
    @Inject
    public TemplateManager(@ApplicationContext Context context, TemplateDao templateDao) {
        this.templateDao = templateDao;
        this.preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.gson = new Gson();
        this.executor = Executors.newSingleThreadExecutor();
        
        loadData();
    }
    
    /**
     * Get all templates sorted by last modified date
     */
    public List<SmsTemplate> getAllTemplates() {
        synchronized (lock) {
            return new ArrayList<>(templates);
        }
    }
    
    /**
     * Get favorite templates
     */
    public List<SmsTemplate> getFavoriteTemplates() {
        List<SmsTemplate> favorites = new ArrayList<>();
        synchronized (lock) {
            for (SmsTemplate template : templates) {
                if (favoriteTemplates.contains(template.getId())) {
                    favorites.add(template);
                }
            }
        }
        return favorites;
    }
    
    /**
     * Get recent templates
     */
    public List<SmsTemplate> getRecentTemplates() {
        List<SmsTemplate> recent = new ArrayList<>();
        synchronized (lock) {
            for (String templateId : recentTemplates) {
                SmsTemplate template = getTemplateById(templateId);
                if (template != null) {
                    recent.add(template);
                }
            }
        }
        return recent;
    }
    
    /**
     * Get template by ID
     */
    public SmsTemplate getTemplateById(String templateId) {
        synchronized (lock) {
            for (SmsTemplate template : templates) {
                if (template.getId().equals(templateId)) {
                    return template;
                }
            }
        }
        return null;
    }
    
    /**
     * Search templates by name or content
     */
    public List<SmsTemplate> searchTemplates(String query) {
        if (query == null || query.trim().isEmpty()) {
            return getAllTemplates();
        }

        List<SmsTemplate> results = new ArrayList<>();
        String lowerQuery = query.trim().toLowerCase(Locale.ROOT);

        synchronized (lock) {
            for (SmsTemplate template : templates) {
                String name = template.getName();
                String content = template.getContent();
                if ((name != null && name.toLowerCase(Locale.ROOT).contains(lowerQuery)) ||
                    (content != null && content.toLowerCase(Locale.ROOT).contains(lowerQuery))) {
                    results.add(template);
                }
            }
        }

        return results;
    }
    
    /**
     * Save or update a template
     */
    public void saveTemplate(SmsTemplate template) {
        executor.execute(() -> {
            try {
                long now = System.currentTimeMillis();
                TemplateEntity entity = toEntity(template);

                Long templateId = parseTemplateId(template.getId());
                TemplateEntity existing = null;
                if (templateId != null) {
                    try {
                        existing = templateDao.getTemplateById(templateId).blockingGet();
                    } catch (Exception ignored) {
                        existing = null;
                    }
                }

                if (existing != null) {
                    entity.id = existing.id;
                    entity.createdAt = existing.createdAt;
                    entity.isFavorite = existing.isFavorite;
                    entity.usageCount = existing.usageCount;
                    entity.lastUsed = existing.lastUsed;
                } else if (entity.createdAt == 0L) {
                    entity.createdAt = now;
                }

                entity.updatedAt = now;
                entity.lastUsed = now; // mark as recently used

                if (entity.id == 0L) {
                    Long newId = templateDao.insertTemplate(entity).blockingGet();
                    if (newId != null) {
                        entity.id = newId;
                    }
                } else {
                    templateDao.updateTemplate(entity).blockingAwait();
                }

                template.setId(String.valueOf(entity.id));
                template.setCreatedAt(entity.createdAt);
                template.setLastModified(entity.updatedAt);

                reloadCache();
                
                Log.d(TAG, "Template saved: " + template.getName());
                
            } catch (Exception e) {
                Log.e(TAG, "Error saving template", e);
            }
        });
    }
    
    /**
     * Delete a template
     */
    public void deleteTemplate(String templateId) {
        executor.execute(() -> {
            try {
                Long id = parseTemplateId(templateId);
                if (id != null) {
                    templateDao.deleteTemplateById(id).blockingAwait();
                }
                reloadCache();
                
                Log.d(TAG, "Template deleted: " + templateId);
                
            } catch (Exception e) {
                Log.e(TAG, "Error deleting template", e);
            }
        });
    }
    
    /**
     * Toggle favorite status
     */
    public void toggleFavorite(String templateId) {
        executor.execute(() -> {
            try {
                Long id = parseTemplateId(templateId);
                if (id != null) {
                    TemplateEntity entity = null;
                    try {
                        entity = templateDao.getTemplateById(id).blockingGet();
                    } catch (Exception ignored) {
                        entity = null;
                    }
                    if (entity != null) {
                        entity.isFavorite = !entity.isFavorite;
                        templateDao.updateTemplate(entity).blockingAwait();
                    }
                }
                reloadCache();
                
            } catch (Exception e) {
                Log.e(TAG, "Error toggling favorite", e);
            }
        });
    }
    
    /**
     * Check if template is favorite
     */
    public boolean isFavorite(String templateId) {
        synchronized (lock) {
            return favoriteTemplates.contains(templateId);
        }
    }
    
    /**
     * Record template usage
     */
    public void recordUsage(String templateId, int recipientCount) {
        executor.execute(() -> {
            try {
                Long id = parseTemplateId(templateId);
                if (id != null) {
                    long now = System.currentTimeMillis();
                    templateDao.incrementUsage(id, now).blockingAwait();
                }
                reloadCache();
                
            } catch (Exception e) {
                Log.e(TAG, "Error recording usage", e);
            }
        });
    }
    
    /**
     * Get template usage statistics
     */
    public TemplateUsageStats getUsageStats() {
        synchronized (lock) {
            return usageStats != null ? usageStats : new TemplateUsageStats();
        }
    }
    
    /**
     * Get template names list (for dropdown/selection)
     */
    public List<String> getTemplateNames() {
        List<String> names = new ArrayList<>();
        synchronized (lock) {
            for (SmsTemplate template : templates) {
                names.add(template.getName());
            }
        }
        Collections.sort(names);
        return names;
    }
    
    /**
     * Get template content by name
     */
    public String getTemplateContent(String templateName) {
        if (templateName == null) {
            return null;
        }
        synchronized (lock) {
            for (SmsTemplate template : templates) {
                if (templateName.equals(template.getName())) {
                    return template.getContent();
                }
            }
        }
        return null;
    }
    
    /**
     * Load data from Room (migrates legacy SharedPreferences if needed)
     */
    private void loadData() {
        try {
            migrateLegacyTemplatesIfNeeded();
            reloadCache();
            Log.d(TAG, "Loaded " + templates.size() + " templates");
            
        } catch (Exception e) {
            Log.e(TAG, "Error loading data from Room", e);
            synchronized (lock) {
                templates = new ArrayList<>();
                recentTemplates = new ArrayList<>();
                favoriteTemplates = new ArrayList<>();
                usageStats = new TemplateUsageStats();
            }
        }
    }

    /**
     * Refresh in-memory cache from Room
     */
    private void reloadCache() {
        try {
            List<TemplateEntity> entities = templateDao.getAllTemplates().blockingGet();
            List<TemplateEntity> recentEntities = templateDao.getRecentlyUsedTemplates(MAX_RECENT_TEMPLATES).blockingGet();

            synchronized (lock) {
                templates = new ArrayList<>();
                favoriteTemplates = new ArrayList<>();
                recentTemplates = new ArrayList<>();
                usageStats = new TemplateUsageStats();

                if (entities != null) {
                    for (TemplateEntity entity : entities) {
                        templates.add(fromEntity(entity));
                        if (entity.isFavorite) {
                            favoriteTemplates.add(String.valueOf(entity.id));
                        }
                    }
                }

                if (recentEntities != null) {
                    for (TemplateEntity entity : recentEntities) {
                        recentTemplates.add(String.valueOf(entity.id));
                    }
                }

                // Keep list sorted by last modified
                templates.sort((t1, t2) -> Long.compare(t2.getLastModified(), t1.getLastModified()));
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to reload template cache", e);
        }
    }

    private SmsTemplate fromEntity(TemplateEntity entity) {
        SmsTemplate template = new SmsTemplate();
        template.setId(String.valueOf(entity.id));
        template.setName(entity.name);
        template.setContent(entity.content);
        template.setDescription(entity.description);
        template.setCategory(entity.category);
        template.setUsageCount(entity.usageCount);
        template.setCreatedAt(entity.createdAt);
        template.setLastModified(entity.updatedAt);
        return template;
    }

    private TemplateEntity toEntity(SmsTemplate template) {
        TemplateEntity entity = new TemplateEntity();
        entity.name = template.getName();
        entity.content = template.getContent();
        entity.description = template.getDescription();
        entity.category = template.getCategory();
        entity.usageCount = template.getUsageCount();
        entity.createdAt = template.getCreatedAt();
        entity.updatedAt = template.getLastModified();
        return entity;
    }

    private Long parseTemplateId(String templateId) {
        if (templateId == null || templateId.trim().isEmpty()) {
            return null;
        }
        try {
            return Long.parseLong(templateId);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * One-time migration: move legacy SharedPreferences templates into Room
     */
    private void migrateLegacyTemplatesIfNeeded() {
        try {
            Integer count = templateDao.getTotalTemplatesCount().blockingGet();
            if (count != null && count > 0) {
                return;
            }
        } catch (Exception e) {
            Log.w(TAG, "Unable to check template count for migration", e);
            return;
        }

        try {
            String templatesJson = preferences.getString(KEY_TEMPLATES, "[]");
            Type templatesType = new TypeToken<List<SmsTemplate>>(){}.getType();
            List<SmsTemplate> legacyTemplates = gson.fromJson(templatesJson, templatesType);

            if (legacyTemplates == null || legacyTemplates.isEmpty()) {
                return;
            }

            String recentJson = preferences.getString(KEY_RECENT_TEMPLATES, "[]");
            Type recentType = new TypeToken<List<String>>(){}.getType();
            List<String> legacyRecent = gson.fromJson(recentJson, recentType);
            if (legacyRecent == null) legacyRecent = new ArrayList<>();

            String favoritesJson = preferences.getString(KEY_FAVORITE_TEMPLATES, "[]");
            Type favoritesType = new TypeToken<List<String>>(){}.getType();
            List<String> legacyFavorites = gson.fromJson(favoritesJson, favoritesType);
            if (legacyFavorites == null) legacyFavorites = new ArrayList<>();

            for (SmsTemplate legacy : legacyTemplates) {
                TemplateEntity entity = new TemplateEntity();
                entity.name = legacy.getName();
                entity.content = legacy.getContent();
                entity.description = legacy.getDescription();
                entity.category = legacy.getCategory();
                entity.usageCount = legacy.getUsageCount();
                entity.isFavorite = legacyFavorites.contains(legacy.getId());

                long createdAt = legacy.getCreatedAt();
                long updatedAt = legacy.getLastModified();
                long now = System.currentTimeMillis();

                entity.createdAt = createdAt > 0 ? createdAt : now;
                entity.updatedAt = updatedAt > 0 ? updatedAt : entity.createdAt;

                if (legacyRecent.contains(legacy.getId())) {
                    entity.lastUsed = entity.updatedAt;
                }

                templateDao.insertTemplate(entity).blockingGet();
            }

            // Clear legacy data after successful migration
            preferences.edit()
                .remove(KEY_TEMPLATES)
                .remove(KEY_RECENT_TEMPLATES)
                .remove(KEY_FAVORITE_TEMPLATES)
                .remove(KEY_TEMPLATE_STATS)
                .apply();

            Log.d(TAG, "Migrated " + legacyTemplates.size() + " legacy templates to Room");

        } catch (Exception e) {
            Log.e(TAG, "Failed to migrate legacy templates", e);
        }
    }
    
    /**
     * Clear all data
     */
    public void clearAllData() {
        executor.execute(() -> {
            try {
                templateDao.deleteAllTemplates().blockingAwait();
                synchronized (lock) {
                    templates.clear();
                    recentTemplates.clear();
                    favoriteTemplates.clear();
                    usageStats = new TemplateUsageStats();
                }
                
                Log.d(TAG, "All template data cleared");
                
            } catch (Exception e) {
                Log.e(TAG, "Error clearing data", e);
            }
        });
    }
}
