package com.devflow.devflow.dto;
public record AdminMenuEntryRequest(String displayLabel, Boolean enabled, Integer sortOrder,
        String requiredPermission) {
    public AdminMenuEntryRequest(String displayLabel, Boolean enabled, Integer sortOrder) {
        this(displayLabel, enabled, sortOrder, null);
    }
}
