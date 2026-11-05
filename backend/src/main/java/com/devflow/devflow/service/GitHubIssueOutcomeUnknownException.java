package com.devflow.devflow.service;

public class GitHubIssueOutcomeUnknownException extends RuntimeException {
    public GitHubIssueOutcomeUnknownException() {
        super("The GitHub issue creation outcome could not be confirmed");
    }
}
