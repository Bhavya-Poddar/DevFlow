package com.devflow.devflow.dto;

import java.time.LocalDate;

import com.devflow.devflow.entity.Sprint;

public class SprintSummaryResponseDTO {
    private final Long id;
    private final String name;
    private final String goal;
    private final LocalDate startDate;
    private final LocalDate endDate;
    private final String status;

    public SprintSummaryResponseDTO(Sprint sprint) {
        this.id = sprint.getId();
        this.name = sprint.getName();
        this.goal = sprint.getGoal();
        this.startDate = sprint.getStartDate();
        this.endDate = sprint.getEndDate();
        this.status = sprint.getStatus();
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getGoal() { return goal; }
    public LocalDate getStartDate() { return startDate; }
    public LocalDate getEndDate() { return endDate; }
    public String getStatus() { return status; }
}
