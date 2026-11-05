package com.devflow.devflow.entity;

import java.util.Locale;

/** Approved issue lifecycle states. The database column remains a String for compatibility. */
public enum IssueStatus {
    OPEN,
    IN_PROGRESS,
    CODE_REVIEW,
    READY_FOR_TESTING,
    TESTING,
    TEAM_LEAD_APPROVAL,
    PROJECT_MANAGER_APPROVAL,
    RESOLVED;

    /** Parse an API status value without silently accepting legacy or custom values. */
    public static IssueStatus fromRequest(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Issue status cannot be blank");
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unsupported issue status: " + value, exception);
        }
    }

    /** Parse a persisted value when a transition depends on its current state. */
    public static IssueStatus fromStored(String value) {
        try {
            return valueOf(value);
        } catch (RuntimeException exception) {
            throw new IllegalStateException("Issue has a legacy or invalid stored status: " + value, exception);
        }
    }

    public boolean isManualTarget() {
        return this == OPEN || this == IN_PROGRESS || this == CODE_REVIEW || this == READY_FOR_TESTING;
    }

    public boolean isNextDeveloperStatus(IssueStatus target) {
        return switch (this) {
            case OPEN -> target == IN_PROGRESS;
            case IN_PROGRESS -> target == CODE_REVIEW;
            case CODE_REVIEW -> target == READY_FOR_TESTING;
            default -> false;
        };
    }

    /** Existing ADMIN/PROJECT_MANAGER workflow allows reopening or moving among manual states. */
    public boolean allowsManagerTransitionTo(IssueStatus target) {
        if (this == TESTING || this == TEAM_LEAD_APPROVAL || this == PROJECT_MANAGER_APPROVAL
                || this == RESOLVED || !target.isManualTarget()) return false;
        if (this.isNextDeveloperStatus(target)) return true;
        // A manager may explicitly send work back for another implementation cycle, but
        // cannot move it forward past a required development stage.
        return target == IN_PROGRESS && this != OPEN;
    }
}
