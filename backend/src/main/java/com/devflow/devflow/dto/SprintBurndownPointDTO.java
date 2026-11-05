package com.devflow.devflow.dto;

import java.time.LocalDate;
import java.time.Instant;

public record SprintBurndownPointDTO(
    LocalDate date,
    Instant capturedAtUtc,
    long totalIssues,
    long remainingIssues,
    double idealRemainingIssues
) {}
