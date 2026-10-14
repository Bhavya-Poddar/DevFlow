package com.devflow.devflow.dto;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Partial update input. Omitted fields keep their existing values. */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProjectUpdateRequestDTO {
    private String name;
    private String description;
    private String status;
    private LocalDate startDate;
    private LocalDate endDate;
    private final Set<String> suppliedFields = new HashSet<>();

    public String getName() { return name; }
    public void setName(String name) { this.name = name; suppliedFields.add("name"); }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; suppliedFields.add("description"); }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; suppliedFields.add("status"); }
    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; suppliedFields.add("startDate"); }
    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; suppliedFields.add("endDate"); }

    @JsonIgnore public boolean hasName() { return suppliedFields.contains("name"); }
    @JsonIgnore public boolean hasDescription() { return suppliedFields.contains("description"); }
    @JsonIgnore public boolean hasStatus() { return suppliedFields.contains("status"); }
    @JsonIgnore public boolean hasStartDate() { return suppliedFields.contains("startDate"); }
    @JsonIgnore public boolean hasEndDate() { return suppliedFields.contains("endDate"); }
}
