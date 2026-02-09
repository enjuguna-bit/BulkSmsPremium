package com.afriserve.smsmanager.data.dao;

import androidx.paging.PagingSource;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Update;
import androidx.room.Delete;
import androidx.room.Query;
import androidx.room.OnConflictStrategy;

import com.afriserve.smsmanager.data.entity.Customer;

import java.util.List;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;

/**
 * Data Access Object for Customer entities
 */
@Dao
public interface CustomerDao {
    
    @Query("SELECT * FROM customers ORDER BY name ASC")
    PagingSource<Integer, Customer> getAllCustomersPaged();
    
    @Query("SELECT * FROM customers WHERE isFavorite = 1 ORDER BY name ASC")
    PagingSource<Integer, Customer> getFavoriteCustomersPaged();
    
    @Query("SELECT * FROM customers WHERE name LIKE '%' || :query || '%' OR phone LIKE '%' || :query || '%' OR email LIKE '%' || :query || '%' ORDER BY name ASC")
    PagingSource<Integer, Customer> searchCustomersPaged(String query);
    
    @Query("SELECT * FROM customers WHERE phone = :phone")
    Single<Customer> getCustomerByPhone(String phone);
    
    @Query("SELECT * FROM customers WHERE id = :id")
    Single<Customer> getCustomerById(long id);
    
    @Query("SELECT * FROM customers WHERE company = :company ORDER BY name ASC")
    Single<List<Customer>> getCustomersByCompany(String company);
    
    @Query("SELECT * FROM customers WHERE lastSeen >= :timestamp ORDER BY lastSeen DESC")
    Single<List<Customer>> getRecentlyActiveCustomers(long timestamp);
    
    @Query("SELECT COUNT(*) FROM customers")
    Single<Integer> getTotalCustomersCount();
    
    @Query("SELECT COUNT(*) FROM customers WHERE isFavorite = 1")
    Single<Integer> getFavoriteCustomersCount();
    
    @Query("SELECT COUNT(*) FROM customers WHERE company = :company")
    Single<Integer> getCustomersCountByCompany(String company);
    
    @Query("SELECT DISTINCT company FROM customers WHERE company IS NOT NULL AND company != '' ORDER BY company ASC")
    Single<List<String>> getAllCompanies();
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    Single<Long> insertCustomer(Customer customer);
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    Completable insertCustomerList(List<Customer> customers);
    
    @Update
    Completable updateCustomer(Customer customer);
    
    @Update
    Completable updateCustomerList(List<Customer> customers);
    
    @Delete
    Completable deleteCustomer(Customer customer);
    
    @Delete
    Completable deleteCustomerList(List<Customer> customers);
    
    @Query("DELETE FROM customers WHERE id = :id")
    Completable deleteCustomerById(long id);
    
    @Query("DELETE FROM customers WHERE phone = :phone")
    Completable deleteCustomerByPhone(String phone);
    
    @Query("DELETE FROM customers WHERE company = :company")
    Completable deleteCustomersByCompany(String company);
    
    @Query("UPDATE customers SET isFavorite = 1 WHERE id = :id")
    Completable addToFavorites(long id);
    
    @Query("UPDATE customers SET isFavorite = 0 WHERE id = :id")
    Completable removeFromFavorites(long id);
    
    @Query("UPDATE customers SET lastSeen = :timestamp WHERE id = :id")
    Completable updateLastSeen(long id, long timestamp);
    
    @Query("UPDATE customers SET lastSeen = :timestamp WHERE phone = :phone")
    Completable updateLastSeenByPhone(String phone, long timestamp);
    
    @Query("SELECT * FROM customers ORDER BY lastSeen DESC LIMIT :limit")
    Single<List<Customer>> getRecentlySeenCustomers(int limit);
    
    @Query("SELECT * FROM customers WHERE isFavorite = 1 ORDER BY lastSeen DESC LIMIT :limit")
    Single<List<Customer>> getFavoriteRecentlySeenCustomers(int limit);
    
    @Query("SELECT * FROM customers WHERE name LIKE :name || '%' ORDER BY name ASC LIMIT :limit")
    Single<List<Customer>> getCustomersByNamePrefix(String name, int limit);
    
    @Query("SELECT * FROM customers WHERE phone LIKE :phone || '%' ORDER BY phone ASC LIMIT :limit")
    Single<List<Customer>> getCustomersByPhonePrefix(String phone, int limit);
    
    @Query("SELECT COUNT(*) FROM customers WHERE lastSeen >= :startTime AND lastSeen <= :endTime")
    Single<Integer> getActiveCustomersCount(long startTime, long endTime);
    
    @Query("SELECT * FROM customers WHERE notes IS NOT NULL AND notes != '' ORDER BY name ASC")
    Single<List<Customer>> getCustomersWithNotes();
    
    @Query("SELECT * FROM customers WHERE email IS NOT NULL AND email != '' ORDER BY name ASC")
    Single<List<Customer>> getCustomersWithEmail();
    
    @Query("SELECT * FROM customers WHERE address IS NOT NULL AND address != '' ORDER BY name ASC")
    Single<List<Customer>> getCustomersWithAddress();
    
    @Query("SELECT * FROM customers ORDER BY createdAt DESC LIMIT :limit")
    Single<List<Customer>> getRecentlyAddedCustomers(int limit);
    
    @Query("SELECT * FROM customers ORDER BY updatedAt DESC LIMIT :limit")
    Single<List<Customer>> getRecentlyUpdatedCustomers(int limit);
    
    @Query("SELECT COUNT(*) FROM customers WHERE createdAt >= :timestamp")
    Flowable<Integer> getNewCustomersCountSince(long timestamp);
    
    @Query("SELECT COUNT(*) FROM customers WHERE lastSeen >= :timestamp")
    Flowable<Integer> getActiveCustomersCountSince(long timestamp);
}
