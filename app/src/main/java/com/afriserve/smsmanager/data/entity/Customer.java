package com.afriserve.smsmanager.data.entity;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.ColumnInfo;
import androidx.room.Index;
import androidx.room.Ignore;

@Entity(
    tableName = "customers",
    indices = {
        @Index(value = {"phone"}, unique = true),
        @Index(value = {"lastSeen"})
    }
)
public class Customer {
    
    @PrimaryKey(autoGenerate = true)
    public long id;
    
    @ColumnInfo(name = "name")
    public String name;
    
    @ColumnInfo(name = "phone")
    public String phone;
    
    @ColumnInfo(name = "email")
    public String email;
    
    @ColumnInfo(name = "address")
    public String address;
    
    @ColumnInfo(name = "company")
    public String company;
    
    @ColumnInfo(name = "notes")
    public String notes;
    
    @ColumnInfo(name = "isFavorite")
    public boolean isFavorite = false;
    
    @ColumnInfo(name = "lastSeen")
    public Long lastSeen;
    
    @ColumnInfo(name = "createdAt")
    public long createdAt;
    
    @ColumnInfo(name = "updatedAt")
    public long updatedAt;
    
    public Customer() {
    }
    
    @Ignore
    public Customer(String name, String phone) {
        this.name = name;
        this.phone = phone;
        this.createdAt = System.currentTimeMillis();
        this.updatedAt = System.currentTimeMillis();
    }
    
    public void updateTimestamp() {
        this.updatedAt = System.currentTimeMillis();
        this.lastSeen = System.currentTimeMillis();
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        
        Customer customer = (Customer) o;
        
        if (id != customer.id) return false;
        if (isFavorite != customer.isFavorite) return false;
        if (createdAt != customer.createdAt) return false;
        if (updatedAt != customer.updatedAt) return false;
        if (name != null ? !name.equals(customer.name) : customer.name != null) return false;
        if (phone != null ? !phone.equals(customer.phone) : customer.phone != null) return false;
        if (email != null ? !email.equals(customer.email) : customer.email != null) return false;
        if (address != null ? !address.equals(customer.address) : customer.address != null) return false;
        if (company != null ? !company.equals(customer.company) : customer.company != null) return false;
        if (notes != null ? !notes.equals(customer.notes) : customer.notes != null) return false;
        return lastSeen != null ? lastSeen.equals(customer.lastSeen) : customer.lastSeen == null;
    }
    
    @Override
    public int hashCode() {
        int result = (int) (id ^ (id >>> 32));
        result = 31 * result + (name != null ? name.hashCode() : 0);
        result = 31 * result + (phone != null ? phone.hashCode() : 0);
        result = 31 * result + (email != null ? email.hashCode() : 0);
        result = 31 * result + (address != null ? address.hashCode() : 0);
        result = 31 * result + (company != null ? company.hashCode() : 0);
        result = 31 * result + (notes != null ? notes.hashCode() : 0);
        result = 31 * result + (isFavorite ? 1 : 0);
        result = 31 * result + (lastSeen != null ? lastSeen.hashCode() : 0);
        result = 31 * result + (int) (createdAt ^ (createdAt >>> 32));
        result = 31 * result + (int) (updatedAt ^ (updatedAt >>> 32));
        return result;
    }
}
