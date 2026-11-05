package com.devflow.devflow.dto;

import java.util.List;

/** Describes an actual application selector and the safe level of administration it supports. */
public record AdminDropdownCategoryDTO(String category, String module, String displayName,
        String description, String managementMode, List<AdminDropdownOptionDTO> options) {}
