package com.devflow.devflow.dto;
public record AdminDropdownOptionDTO(Long id, String category, String code, String displayLabel, boolean enabled,
        int sortOrder, long usageCount, boolean usageTracked) {
    /** Backward-compatible constructor for existing callers that supplied usage counts only. */
    public AdminDropdownOptionDTO(Long id, String category, String code, String displayLabel, boolean enabled,
            int sortOrder, long usageCount) {
        this(id, category, code, displayLabel, enabled, sortOrder, usageCount, true);
    }
}
