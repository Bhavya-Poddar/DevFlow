package com.devflow.devflow.dto;

import java.time.LocalDate;
import java.util.List;

public record SprintBurndownResponseDTO(
    Long sprintId,
    String sprintName,
    LocalDate sprintStartDate,
    LocalDate sprintEndDate,
    LocalDate trackingStartedOn,
    Long baselineRemainingIssues,
    List<SprintBurndownPointDTO> snapshots
) {}
