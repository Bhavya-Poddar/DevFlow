package com.devflow.devflow.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** A relationship reference in the existing nested {"id": ...} API shape. */
@JsonIgnoreProperties(ignoreUnknown = true)
public class IdReferenceRequestDTO {
    private Long id;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
}
