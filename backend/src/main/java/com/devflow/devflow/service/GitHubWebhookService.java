package com.devflow.devflow.service;

import java.net.URI;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.devflow.devflow.dto.GitHubWebhookResponseDTO;
import com.devflow.devflow.entity.GitHubIssueActivity;
import com.devflow.devflow.entity.GitHubPullRequest;
import com.devflow.devflow.entity.GitHubWebhookDelivery;
import com.devflow.devflow.entity.Issue;
import com.devflow.devflow.entity.Project;
import com.devflow.devflow.repository.GitHubIssueActivityRepository;
import com.devflow.devflow.repository.GitHubPullRequestRepository;
import com.devflow.devflow.repository.GitHubWebhookDeliveryRepository;
import com.devflow.devflow.repository.IssueRepository;
import com.devflow.devflow.repository.ProjectRepository;
import tools.jackson.databind.JsonNode;

@Service
@ConditionalOnProperty(prefix = "devflow.github", name = "enabled", havingValue = "true")
public class GitHubWebhookService {
    private static final Pattern ISSUE_REFERENCE = Pattern.compile("(?<![A-Za-z0-9_])#([1-9][0-9]*)(?![A-Za-z0-9_])");
    private static final Set<String> PULL_REQUEST_ACTIONS = Set.of(
        "opened", "reopened", "synchronize", "closed", "edited", "ready_for_review", "converted_to_draft");

    private final ProjectRepository projectRepository;
    private final IssueRepository issueRepository;
    private final GitHubWebhookDeliveryRepository deliveryRepository;
    private final GitHubIssueActivityRepository activityRepository;
    private final GitHubPullRequestRepository pullRequestRepository;

    public GitHubWebhookService(ProjectRepository projectRepository, IssueRepository issueRepository,
            GitHubWebhookDeliveryRepository deliveryRepository, GitHubIssueActivityRepository activityRepository,
            GitHubPullRequestRepository pullRequestRepository) {
        this.projectRepository = projectRepository;
        this.issueRepository = issueRepository;
        this.deliveryRepository = deliveryRepository;
        this.activityRepository = activityRepository;
        this.pullRequestRepository = pullRequestRepository;
    }

