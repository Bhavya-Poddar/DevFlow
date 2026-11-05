package com.devflow.devflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.devflow.devflow.dto.GitHubIssueLinkResponseDTO;
import com.devflow.devflow.service.GitHubIssueClient;
import com.devflow.devflow.service.GitHubIssueCreationService;
import com.devflow.devflow.service.GitHubIssueLinkCoordinator;

@ExtendWith(MockitoExtension.class)
class GitHubIssueCreationServiceTests {
    @Mock private GitHubIssueLinkCoordinator coordinator;
    @Mock private GitHubIssueClient githubClient;

    @Test
    void createsAndPersistsRemoteIssueLink() {
        when(coordinator.prepare(5L, "manager@example.test")).thenReturn(
            new GitHubIssueLinkCoordinator.Prepared(7L, 5L, "acme", "app", "Fix login",
                "<!-- DevFlow-Task-ID:5 -->", "CREATE", 3L));
        GitHubIssueClient.RemoteIssue remote = new GitHubIssueClient.RemoteIssue(24, "Fix login",
            "https://github.com/acme/app/issues/24");
        when(githubClient.create("acme", "app", "Fix login", "<!-- DevFlow-Task-ID:5 -->")).thenReturn(remote);
        GitHubIssueLinkResponseDTO linked = org.mockito.Mockito.mock(GitHubIssueLinkResponseDTO.class);
        when(coordinator.markLinked(7L, remote, 3L)).thenReturn(linked);

        assertEquals(linked, new GitHubIssueCreationService(coordinator, githubClient)
            .createOrReconcile(5L, "manager@example.test"));
    }

    @Test
    void existingLinkReturnsWithoutMakingAnotherRemoteIssue() {
        when(coordinator.prepare(5L, "manager@example.test")).thenReturn(
            new GitHubIssueLinkCoordinator.Prepared(7L, 5L, "acme", "app", "Fix login", "", "LINKED", 3L));
        GitHubIssueLinkResponseDTO linked = org.mockito.Mockito.mock(GitHubIssueLinkResponseDTO.class);
        when(coordinator.getLink(5L)).thenReturn(Optional.of(linked));

        assertEquals(linked, new GitHubIssueCreationService(coordinator, githubClient)
            .createOrReconcile(5L, "manager@example.test"));
        verifyNoInteractions(githubClient);
    }

    @Test
    void reconcilesExistingAttemptInsteadOfBlindlyRetryingPost() {
        when(coordinator.prepare(5L, "manager@example.test")).thenReturn(
            new GitHubIssueLinkCoordinator.Prepared(7L, 5L, "acme", "app", "Fix login", "", "RECONCILE", 3L));
        when(githubClient.findByTaskMarker("acme", "app", 5L)).thenReturn(List.of());

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
            () -> new GitHubIssueCreationService(coordinator, githubClient)
                .createOrReconcile(5L, "manager@example.test"));
        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
        verify(coordinator).markState(7L, "UNKNOWN");
        org.mockito.Mockito.verify(githubClient).findByTaskMarker("acme", "app", 5L);
        org.mockito.Mockito.verify(githubClient, org.mockito.Mockito.never())
            .create(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void multipleReconciliationCandidatesRemainUnresolved() {
        when(coordinator.prepare(5L, "manager@example.test")).thenReturn(
            new GitHubIssueLinkCoordinator.Prepared(7L, 5L, "acme", "app", "Fix login", "", "RECONCILE", 3L));
        when(githubClient.findByTaskMarker("acme", "app", 5L)).thenReturn(List.of(
            new GitHubIssueClient.RemoteIssue(24, "First", "https://github.com/acme/app/issues/24"),
            new GitHubIssueClient.RemoteIssue(25, "Second", "https://github.com/acme/app/issues/25")));

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
            () -> new GitHubIssueCreationService(coordinator, githubClient)
                .createOrReconcile(5L, "manager@example.test"));

        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
        verify(coordinator).markState(7L, "UNKNOWN");
        verify(coordinator, never()).markLinked(org.mockito.ArgumentMatchers.anyLong(),
            org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void rateLimitDoesNotBecomeAnUncertainRetry() {
        when(coordinator.prepare(5L, "manager@example.test")).thenReturn(
            new GitHubIssueLinkCoordinator.Prepared(7L, 5L, "acme", "app", "Fix login", "body", "CREATE", 3L));
        when(githubClient.create("acme", "app", "Fix login", "body"))
            .thenThrow(new com.devflow.devflow.service.GitHubApiException(429, true));

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
            () -> new GitHubIssueCreationService(coordinator, githubClient)
                .createOrReconcile(5L, "manager@example.test"));
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, error.getStatusCode());
        verify(coordinator).markState(7L, "FAILED");
    }

    @Test
    void requestTimeoutIsTreatedAsAmbiguousAndRequiresReconciliation() {
        when(coordinator.prepare(5L, "manager@example.test")).thenReturn(
            new GitHubIssueLinkCoordinator.Prepared(7L, 5L, "acme", "app", "Fix login", "body", "CREATE", 3L));
        when(githubClient.create("acme", "app", "Fix login", "body"))
            .thenThrow(new com.devflow.devflow.service.GitHubApiException(408, false));

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
            () -> new GitHubIssueCreationService(coordinator, githubClient)
                .createOrReconcile(5L, "manager@example.test"));
        assertEquals(HttpStatus.BAD_GATEWAY, error.getStatusCode());
        verify(coordinator).markState(7L, "UNKNOWN");
    }
}
