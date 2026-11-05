package com.devflow.devflow.dto;

import java.util.List;
import java.util.Set;

public record EffectivePermissionsResponseDTO(Set<String> global,
        List<ScopedPermissions> projects, List<ScopedPermissions> teams, boolean recoveryCapable) {
    public record ScopedPermissions(Long scopeId, String roleCode, Set<String> permissions) {}
}
