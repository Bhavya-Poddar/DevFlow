package com.devflow.devflow.service;

public class GitHubApiException extends RuntimeException {
    private final int status;
    private final boolean rateLimited;

    public GitHubApiException(int status, boolean rateLimited) {
        super("GitHub API request failed");
        this.status = status;
        this.rateLimited = rateLimited;
    }

    public int getStatus() { return status; }
    public boolean isRateLimited() { return rateLimited; }
}
