package com.devflow.devflow.entity;

import java.time.LocalDateTime;
import jakarta.persistence.*;

@Entity
@Table(name="admin_menu_entry")
public class AdminMenuEntry {
    @Id @Column(name="route_key", nullable=false, length=64) private String routeKey;
    @Column(name="display_label", nullable=false, length=80) private String displayLabel;
    @Column(nullable=false) private boolean enabled=true;
    @Column(name="sort_order", nullable=false) private int sortOrder;
    @Column(name="required_permission", length=80) private String requiredPermission;
    @Column(name="updated_at", nullable=false) private LocalDateTime updatedAt;
    @PrePersist @PreUpdate void updateDate(){updatedAt=LocalDateTime.now();}
    public String getRouteKey(){return routeKey;} public void setRouteKey(String v){routeKey=v;} public String getDisplayLabel(){return displayLabel;}
    public void setDisplayLabel(String v){displayLabel=v;} public boolean isEnabled(){return enabled;} public void setEnabled(boolean v){enabled=v;}
    public int getSortOrder(){return sortOrder;} public void setSortOrder(int v){sortOrder=v;} public LocalDateTime getUpdatedAt(){return updatedAt;}
    public String getRequiredPermission(){return requiredPermission;} public void setRequiredPermission(String v){requiredPermission=v;}
}
