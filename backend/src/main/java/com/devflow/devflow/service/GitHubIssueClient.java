package com.devflow.devflow.service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import com.devflow.devflow.config.GitHubAppProperties;

@Service
@ConditionalOnProperty(prefix = "devflow.github", name = "enabled", havingValue = "true")
public class GitHubIssueClient {
    private static final int SEARCH_PAGE_SIZE = 100;
    private static final int MAX_SEARCH_PAGES = 10;
    private static final String LEGACY_MARKER_PREFIX = "DevFlow-Task-";
    private final GitHubHttpClient httpClient;
    private final GitHubInstallationTokenProvider tokenProvider;
    private final GitHubAppProperties appProperties;

    public GitHubIssueClient(GitHubHttpClient httpClient, GitHubInstallationTokenProvider tokenProvider,
            GitHubAppProperties appProperties) {
        this.httpClient = httpClient;
        this.tokenProvider = tokenProvider;
        this.appProperties = appProperties;
    }

    public RemoteIssue create(String owner, String repository, String title, String body) {
        String token = tokenProvider.getToken();
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("title", title);
        request.put("body", body);
        Map<String, Object> response;
        try {
            response = httpClient.post(repositoryPath(owner, repository) + "/issues", token, request);
        } catch (GitHubApiException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new GitHubIssueOutcomeUnknownException();
        }
        return parseIssue(response, owner, repository);
    }

    /** Finds a prior creation using an exact first-line marker; no result is not proof POST never succeeded. */
    public List<RemoteIssue> findByTaskMarker(String owner, String repository, Long taskId) {
        String token = tokenProvider.getToken();
        Map<Long, RemoteIssue> matching = new LinkedHashMap<>();
        collectMarkerMatches(owner, repository, taskId, marker(taskId), token, matching);

        // Legacy markers are not uniquely delimited and can be ordinary user-authored text.
        // Detect them for operator reconciliation, but never link a GitHub issue based on one.
        Map<Long, RemoteIssue> legacyMatches = new LinkedHashMap<>();
        collectMarkerMatches(owner, repository, taskId, legacyMarker(taskId), token, legacyMatches);
        if (!legacyMatches.isEmpty()) {
            throw new GitHubIssueOutcomeUnknownException();
        }
        return List.copyOf(matching.values());
    }

    public static String marker(Long taskId) { return "<!-- DevFlow-Task-ID:" + taskId + " -->"; }

    private String legacyMarker(Long taskId) { return LEGACY_MARKER_PREFIX + taskId; }

    private void collectMarkerMatches(String owner, String repository, Long taskId, String candidateMarker,
            String token, Map<Long, RemoteIssue> matching) {
        String query = "repo:" + owner + "/" + repository + " in:body \"" + candidateMarker + "\"";
        Long expectedTotal = null;
        int retrieved = 0;
        for (int page = 1; page <= MAX_SEARCH_PAGES; page++) {
            String path = "/search/issues?q=" + URLEncoder.encode(query, StandardCharsets.UTF_8)
                + "&per_page=" + SEARCH_PAGE_SIZE + "&page=" + page;
            Map<String, Object> response = httpClient.get(path, token);
            if (response == null || !(response.get("total_count") instanceof Number totalNumber)
                    || !(response.get("items") instanceof List<?> items)
                    || !(response.get("incomplete_results") instanceof Boolean incomplete)) {
                throw new GitHubIssueOutcomeUnknownException();
            }
            long total = totalNumber.longValue();
            if (total < 0 || incomplete || (expectedTotal != null && expectedTotal != total)) {
                throw new GitHubIssueOutcomeUnknownException();
            }
            expectedTotal = total;
            int pageCount = (int) Math.min(SEARCH_PAGE_SIZE, total - retrieved);
            if (items.size() != pageCount) throw new GitHubIssueOutcomeUnknownException();
            for (Object raw : items) {
                if (!(raw instanceof Map<?, ?> item)) throw new GitHubIssueOutcomeUnknownException();
                Object body = item.get("body");
                if (!(body instanceof String text) || !hasExactFirstLineMarker(text, taskId, candidateMarker)) continue;
                @SuppressWarnings("unchecked") Map<String, Object> typed = (Map<String, Object>) item;
                RemoteIssue remote = parseIssue(typed, owner, repository);
                matching.putIfAbsent(remote.number(), remote);
            }
            retrieved += items.size();
            if (retrieved >= total) return;
            if (page == MAX_SEARCH_PAGES) throw new GitHubIssueOutcomeUnknownException();
        }
    }

    private boolean hasExactFirstLineMarker(String body, Long taskId, String searchedMarker) {
        int lineEnd = body.indexOf('\n');
        String firstLine = lineEnd < 0 ? body : body.substring(0, lineEnd);
        if (firstLine.endsWith("\r")) firstLine = firstLine.substring(0, firstLine.length() - 1);
        return firstLine.equals(searchedMarker)
            && (searchedMarker.equals(marker(taskId)) || searchedMarker.equals(legacyMarker(taskId)));
    }

    private RemoteIssue parseIssue(Map<String, Object> response, String owner, String repository) {
        if (response == null || !(response.get("number") instanceof Number number)
                || number.longValue() < 1 || !(response.get("html_url") instanceof String url)
                || !validIssueUrl(url, owner, repository, number.longValue())
                || !(response.get("title") instanceof String title) || title.isBlank()) {
            // A response may have been accepted by GitHub before a proxy returned a malformed response.
            throw new GitHubIssueOutcomeUnknownException();
        }
        return new RemoteIssue(number.longValue(), title, url);
    }

    private boolean validIssueUrl(String value, String owner, String repository, long issueNumber) {
        try {
            java.net.URI uri = java.net.URI.create(value);
            java.net.URI apiBase = java.net.URI.create(appProperties.getApiBaseUrl());
            String expectedHost = "api.github.com".equalsIgnoreCase(apiBase.getHost())
                ? "github.com" : apiBase.getHost();
            String[] path = uri.getRawPath().split("/", -1);
            return "https".equalsIgnoreCase(uri.getScheme())
                && expectedHost != null && expectedHost.equalsIgnoreCase(uri.getHost())
                && uri.getRawUserInfo() == null && uri.getRawQuery() == null && uri.getRawFragment() == null
                && (uri.getPort() == -1 || uri.getPort() == 443)
                && path.length == 5 && path[0].isEmpty()
                && path[1].equalsIgnoreCase(owner) && path[2].equalsIgnoreCase(repository)
                && "issues".equals(path[3]) && path[4].matches("[1-9][0-9]*")
                && Long.parseLong(path[4]) == issueNumber;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private String repositoryPath(String owner, String repository) {
        // Owner/repository are validated and normalized when the project mapping is saved.
        return "/repos/" + owner + "/" + repository;
    }

    public record RemoteIssue(long number, String title, String htmlUrl) {}
}