    @Transactional
    public GitHubWebhookResponseDTO process(String eventType, String deliveryId, JsonNode payload) {
        if (deliveryId == null || deliveryId.isBlank() || deliveryId.length() > 64) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A valid GitHub delivery ID is required");
        }
        if (!"push".equals(eventType) && !"pull_request".equals(eventType)) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Unsupported GitHub event");
        }
        if (payload == null || !payload.isObject()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Malformed GitHub payload");
        }

        JsonNode repository = payload.path("repository");
        String owner = text(repository.path("owner"), "login");
        String name = text(repository, "name");
        if (owner == null || name == null) {
            String fullName = text(repository, "full_name");
            if (fullName != null && fullName.indexOf('/') > 0 && fullName.indexOf('/') == fullName.lastIndexOf('/')) {
                String[] parts = fullName.split("/", 2);
                owner = parts[0];
                name = parts[1];
            }
        }
        if (!validRepositoryPart(owner, 39) || !validRepositoryPart(name, 100)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Malformed repository identity in GitHub payload");
        }
        owner = owner.toLowerCase(Locale.ROOT);
        name = name.toLowerCase(Locale.ROOT);

        if (deliveryRepository.existsByDeliveryId(deliveryId)) {
            return new GitHubWebhookResponseDTO(true, true, 0);
        }

        JsonNode pullRequest = null;
        String action = null;
        PullRequestSnapshot pullRequestSnapshot = null;
        if ("pull_request".equals(eventType)) {
            action = text(payload, "action");
            if (!PULL_REQUEST_ACTIONS.contains(action)) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Unsupported pull request action");
            }
            pullRequest = payload.path("pull_request");
            if (!pullRequest.isObject()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Malformed pull request payload");
            }
            pullRequestSnapshot = parsePullRequest(pullRequest, action);
        }

        // Lock the mapped project while processing PR webhooks so concurrent first deliveries
        // for one repository cannot race to insert duplicate current-state records.
        Project project = ("pull_request".equals(eventType)
            ? projectRepository.findByGithubRepositoryForUpdate(owner, name)
            : projectRepository.findByGithubRepositoryOwnerIgnoreCaseAndGithubRepositoryNameIgnoreCase(owner, name))
            .orElse(null);
        if (project == null) return new GitHubWebhookResponseDTO(false, false, 0);

        GitHubWebhookDelivery delivery = new GitHubWebhookDelivery();
        delivery.setDeliveryId(deliveryId);
        delivery.setEventType(eventType);
        delivery.setRepositoryOwner(owner);
        delivery.setRepositoryName(name);
        delivery = deliveryRepository.saveAndFlush(delivery);

        int savedCount = "push".equals(eventType)
            ? saveCommits(payload, project, delivery)
            : savePullRequest(pullRequestSnapshot, project, delivery);
        return new GitHubWebhookResponseDTO(false, true, savedCount);
    }

    @Transactional(readOnly = true)
    public boolean deliveryWasProcessed(String deliveryId) {
        return deliveryRepository.existsByDeliveryId(deliveryId);
    }

    private int saveCommits(JsonNode payload, Project project, GitHubWebhookDelivery delivery) {
        JsonNode commits = payload.path("commits");
        if (!commits.isArray()) return 0;
        int saved = 0;
        for (JsonNode commit : commits) {
            String sha = text(commit, "id");
            String summary = text(commit, "message");
            String url = text(commit, "url");
            if (sha == null || !sha.matches("(?i)[0-9a-f]{40,64}") || summary == null || !validGitHubUrl(url)) continue;
            Set<Long> issueIds = issueReferences(summary);
            for (Long issueId : issueIds) {
                Issue issue = issueRepository.findByIdAndProjectId(issueId, project.getId()).orElse(null);
                if (issue == null) continue;
                GitHubIssueActivity activity = new GitHubIssueActivity();
                activity.setIssue(issue);
                activity.setDelivery(delivery);
                activity.setActivityType("COMMIT");
                activity.setExternalId(sha.toLowerCase(Locale.ROOT));
                activity.setSummary(summary);
                activity.setHtmlUrl(url);
                activity.setAuthor(text(commit.path("author"), "name"));
                activity.setEventAt(parseTimestamp(text(commit, "timestamp")));
                activityRepository.save(activity);
                saved++;
            }
        }
        return saved;
    }

    private int savePullRequest(PullRequestSnapshot snapshot, Project project, GitHubWebhookDelivery delivery) {
        GitHubPullRequest current = pullRequestRepository
            .findForUpdate(project.getGithubRepositoryOwner().toLowerCase(Locale.ROOT),
                project.getGithubRepositoryName().toLowerCase(Locale.ROOT), snapshot.number())
            .orElseGet(GitHubPullRequest::new);

        boolean stale = current.getId() != null && current.getUpdatedAt() != null
            && snapshot.updatedAt() != null && snapshot.updatedAt().isBefore(current.getUpdatedAt());
        if (!stale) {
            current.setProject(project);
            current.setRepositoryOwner(project.getGithubRepositoryOwner().toLowerCase(Locale.ROOT));
            current.setRepositoryName(project.getGithubRepositoryName().toLowerCase(Locale.ROOT));
            current.setPullRequestNumber(snapshot.number());
            current.setTitle(snapshot.title());
            current.setHtmlUrl(snapshot.url());
            current.setAuthor(snapshot.author());
            current.setHeadBranch(snapshot.headBranch());
            current.setBaseBranch(snapshot.baseBranch());
            current.setState(snapshot.state());
            current.setMerged(snapshot.merged());
            current.setCreatedAt(snapshot.createdAt());
            current.setUpdatedAt(snapshot.updatedAt());
            current.setClosedAt(snapshot.closedAt());
            current.setMergedAt(snapshot.mergedAt());

            Set<Long> issueIds = issueReferences(snapshot.title() + "\n" + snapshot.body());
            Set<Issue> linkedIssues = new LinkedHashSet<>();
            for (Long issueId : issueIds) {
                issueRepository.findByIdAndProjectId(issueId, project.getId()).ifPresent(linkedIssues::add);
            }
            current.getLinkedIssues().clear();
            current.getLinkedIssues().addAll(linkedIssues);
            current = pullRequestRepository.save(current);
        }

        Set<Long> issueIds = issueReferences(snapshot.title() + "\n" + snapshot.body());
        int saved = 0;
        for (Long issueId : issueIds) {
            Issue issue = issueRepository.findByIdAndProjectId(issueId, project.getId()).orElse(null);
            if (issue == null) continue;
            GitHubIssueActivity activity = new GitHubIssueActivity();
            activity.setIssue(issue);
            activity.setDelivery(delivery);
            activity.setActivityType("PULL_REQUEST");
            activity.setExternalId("PR-" + snapshot.number());
            activity.setSummary(snapshot.action().toUpperCase(Locale.ROOT) + ": " + snapshot.title());
            activity.setHtmlUrl(snapshot.url());
            activity.setAuthor(snapshot.author());
            activity.setEventAt(snapshot.updatedAt() == null ? snapshot.createdAt() : snapshot.updatedAt());
            activityRepository.save(activity);
            saved++;
        }
        return saved;
    }

    private PullRequestSnapshot parsePullRequest(JsonNode pullRequest, String action) {
        JsonNode numberNode = pullRequest.get("number");
        long number = numberNode != null && numberNode.isIntegralNumber() ? numberNode.asLong() : -1;
        String title = text(pullRequest, "title");
        String url = text(pullRequest, "html_url");
        String state = text(pullRequest, "state");
        JsonNode mergedNode = pullRequest.get("merged");
        if (number < 1 || title == null || title.isBlank() || title.length() > 512
                || !validGitHubUrl(url) || !("open".equals(state) || "closed".equals(state))
                || mergedNode == null || !mergedNode.isBoolean()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Malformed pull request payload");
        }
        boolean merged = mergedNode.booleanValue();
        if (("closed".equals(action) && !"closed".equals(state))
                || (("opened".equals(action) || "reopened".equals(action) || "synchronize".equals(action)
                    || "ready_for_review".equals(action) || "converted_to_draft".equals(action))
                    && !"open".equals(state))
                || (merged && !"closed".equals(state))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Inconsistent pull request state in payload");
        }

        String headBranch = text(pullRequest.path("head"), "ref");
        String baseBranch = text(pullRequest.path("base"), "ref");
        String author = text(pullRequest.path("user"), "login");
        if ((headBranch != null && headBranch.length() > 255)
                || (baseBranch != null && baseBranch.length() > 255)
                || (author != null && author.length() > 255)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Malformed pull request payload");
        }

        return new PullRequestSnapshot(number, title, text(pullRequest, "body") == null ? "" : text(pullRequest, "body"),
            url, author, headBranch, baseBranch, state, merged,
            parseOptionalTimestamp(pullRequest, "created_at"),
            parseOptionalTimestamp(pullRequest, "updated_at"),
            parseOptionalTimestamp(pullRequest, "closed_at"),
            parseOptionalTimestamp(pullRequest, "merged_at"), action);
    }

    private LocalDateTime parseOptionalTimestamp(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) return null;
        if (!value.isTextual()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Malformed pull request timestamp");
        }
        try {
            return LocalDateTime.ofInstant(Instant.parse(value.asText()), ZoneOffset.UTC);
        } catch (DateTimeParseException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Malformed pull request timestamp");
        }
    }

    private record PullRequestSnapshot(long number, String title, String body, String url, String author,
            String headBranch, String baseBranch, String state, boolean merged,
            LocalDateTime createdAt, LocalDateTime updatedAt, LocalDateTime closedAt,
            LocalDateTime mergedAt, String action) {}

    private Set<Long> issueReferences(String text) {
        Set<Long> ids = new LinkedHashSet<>();
        Matcher matcher = ISSUE_REFERENCE.matcher(text);
        while (matcher.find()) {
            try { ids.add(Long.parseLong(matcher.group(1))); }
            catch (NumberFormatException ignored) { /* Ignore out-of-range references. */ }
        }
        return ids;
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || value.isNull() || !value.isValueNode() ? null : value.asText();
    }

    private boolean validRepositoryPart(String value, int maxLength) {
        return value != null && value.length() <= maxLength && value.matches("[A-Za-z0-9][A-Za-z0-9._-]*");
    }

    private boolean validGitHubUrl(String value) {
        if (value == null || value.length() > 2048) return false;
        try {
            URI uri = URI.create(value);
            return "https".equalsIgnoreCase(uri.getScheme()) && "github.com".equalsIgnoreCase(uri.getHost());
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private LocalDateTime parseTimestamp(String value) {
        if (value == null) return null;
        try { return LocalDateTime.ofInstant(Instant.parse(value), ZoneOffset.UTC); }
        catch (DateTimeParseException exception) { return null; }
    }

    private String firstNonBlank(String first, String second) {
        return first == null || first.isBlank() ? second : first;
    }
}
