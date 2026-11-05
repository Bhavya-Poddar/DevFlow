package com.devflow.devflow.dto;

public record GitHubWebhookResponseDTO(boolean duplicate, boolean repositoryMatched, int activityCount) { }
