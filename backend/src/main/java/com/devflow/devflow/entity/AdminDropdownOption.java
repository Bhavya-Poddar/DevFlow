package com.devflow.devflow.entity;

import java.time.LocalDateTime;
import jakarta.persistence.*;

@Entity
@Table(name = "admin_dropdown_option",
    uniqueConstraints = @UniqueConstraint(name = "uk_admin_dropdown_option", columnNames = {"category", "code"}),
    indexes = @Index(name = "ix_admin_dropdown_category_order", columnList = "category, enabled, sort_order"))
public class AdminDropdownOption {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, length = 40) private String category;
    @Column(nullable = false, length = 64) private String code;
    @Column(name = "display_label", nullable = false, length = 120) private String displayLabel;
    @Column(nullable = false) private boolean enabled = true;
    @Column(name = "sort_order", nullable = false) private int sortOrder;
    @Column(name = "created_at", nullable = false, updatable = false) private LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false) private LocalDateTime updatedAt;
    @PrePersist void createDates() { var now=LocalDateTime.now(); if(createdAt==null) createdAt=now; updatedAt=now; }
    @PreUpdate void updateDate() { updatedAt=LocalDateTime.now(); }
    public Long getId(){return id;} public String getCategory(){return category;} public void setCategory(String v){category=v;}
    public String getCode(){return code;} public void setCode(String v){code=v;} public String getDisplayLabel(){return displayLabel;}
    public void setDisplayLabel(String v){displayLabel=v;} public boolean isEnabled(){return enabled;} public void setEnabled(boolean v){enabled=v;}
    public int getSortOrder(){return sortOrder;} public void setSortOrder(int v){sortOrder=v;} public LocalDateTime getCreatedAt(){return createdAt;}
    public LocalDateTime getUpdatedAt(){return updatedAt;}
}
