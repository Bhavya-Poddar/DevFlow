package com.devflow.devflow.service;

/** Internal routes supported by the frontend for notification navigation. */
public final class NotificationTargetPaths {
    private NotificationTargetPaths() { }

    public static String issue(Long issueId) {
        return issueId == null ? null : "/issues?issueId=" + issueId;
    }

    public static String project(Long projectId) {
        return projectId == null ? "/projects" : "/projects?projectId=" + projectId;
    }

    public static String team(Long teamId) {
        return teamId == null ? "/private-teams" : "/private-teams?teamId=" + teamId;
    }
}
