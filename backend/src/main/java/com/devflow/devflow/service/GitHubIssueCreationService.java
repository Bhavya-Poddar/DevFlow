package com.devflow.devflow.service;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.devflow.devflow.dto.GitHubIssueLinkResponseDTO;

@Service
@ConditionalOnProperty(prefix = "devflow.github", name = "enabled", havingValue = "true")
public class GitHubIssueCreationService {
    private final GitHubIssueLinkCoordinator coordinator;
    private final GitHubIssueClient githubClient;

    public GitHubIssueCreationService(GitHubIssueLinkCoordinator coordinator, GitHubIssueClient githubClient) {
        this.coordinator = coordinator;
        this.githubClient = githubClient;
    }

    public GitHubIssueLinkResponseDTO createOrReconcile(Long issueId, String authenticatedEmail) {
        GitHubIssueLinkCoordinator.Prepared prepared = coordinator.prepare(issueId, authenticatedEmail);
        if ("LINKED".equals(prepared.action())) {
            return coordinator.getLink(issueId).orElseThrow();
        }

        if ("RECONCILE".equals(prepared.action())) {
            try {
                List<GitHubIssueClient.RemoteIssue> matches = githubClient.findByTaskMarker(
                    prepared.owner(), prepared.repository(), prepared.issueId());
                if (matches.size() == 1) {
                    return coordinator.markReconciled(prepared.linkId(), matches.get(0), prepared.actorId());
                }
            } catch (ResponseStatusException exception) {
                throw exception;
            } catch (RuntimeException exception) {
                coordinator.markState(prepared.linkId(), "UNKNOWN");
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "GitHub creation is still being reconciled. No second issue was created.");
            }
            coordinator.markState(prepared.linkId(), "UNKNOWN");
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "GitHub creation is unresolved. Check the repository for the DevFlow task marker before retrying.");
        }

        try {
            GitHubIssueClient.RemoteIssue remote = githubClient.create(prepared.owner(), prepared.repository(),
                prepared.title(), prepared.body());
            return coordinator.markLinked(prepared.linkId(), remote, prepared.actorId());
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (GitHubIssueOutcomeUnknownException exception) {
            coordinator.markState(prepared.linkId(), "UNKNOWN");
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "GitHub may have created the issue, but DevFlow could not confirm it. Reconcile before retrying.");
        } catch (GitHubApiException exception) {
            if (exception.getStatus() >= 500 || exception.getStatus() == 408) {
                coordinator.markState(prepared.linkId(), "UNKNOWN");
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "GitHub's response was uncertain. Reconcile before retrying.");
            }
            coordinator.markState(prepared.linkId(), "FAILED");
            if (exception.isRateLimited()) {
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "GitHub rate limit reached. Try again later.");
            }
            if (exception.getStatus() == 403) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "The GitHub App is not authorized to create issues in this repository.");
            }
            if (exception.getStatus() == 404) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "The configured GitHub repository is unavailable to the GitHub App.");
            }
            if (exception.getStatus() == 422) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "GitHub rejected the issue title or body.");
            }
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                "GitHub rejected the issue creation request.");
        } catch (IllegalStateException exception) {
            coordinator.markState(prepared.linkId(), "FAILED");
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "GitHub App integration is not configured correctly.");
        } catch (RuntimeException exception) {
            coordinator.markState(prepared.linkId(), "UNKNOWN");
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "GitHub may have created the issue, but DevFlow could not confirm it. Reconcile before retrying.");
        }
    }

    public java.util.Optional<GitHubIssueLinkResponseDTO> getLink(Long issueId) {
        return coordinator.getLink(issueId);
    }
}
