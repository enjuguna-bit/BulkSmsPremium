package com.afriserve.smsmanager.data.dao;

import androidx.paging.PagingSource;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Update;
import androidx.room.Delete;
import androidx.room.Query;
import androidx.room.OnConflictStrategy;

import com.afriserve.smsmanager.data.entity.TemplateEntity;

import java.util.List;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;

/**
 * Data Access Object for TemplateEntity.
 *
 * Changes v2:
 *  - All standard list/paged queries now filter "WHERE isArchived = 0" so
 *    soft-deleted templates are automatically excluded from normal results.
 *  - Added archiveTemplate() / unarchiveTemplate() helpers.
 *  - Added getArchivedTemplatesPaged() for an archive management screen.
 *  - Added updateCharCount() to keep the pre-computed charCount in sync when
 *    template content is edited outside of the entity's helper method.
 */
@Dao
public interface TemplateDao {

    // ── Paged queries (active templates only) ─────────────────────────────────

    @Query("SELECT * FROM template_entities WHERE isArchived = 0 ORDER BY name ASC")
    PagingSource<Integer, TemplateEntity> getAllTemplatesPaged();

    @Query("SELECT * FROM template_entities WHERE isArchived = 0 AND category = :category ORDER BY name ASC")
    PagingSource<Integer, TemplateEntity> getTemplatesByCategoryPaged(String category);

    @Query("SELECT * FROM template_entities WHERE isArchived = 0 AND isFavorite = 1 ORDER BY name ASC")
    PagingSource<Integer, TemplateEntity> getFavoriteTemplatesPaged();

    @Query("SELECT * FROM template_entities WHERE isArchived = 0 AND (name LIKE '%' || :query || '%' OR content LIKE '%' || :query || '%' OR description LIKE '%' || :query || '%') ORDER BY name ASC")
    PagingSource<Integer, TemplateEntity> searchTemplatesPaged(String query);

    @Query("SELECT * FROM template_entities WHERE isArchived = 0 ORDER BY usageCount DESC, lastUsed DESC")
    PagingSource<Integer, TemplateEntity> getMostUsedTemplatesPaged();

    @Query("SELECT * FROM template_entities WHERE isArchived = 0 AND lastUsed >= :timestamp ORDER BY lastUsed DESC")
    PagingSource<Integer, TemplateEntity> getRecentlyUsedTemplatesPaged(long timestamp);

    /** Archived templates (for an archive/trash management screen). */
    @Query("SELECT * FROM template_entities WHERE isArchived = 1 ORDER BY updatedAt DESC")
    PagingSource<Integer, TemplateEntity> getArchivedTemplatesPaged();

    // ── Single / List queries ──────────────────────────────────────────────────

    @Query("SELECT * FROM template_entities WHERE isArchived = 0 ORDER BY updatedAt DESC")
    Single<List<TemplateEntity>> getAllTemplates();

    @Query("SELECT * FROM template_entities WHERE isArchived = 0 AND isFavorite = 1 ORDER BY name ASC")
    Single<List<TemplateEntity>> getFavoriteTemplates();

    @Query("SELECT * FROM template_entities WHERE id = :id")
    Single<TemplateEntity> getTemplateById(long id);

    @Query("SELECT * FROM template_entities WHERE name = :name AND isArchived = 0")
    Single<TemplateEntity> getTemplateByName(String name);

    @Query("SELECT * FROM template_entities WHERE isArchived = 0 AND category = :category ORDER BY name ASC")
    Single<List<TemplateEntity>> getTemplatesByCategory(String category);

    @Query("SELECT DISTINCT category FROM template_entities WHERE isArchived = 0 AND category IS NOT NULL AND category != '' ORDER BY category ASC")
    Single<List<String>> getAllCategories();

    @Query("SELECT * FROM template_entities WHERE isArchived = 0 ORDER BY usageCount DESC LIMIT :limit")
    Single<List<TemplateEntity>> getMostUsedTemplates(int limit);

    @Query("SELECT * FROM template_entities WHERE isArchived = 0 AND isFavorite = 1 ORDER BY usageCount DESC LIMIT :limit")
    Single<List<TemplateEntity>> getFavoriteMostUsedTemplates(int limit);

    @Query("SELECT * FROM template_entities WHERE isArchived = 0 ORDER BY lastUsed DESC LIMIT :limit")
    Single<List<TemplateEntity>> getRecentlyUsedTemplates(int limit);

    @Query("SELECT * FROM template_entities WHERE isArchived = 0 ORDER BY createdAt DESC LIMIT :limit")
    Single<List<TemplateEntity>> getRecentlyCreatedTemplates(int limit);

    @Query("SELECT * FROM template_entities WHERE isArchived = 0 AND name LIKE :name || '%' ORDER BY name ASC LIMIT :limit")
    Single<List<TemplateEntity>> getTemplatesByNamePrefix(String name, int limit);

    @Query("SELECT * FROM template_entities WHERE isArchived = 0 AND content LIKE '%' || :keyword || '%' ORDER BY name ASC")
    Single<List<TemplateEntity>> getTemplatesByContentKeyword(String keyword);

    @Query("SELECT * FROM template_entities WHERE isArchived = 0 AND variables IS NOT NULL AND variables != '' ORDER BY name ASC")
    Single<List<TemplateEntity>> getTemplatesWithVariables();

    @Query("SELECT * FROM template_entities WHERE isArchived = 0 AND usageCount = 0 ORDER BY name ASC")
    Single<List<TemplateEntity>> getUnusedTemplates();

    @Query("SELECT * FROM template_entities WHERE isArchived = 0 AND usageCount > :minUsage ORDER BY usageCount DESC")
    Single<List<TemplateEntity>> getTemplatesWithMinimumUsage(int minUsage);

