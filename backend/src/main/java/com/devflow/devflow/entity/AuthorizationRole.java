package com.devflow.devflow.entity;

import java.util.LinkedHashSet;
import java.util.Set;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "role_definition", uniqueConstraints = @UniqueConstraint(name = "uk_role_code", columnNames = "code"))
public class AuthorizationRole {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String code;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(length = 250)
    private String description;

    @Column(name = "scope_type", nullable = false, length = 20)
    private String scopeType;

    @Column(nullable = false)
    private boolean retired;

    @Column(name = "system_role", nullable = false)
    private boolean systemRole;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "role_permission",
        joinColumns = @JoinColumn(name = "role_id", nullable = false),
        inverseJoinColumns = @JoinColumn(name = "permission_key", referencedColumnName = "permission_key", nullable = false))
    private Set<PermissionDefinition> permissions = new LinkedHashSet<>();

    protected AuthorizationRole() {}

    public AuthorizationRole(String code, String name, PermissionScope scope, boolean systemRole) {
        this.code = code;
        this.name = name;
        this.scopeType = scope.name();
        this.systemRole = systemRole;
    }

    public Long getId() { return id; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getScopeType() { return scopeType; }
    public PermissionScope getScope() { return PermissionScope.valueOf(scopeType); }
    public boolean isRetired() { return retired; }
    public void setRetired(boolean retired) { this.retired = retired; }
    public boolean isSystemRole() { return systemRole; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public Set<PermissionDefinition> getPermissions() { return permissions; }
    public void setPermissions(Set<PermissionDefinition> permissions) { this.permissions = permissions; }

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() { updatedAt = LocalDateTime.now(); }
}
