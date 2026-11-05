package com.devflow.devflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.devflow.devflow.config.GitHubAppProperties;
import com.devflow.devflow.service.GitHubApiException;
import com.devflow.devflow.service.GitHubHttpClient;
import com.devflow.devflow.service.GitHubInstallationTokenProvider;
import com.devflow.devflow.service.GitHubIssueClient;
import com.devflow.devflow.service.GitHubIssueOutcomeUnknownException;

@ExtendWith(MockitoExtension.class)
class GitHubIssueClientTests {
    @Mock private GitHubHttpClient httpClient;
    @Mock private GitHubInstallationTokenProvider tokenProvider;

    @Test
    void createsIssueInMappedRepositoryWithRequestedTitleAndStableDelimitedTaskMarker() {
        when(tokenProvider.getToken()).thenReturn("test-installation-token");
        when(httpClient.post(eq("/repos/acme/app/issues"), eq("test-installation-token"), anyMap()))
            .thenReturn(issue(12, "Login fails", "https://github.com/acme/app/issues/12"));

        GitHubIssueClient.RemoteIssue result = client().create("acme", "app", "Login fails",
            GitHubIssueClient.marker(45L) + "\n\nDetails");

        assertEquals(12L, result.number());
        assertEquals("https://github.com/acme/app/issues/12", result.htmlUrl());
        verify(httpClient).post(eq("/repos/acme/app/issues"), eq("test-installation-token"),
            org.mockito.ArgumentMatchers.argThat(body -> "Login fails".equals(body.get("title"))
                && ((String) body.get("body")).startsWith("<!-- DevFlow-Task-ID:45 -->")));
    }

    @Test
    void rejectsResponseForAnotherRepositoryAsUncertain() {
        when(tokenProvider.getToken()).thenReturn("test-token");
        when(httpClient.post(eq("/repos/acme/app/issues"), eq("test-token"), anyMap()))
            .thenReturn(Map.of("number", 12, "title", "Login fails",
                "html_url", "https://github.com/other/repo/issues/12"));
        assertThrows(GitHubIssueOutcomeUnknownException.class,
            () -> client().create("acme", "app", "Login fails", "body"));
    }

    @Test
    void acceptsCanonicalRepositoryCapitalizationInReturnedGitHubUrl() {
        when(tokenProvider.getToken()).thenReturn("test-token");
        when(httpClient.post(eq("/repos/acme/app/issues"), eq("test-token"), anyMap()))
            .thenReturn(remoteIssue(12, "Login fails", "https://github.com/Acme/App/issues/12"));

        assertEquals("https://github.com/Acme/App/issues/12",
            client().create("acme", "app", "Login fails", "body").htmlUrl());
    }

    @Test
    void rejectsResponseWhenIssueUrlNumberDiffersFromReturnedIssueNumber() {
        when(tokenProvider.getToken()).thenReturn("test-token");
        when(httpClient.post(eq("/repos/acme/app/issues"), eq("test-token"), anyMap()))
            .thenReturn(remoteIssue(12, "Login fails", "https://github.com/acme/app/issues/13"));

        assertThrows(GitHubIssueOutcomeUnknownException.class,
            () -> client().create("acme", "app", "Login fails", "body"));
    }

    @Test
    void acceptsEnterpriseIssueUrlOnConfiguredApiHostAndExpectedRepositoryPath() {
        when(tokenProvider.getToken()).thenReturn("test-token");
        when(httpClient.post(eq("/repos/acme/app/issues"), eq("test-token"), anyMap()))
            .thenReturn(remoteIssue(12, "Login fails", "https://github.enterprise.example/acme/app/issues/12"));

        GitHubAppProperties properties = new GitHubAppProperties();
        properties.setApiBaseUrl("https://github.enterprise.example/api/v3");
        properties.setAllowedEnterpriseHosts(List.of("github.enterprise.example"));
        GitHubIssueClient enterpriseClient = new GitHubIssueClient(httpClient, tokenProvider, properties);

        assertEquals("https://github.enterprise.example/acme/app/issues/12",
            enterpriseClient.create("acme", "app", "Login fails", "body").htmlUrl());
    }

    @Test
    void rejectsIssueUrlFromAnotherHostEvenWhenRepositoryPathMatches() {
        when(tokenProvider.getToken()).thenReturn("test-token");
        when(httpClient.post(eq("/repos/acme/app/issues"), eq("test-token"), anyMap()))
            .thenReturn(remoteIssue(12, "Login fails", "https://attacker.example/acme/app/issues/12"));

        assertThrows(GitHubIssueOutcomeUnknownException.class,
            () -> client().create("acme", "app", "Login fails", "body"));
    }

    @Test
    void reconciliationMatchesTheExactDelimitedFirstLine() {
        when(tokenProvider.getToken()).thenReturn("test-token");
        stubSearch((query, page) -> query.contains("DevFlow-Task-ID%3A45") && page == 1
            ? List.of(issue(12, "DevFlow task", GitHubIssueClient.marker(45L) + "\nDetails")) : List.of());

        List<GitHubIssueClient.RemoteIssue> found = client().findByTaskMarker("acme", "app", 45L);

        assertEquals(1, found.size());
        assertEquals(12L, found.get(0).number());
    }

