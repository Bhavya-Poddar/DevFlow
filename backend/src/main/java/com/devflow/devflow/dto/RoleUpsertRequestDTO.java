package com.devflow.devflow.dto;

import java.util.Set;

import com.devflow.devflow.entity.PermissionScope;

public record RoleUpsertRequestDTO(String code, String name, PermissionScope scope, Set<String> permissions,
        String description) {
    public RoleUpsertRequestDTO(String code, String name, PermissionScope scope, Set<String> permissions) {
        this(code, name, scope, permissions, null);
    }
}
