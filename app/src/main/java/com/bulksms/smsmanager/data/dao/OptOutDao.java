package com.bulksms.smsmanager.data.dao;

import androidx.paging.PagingSource;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Update;
import androidx.room.Delete;
import androidx.room.Query;

import com.bulksms.smsmanager.data.entity.OptOutEntity;

import java.util.List;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;

/**
 * Data Access Object for OptOut entities
 */
@Dao
public interface OptOutDao {
    
    @Insert
    Completable insertOptOut(OptOutEntity optOut);
    
    @Insert
    Completable insertOptOuts(List<OptOutEntity> optOuts);
    
    @Update
    Completable updateOptOut(OptOutEntity optOut);
    
    @Delete
    Completable deleteOptOut(OptOutEntity optOut);
    
    @Query("SELECT * FROM opt_outs WHERE phoneNumber = :phoneNumber")
    Single<OptOutEntity> getOptOutByPhone(String phoneNumber);
    
    @Query("DELETE FROM opt_outs WHERE phoneNumber = :phoneNumber")
    Completable deleteOptOutByPhone(String phoneNumber);
    
    @Query("SELECT * FROM opt_outs WHERE isActive = 1 ORDER BY optOutTime DESC")
    PagingSource<Integer, OptOutEntity> getActiveOptOutsPaged();
    
    @Query("SELECT * FROM opt_outs WHERE isActive = 1 ORDER BY optOutTime DESC LIMIT :limit")
    Single<List<OptOutEntity>> getRecentOptOuts(int limit);
    
    @Query("SELECT * FROM opt_outs WHERE source = :source ORDER BY optOutTime DESC")
    PagingSource<Integer, OptOutEntity> getOptOutsBySourcePaged(String source);
    
    @Query("SELECT * FROM opt_outs WHERE campaignId = :campaignId ORDER BY optOutTime DESC")
    Single<List<OptOutEntity>> getOptOutsByCampaign(long campaignId);
    
    @Query("SELECT COUNT(*) FROM opt_outs WHERE isActive = 1")
    Single<Integer> getOptOutCount();
    
    @Query("SELECT COUNT(*) FROM opt_outs WHERE source = :source AND isActive = 1")
    Single<Integer> getOptOutCountBySource(String source);
    
    @Query("SELECT COUNT(*) FROM opt_outs WHERE optOutTime >= :since AND isActive = 1")
    Single<Integer> getOptOutCountSince(long since);
    
    @Query("SELECT source, COUNT(*) as count FROM opt_outs WHERE isActive = 1 GROUP BY source ORDER BY count DESC")
    Single<List<OptOutSourceStats>> getOptOutStatsBySource();
    
    @Query("SELECT DATE(optOutTime/1000, 'unixepoch') as date, COUNT(*) as count " +
           "FROM opt_outs WHERE optOutTime >= :since AND isActive = 1 " +
           "GROUP BY DATE(optOutTime/1000, 'unixepoch') ORDER BY date DESC")
    Single<List<OptOutDailyStats>> getOptOutDailyStats(long since);
    
    @Query("UPDATE opt_outs SET isActive = 0 WHERE phoneNumber = :phoneNumber")
    Completable deactivateOptOut(String phoneNumber);
    
    @Query("UPDATE opt_outs SET isActive = 0, updatedAt = :updatedAt WHERE id = :id")
    Completable reactivateOptOut(long id, long updatedAt);
    
    @Query("DELETE FROM opt_outs WHERE optOutTime < :before AND isActive = 0")
    Completable deleteInactiveOptOutsBefore(long before);
    
    @Query("SELECT phoneNumber FROM opt_outs WHERE isActive = 1")
    Single<List<String>> getAllActiveOptOutNumbers();
    
    @Query("SELECT EXISTS(SELECT 1 FROM opt_outs WHERE phoneNumber = :phoneNumber AND isActive = 1)")
    Single<Boolean> isOptedOut(String phoneNumber);
    
    /**
     * Statistics data classes
     */
    class OptOutSourceStats {
        public final String source;
        public final int count;
        
        public OptOutSourceStats(String source, int count) {
            this.source = source;
            this.count = count;
        }
    }
    
    class OptOutDailyStats {
        public final String date;
        public final int count;
        
        public OptOutDailyStats(String date, int count) {
            this.date = date;
            this.count = count;
        }
    }
}