    @Test
    void taskFourDoesNotMatchTaskFortyFiveMarker() {
        when(tokenProvider.getToken()).thenReturn("test-token");
        stubSearch((query, page) -> page == 1
            ? List.of(issue(45, "Task 45", GitHubIssueClient.marker(45L) + "\nDetails")) : List.of());

        assertEquals(List.of(), client().findByTaskMarker("acme", "app", 4L));
    }

    @Test
    void userSuppliedTextContainingMarkerLikeLinesDoesNotMatch() {
        when(tokenProvider.getToken()).thenReturn("test-token");
        stubSearch((query, page) -> page == 1 ? List.of(
            issue(12, "Other task", GitHubIssueClient.marker(45L) + "\n" + GitHubIssueClient.marker(4L)),
            issue(13, "Description text", "Description\n" + GitHubIssueClient.marker(4L))) : List.of());

        assertEquals(List.of(), client().findByTaskMarker("acme", "app", 4L));
    }

    @Test
    void exactLegacyMarkerOnFirstLineRemainsUnresolvedForManualReconciliation() {
        when(tokenProvider.getToken()).thenReturn("test-token");
        stubSearch((query, page) -> query.contains("DevFlow-Task-4") && page == 1
            ? List.of(issue(12, "Legacy task", "DevFlow-Task-4\n\nOld details")) : List.of());

        assertThrows(GitHubIssueOutcomeUnknownException.class,
            () -> client().findByTaskMarker("acme", "app", 4L));
    }

    @Test
    void reconciliationScansAdditionalSearchPagesBeforeReturningUniqueMatch() {
        when(tokenProvider.getToken()).thenReturn("test-token");
        List<Map<String, Object>> firstPage = new ArrayList<>();
        for (int i = 1; i <= 100; i++) firstPage.add(issue(i, "Unrelated", "no marker"));
        when(httpClient.get(anyString(), eq("test-token"))).thenAnswer(call -> {
            String path = call.getArgument(0);
            String decoded = URLDecoder.decode(path, StandardCharsets.UTF_8);
            if (decoded.contains("DevFlow-Task-ID:45")) {
                return searchResult(101, decoded.contains("page=2")
                    ? List.of(issue(101, "Task 45", GitHubIssueClient.marker(45L))) : firstPage);
            }
            return searchResult(0, List.of());
        });

        List<GitHubIssueClient.RemoteIssue> found = client().findByTaskMarker("acme", "app", 45L);

        assertEquals(1, found.size());
        assertEquals(101L, found.get(0).number());
        verify(httpClient).get(org.mockito.ArgumentMatchers.contains("page=2"), eq("test-token"));
    }

    @Test
    void incompleteSearchResultsFailClosed() {
        when(tokenProvider.getToken()).thenReturn("test-token");
        when(httpClient.get(anyString(), eq("test-token"))).thenReturn(Map.of(
            "total_count", 1, "incomplete_results", true, "items", List.of()));

        assertThrows(GitHubIssueOutcomeUnknownException.class,
            () -> client().findByTaskMarker("acme", "app", 4L));
    }

    @Test
    void apiErrorsRetainOnlyStatusAndRateLimitClassification() {
        when(tokenProvider.getToken()).thenReturn("test-token");
        when(httpClient.post(eq("/repos/acme/app/issues"), eq("test-token"), anyMap()))
            .thenThrow(new GitHubApiException(403, true));
        GitHubApiException exception = assertThrows(GitHubApiException.class,
            () -> client().create("acme", "app", "Title", "body"));
        assertEquals(403, exception.getStatus());
        org.junit.jupiter.api.Assertions.assertTrue(exception.isRateLimited());
    }

    private GitHubIssueClient client() {
        return new GitHubIssueClient(httpClient, tokenProvider, new GitHubAppProperties());
    }

    private void stubSearch(SearchAnswer answer) {
        when(httpClient.get(anyString(), eq("test-token"))).thenAnswer(call -> {
            String path = call.getArgument(0);
            String query = path.substring(path.indexOf("q=") + 2, path.indexOf("&per_page="));
            int page = Integer.parseInt(path.substring(path.indexOf("&page=") + 6));
            List<Map<String, Object>> items = answer.items(query, page);
            return searchResult(items.size(), items);
        });
    }

    private Map<String, Object> searchResult(int total, List<Map<String, Object>> items) {
        return Map.of("total_count", total, "incomplete_results", false, "items", items);
    }

    private Map<String, Object> issue(int number, String title, String body) {
        return Map.of("number", number, "title", title, "body", body,
            "html_url", "https://github.com/acme/app/issues/" + number);
    }

    private Map<String, Object> remoteIssue(int number, String title, String url) {
        return Map.of("number", number, "title", title, "html_url", url);
    }

    @FunctionalInterface private interface SearchAnswer {
        List<Map<String, Object>> items(String query, int page);
    }
}
