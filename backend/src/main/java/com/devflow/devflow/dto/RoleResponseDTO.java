package com.devflow.devflow.dto;

import java.util.Set;
import java.util.TreeSet;
import com.devflow.devflow.entity.AuthorizationRole;

public record RoleResponseDTO(Long id, String code, String name, String scope, boolean retired,
        boolean systemRole, Set<String> permissions, java.time.LocalDateTime createdAt,
        java.time.LocalDateTime updatedAt, String description, long globalUserCount,
        long projectAssignmentCount, long teamAssignmentCount) {
    public RoleResponseDTO(AuthorizationRole role) {
        this(role.getId(), role.getCode(), role.getName(), role.getScopeType(), role.isRetired(),
            role.isSystemRole(), role.getPermissions().stream().map(p -> p.getKey())
                .filter(key -> !"ROLE_MANAGE".equals(key))
                .collect(java.util.stream.Collectors.toCollection(TreeSet::new)),
            role.getCreatedAt(), role.getUpdatedAt(), role.getDescription(), 0, 0, 0);
    }
}