    @Query("SELECT * FROM template_entities WHERE isArchived = 0 AND lastUsed >= :startTime AND lastUsed <= :endTime ORDER BY usageCount DESC")
    Single<List<TemplateEntity>> getMostUsedTemplatesInTimeRange(long startTime, long endTime);

    @Query("SELECT * FROM template_entities WHERE isArchived = 0 AND isFavorite = 1 AND lastUsed IS NULL ORDER BY name ASC")
    Single<List<TemplateEntity>> getUnusedFavoriteTemplates();

    @Query("SELECT * FROM template_entities WHERE isArchived = 0 ORDER BY usageCount DESC LIMIT 1")
    Single<TemplateEntity> getMostUsedTemplate();

    @Query("SELECT * FROM template_entities WHERE isArchived = 0 ORDER BY lastUsed DESC LIMIT 1")
    Single<TemplateEntity> getLastUsedTemplate();

    // ── Count queries ──────────────────────────────────────────────────────────

    @Query("SELECT COUNT(*) FROM template_entities WHERE isArchived = 0")
    Single<Integer> getTotalTemplatesCount();

    @Query("SELECT COUNT(*) FROM template_entities WHERE isArchived = 0 AND category = :category")
    Single<Integer> getTemplatesCountByCategory(String category);

    @Query("SELECT COUNT(*) FROM template_entities WHERE isArchived = 0 AND isFavorite = 1")
    Single<Integer> getFavoriteTemplatesCount();

    @Query("SELECT COUNT(*) FROM template_entities WHERE isArchived = 0 AND lastUsed >= :timestamp")
    Single<Integer> getRecentlyUsedTemplatesCount(long timestamp);

    @Query("SELECT SUM(usageCount) FROM template_entities WHERE isArchived = 0")
    Single<Integer> getTotalUsageCount();

    @Query("SELECT COUNT(*) FROM template_entities WHERE isArchived = 1")
    Single<Integer> getArchivedTemplatesCount();

    // ── Reactive counts ────────────────────────────────────────────────────────

    @Query("SELECT COUNT(*) FROM template_entities WHERE isArchived = 0 AND createdAt >= :timestamp")
    Flowable<Integer> getNewTemplatesCountSince(long timestamp);

    @Query("SELECT COUNT(*) FROM template_entities WHERE isArchived = 0 AND lastUsed >= :timestamp")
    Flowable<Integer> getUsedTemplatesCountSince(long timestamp);

    // ── Insert / Update / Delete ───────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    Single<Long> insertTemplate(TemplateEntity template);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    Completable insertTemplateList(List<TemplateEntity> templates);

    @Update
    Completable updateTemplate(TemplateEntity template);

    @Update
    Completable updateTemplateList(List<TemplateEntity> templates);

    @Delete
    Completable deleteTemplate(TemplateEntity template);

    @Delete
    Completable deleteTemplateList(List<TemplateEntity> templates);

    @Query("DELETE FROM template_entities WHERE id = :id")
    Completable deleteTemplateById(long id);

    @Query("DELETE FROM template_entities WHERE category = :category AND isArchived = 0")
    Completable deleteTemplatesByCategory(String category);

    @Query("DELETE FROM template_entities WHERE createdAt < :timestamp AND isFavorite = 0 AND isArchived = 0")
    Completable deleteOldUnusedTemplates(long timestamp);

    /** Hard-deletes all archived templates (use as a "empty trash" action). */
    @Query("DELETE FROM template_entities WHERE isArchived = 1")
    Completable deleteAllArchivedTemplates();

    @Query("DELETE FROM template_entities")
    Completable deleteAllTemplates();

    // ── Field-level update helpers ─────────────────────────────────────────────

    @Query("UPDATE template_entities SET isFavorite = 1, updatedAt = :timestamp WHERE id = :id")
    Completable addToFavorites(long id, long timestamp);

    @Query("UPDATE template_entities SET isFavorite = 0, updatedAt = :timestamp WHERE id = :id")
    Completable removeFromFavorites(long id, long timestamp);

    /** Soft-delete: hides the template from normal queries without losing usage history. */
    @Query("UPDATE template_entities SET isArchived = 1, updatedAt = :timestamp WHERE id = :id")
    Completable archiveTemplate(long id, long timestamp);

    /** Restores a soft-deleted template. */
    @Query("UPDATE template_entities SET isArchived = 0, updatedAt = :timestamp WHERE id = :id")
    Completable unarchiveTemplate(long id, long timestamp);

    @Query("UPDATE template_entities SET usageCount = usageCount + 1, lastUsed = :timestamp, updatedAt = :timestamp WHERE id = :id")
    Completable incrementUsage(long id, long timestamp);

    @Query("UPDATE template_entities SET usageCount = :usageCount, lastUsed = :lastUsed, updatedAt = :timestamp WHERE id = :id")
    Completable updateUsageStats(long id, int usageCount, long lastUsed, long timestamp);

    @Query("UPDATE template_entities SET category = :newCategory, updatedAt = :timestamp WHERE id = :id")
    Completable updateCategory(long id, String newCategory, long timestamp);

    @Query("UPDATE template_entities SET name = :name, content = :content, description = :description, variables = :variables, charCount = :charCount, updatedAt = :timestamp WHERE id = :id")
    Completable updateTemplateContent(long id, String name, String content, String description,
                                      String variables, int charCount, long timestamp);

    /**
     * Keeps the stored charCount in sync after content edits made via direct field
     * assignment (bypassing {@link TemplateEntity#setContentAndRefreshCharCount}).
     */
    @Query("UPDATE template_entities SET charCount = :charCount, updatedAt = :timestamp WHERE id = :id")
    Completable updateCharCount(long id, int charCount, long timestamp);
}
