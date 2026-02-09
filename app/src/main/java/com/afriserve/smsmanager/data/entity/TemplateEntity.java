package com.afriserve.smsmanager.data.entity;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.ColumnInfo;
import androidx.room.Index;
import androidx.room.Ignore;

@Entity(
    tableName = "template_entities",
    indices = {
        @Index(value = {"category"}),
        @Index(value = {"isFavorite"}),
        @Index(value = {"usageCount"})
    }
)
public class TemplateEntity {
    
    @PrimaryKey(autoGenerate = true)
    public long id;
    
    @ColumnInfo(name = "name")
    public String name;
    
    @ColumnInfo(name = "content")
    public String content;
    
    @ColumnInfo(name = "category")
    public String category;
    
    @ColumnInfo(name = "description")
    public String description;
    
    @ColumnInfo(name = "isFavorite")
    public boolean isFavorite = false;
    
    @ColumnInfo(name = "usageCount")
    public int usageCount = 0;
    
    @ColumnInfo(name = "lastUsed")
    public Long lastUsed;
    
    @ColumnInfo(name = "variables")
    public String variables; // JSON string for template variables
    
    @ColumnInfo(name = "createdAt")
    public long createdAt;
    
    @ColumnInfo(name = "updatedAt")
    public long updatedAt;
    
    public TemplateEntity() {
    }
    
    @Ignore
    public TemplateEntity(String name, String content, String category) {
        this.name = name;
        this.content = content;
        this.category = category;
        this.createdAt = System.currentTimeMillis();
        this.updatedAt = System.currentTimeMillis();
    }
    
    public void incrementUsage() {
        this.usageCount++;
        this.lastUsed = System.currentTimeMillis();
        this.updatedAt = System.currentTimeMillis();
    }
    
    public void updateTimestamp() {
        this.updatedAt = System.currentTimeMillis();
    }
    
    public boolean isRecentlyUsed() {
        if (lastUsed == null) return false;
        long oneWeekAgo = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000L);
        return lastUsed > oneWeekAgo;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        
        TemplateEntity that = (TemplateEntity) o;
        
        if (id != that.id) return false;
        if (isFavorite != that.isFavorite) return false;
        if (usageCount != that.usageCount) return false;
        if (createdAt != that.createdAt) return false;
        if (updatedAt != that.updatedAt) return false;
        if (name != null ? !name.equals(that.name) : that.name != null) return false;
        if (content != null ? !content.equals(that.content) : that.content != null) return false;
        if (category != null ? !category.equals(that.category) : that.category != null) return false;
        if (description != null ? !description.equals(that.description) : that.description != null) return false;
        if (variables != null ? !variables.equals(that.variables) : that.variables != null) return false;
        return lastUsed != null ? lastUsed.equals(that.lastUsed) : that.lastUsed == null;
    }
    
    @Override
    public int hashCode() {
        int result = (int) (id ^ (id >>> 32));
        result = 31 * result + (name != null ? name.hashCode() : 0);
        result = 31 * result + (content != null ? content.hashCode() : 0);
        result = 31 * result + (category != null ? category.hashCode() : 0);
        result = 31 * result + (description != null ? description.hashCode() : 0);
        result = 31 * result + (isFavorite ? 1 : 0);
        result = 31 * result + usageCount;
        result = 31 * result + (lastUsed != null ? lastUsed.hashCode() : 0);
        result = 31 * result + (variables != null ? variables.hashCode() : 0);
        result = 31 * result + (int) (createdAt ^ (createdAt >>> 32));
        result = 31 * result + (int) (updatedAt ^ (updatedAt >>> 32));
        return result;
    }
}
