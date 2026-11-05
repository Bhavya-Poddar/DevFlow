package com.devflow.devflow.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "permission_catalogue")
public class PermissionDefinition {
    @Id
    @Column(name = "permission_key", length = 80, nullable = false)
    private String key;

    @Column(nullable = false, length = 180)
    private String description;

    @Column(name = "scope_type", nullable = false, length = 20)
    private String scopeType;

    protected PermissionDefinition() {}

    public PermissionDefinition(String key, String description, PermissionScope scope) {
        this.key = key;
        this.description = description;
        this.scopeType = scope.name();
    }

    public String getKey() { return key; }
    public String getDescription() { return description; }
    public String getScopeType() { return scopeType; }
}
