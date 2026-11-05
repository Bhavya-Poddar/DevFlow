package com.devflow.devflow.dto;
import java.time.LocalDateTime;
public record AdminMenuEntryDTO(String routeKey, String displayLabel, boolean enabled, int sortOrder,
        LocalDateTime updatedAt, String requiredPermission) {
    public AdminMenuEntryDTO(String routeKey, String displayLabel, boolean enabled, int sortOrder,
            LocalDateTime updatedAt) {
        this(routeKey, displayLabel, enabled, sortOrder, updatedAt, null);
    }
}
