package com.devflow.devflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

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
import com.devflow.devflow.service.GitHubWebhookService;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@TestPropertySource(properties = "devflow.github.enabled=true")
class GitHubWebhookIntegrationTests {
    @Autowired private GitHubWebhookService webhookService;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private IssueRepository issueRepository;
    @Autowired private GitHubWebhookDeliveryRepository deliveryRepository;
    @Autowired private GitHubIssueActivityRepository activityRepository;
    @Autowired private GitHubPullRequestRepository pullRequestRepository;
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    private final List<Project> projects = new ArrayList<>();
    private final List<Issue> issues = new ArrayList<>();
    private final List<String> deliveryIds = new ArrayList<>();
    private String repositoryName;
    private Project linkedProject;
    private Issue linkedIssue;
    private Issue differentProjectIssue;

    @BeforeEach
    void createTestProjectAndIssues() {
        repositoryName = "app-" + UUID.randomUUID().toString().substring(0, 8);
        linkedProject = saveProject("devflow-test-org", repositoryName);
        Project otherProject = saveProject("other-org", "other-app-" + UUID.randomUUID().toString().substring(0, 8));
        linkedIssue = saveIssue(linkedProject, "linked issue");
        differentProjectIssue = saveIssue(otherProject, "different project issue");
    }

    @AfterEach
    void cleanTestRows() {
        pullRequestRepository.deleteAll();
        activityRepository.deleteAll(issues.stream()
            .flatMap(issue -> activityRepository.findByIssueIdOrderByEventAtDescIdDesc(issue.getId()).stream()).toList());
        deliveryIds.forEach(id -> deliveryRepository.findByDeliveryId(id).ifPresent(deliveryRepository::delete));
        issueRepository.deleteAll(issues);
        projectRepository.deleteAll(projects);
    }

    @Test
    void pushActivityIsScopedToMappedProjectAndDeliveryRetriesAreIdempotent() throws Exception {
        String deliveryId = "push-" + UUID.randomUUID();
        deliveryIds.add(deliveryId);
        String body = "{\"repository\":{\"owner\":{\"login\":\"DEVFLOW-TEST-ORG\"},\"name\":\""
            + repositoryName + "\"},\"commits\":["
            + "{\"id\":\"" + "a".repeat(40) + "\",\"message\":\"Fix #" + linkedIssue.getId()
            + "\",\"url\":\"https://github.com/devflow-test-org/" + repositoryName
            + "/commit/" + "a".repeat(40) + "\",\"timestamp\":\"2026-10-15T12:00:00Z\"},"
            + "{\"id\":\"" + "b".repeat(40) + "\",\"message\":\"Other #" + differentProjectIssue.getId()
            + "\",\"url\":\"https://github.com/devflow-test-org/" + repositoryName
            + "/commit/" + "b".repeat(40) + "\"}]}";

        var first = webhookService.process("push", deliveryId, jsonMapper.readTree(body));
        var retry = webhookService.process("push", deliveryId, jsonMapper.readTree(body));

        assertTrue(first.repositoryMatched());
        assertFalse(first.duplicate());
        assertEquals(1, first.activityCount());
        assertTrue(retry.duplicate());
        assertEquals(1, activityRepository.findByIssueIdOrderByEventAtDescIdDesc(linkedIssue.getId()).size());
        assertTrue(activityRepository.findByIssueIdOrderByEventAtDescIdDesc(differentProjectIssue.getId()).isEmpty());
    }

    @Test
    void pullRequestEventStoresSupportedActionAndRejectsUnsupportedAction() throws Exception {
        String deliveryId = "pr-" + UUID.randomUUID();
        deliveryIds.add(deliveryId);
        String payload = pullRequestPayload("closed", 12, "closed", false, linkedIssue.getId(), "2026-10-15T12:00:00Z");

        var result = webhookService.process("pull_request", deliveryId, jsonMapper.readTree(payload));
        GitHubIssueActivity activity = activityRepository
            .findByIssueIdOrderByEventAtDescIdDesc(linkedIssue.getId()).get(0);
        assertEquals(1, result.activityCount());
        assertEquals("PULL_REQUEST", activity.getActivityType());
        assertTrue(activity.getSummary().startsWith("CLOSED:"));
        assertEquals("contributor", activity.getAuthor());
        GitHubPullRequest tracked = pullRequestRepository.findAll().get(0);
        assertEquals("closed", tracked.getState());
        assertFalse(tracked.isMerged());
        assertEquals("feature/test", tracked.getHeadBranch());
        assertEquals("main", tracked.getBaseBranch());
        assertEquals("contributor", tracked.getAuthor());

        ResponseStatusException unsupported = assertThrows(ResponseStatusException.class,
            () -> webhookService.process("pull_request", "bad-action-" + UUID.randomUUID(),
                jsonMapper.readTree(payload.replace("\"action\":\"closed\"", "\"action\":\"unknown\""))));
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, unsupported.getStatusCode());
    }

    @Test
    void pullRequestActionsUpsertOneRecordAndDuplicateDeliveryIsIdempotent() throws Exception {
        long issueId = linkedIssue.getId();
        String openedId = "pr-open-" + UUID.randomUUID();
        deliveryIds.add(openedId);
        var opened = webhookService.process("pull_request", openedId,
            jsonMapper.readTree(pullRequestPayload("opened", 21, "open", false, issueId, "2026-10-15T10:00:00Z")));
        assertEquals(1, opened.activityCount());
        var duplicate = webhookService.process("pull_request", openedId,
            jsonMapper.readTree(pullRequestPayload("opened", 21, "open", false, issueId, "2026-10-15T10:00:00Z")));
        assertTrue(duplicate.duplicate());

        for (String action : List.of("synchronize", "reopened")) {
            String id = "pr-" + action + "-" + UUID.randomUUID();
            deliveryIds.add(id);
            webhookService.process("pull_request", id, jsonMapper.readTree(pullRequestPayload(
                action, 21, "open", false, issueId, action.equals("synchronize")
                    ? "2026-10-15T11:00:00Z" : "2026-10-15T12:00:00Z")));
        }

        String closedId = "pr-close-" + UUID.randomUUID();
        deliveryIds.add(closedId);
        webhookService.process("pull_request", closedId, jsonMapper.readTree(pullRequestPayload(
            "closed", 21, "closed", false, issueId, "2026-10-15T13:00:00Z")));

        List<GitHubPullRequest> records = pullRequestRepository.findAll();
        assertEquals(1, records.size());
        assertEquals("closed", records.get(0).getState());
        assertFalse(records.get(0).isMerged());
        assertEquals(4, activityRepository.findByIssueIdOrderByEventAtDescIdDesc(issueId).size());
    }

    @Test
    void mergedClosedPullRequestIsDistinguishedFromClosedWithoutMerge() throws Exception {
        String id = "pr-merged-" + UUID.randomUUID();
        deliveryIds.add(id);
        webhookService.process("pull_request", id, jsonMapper.readTree(pullRequestPayload(
            "closed", 22, "closed", true, linkedIssue.getId(), "2026-10-15T14:00:00Z")));
        GitHubPullRequest tracked = pullRequestRepository.findAll().get(0);
        assertTrue(tracked.isMerged());
        assertEquals("closed", tracked.getState());
        assertEquals(java.time.LocalDateTime.of(2026, 10, 15, 14, 0), tracked.getMergedAt());
    }

    @Test
    void malformedPullRequestAndTimestampsAreRejectedWithoutSavingCurrentState() throws Exception {
        String id = "pr-malformed-" + UUID.randomUUID();
        String valid = pullRequestPayload("opened", 23, "open", false, linkedIssue.getId(), "2026-10-15T10:00:00Z");
        ResponseStatusException missingMerged = assertThrows(ResponseStatusException.class,
            () -> webhookService.process("pull_request", id, jsonMapper.readTree(valid.replace(",\"merged\":false", ""))));
        assertEquals(HttpStatus.BAD_REQUEST, missingMerged.getStatusCode());

        ResponseStatusException invalidTimestamp = assertThrows(ResponseStatusException.class,
            () -> webhookService.process("pull_request", id, jsonMapper.readTree(valid.replace("2026-10-15T10:00:00Z", "yesterday"))));
        assertEquals(HttpStatus.BAD_REQUEST, invalidTimestamp.getStatusCode());

        ResponseStatusException inconsistentState = assertThrows(ResponseStatusException.class,
            () -> webhookService.process("pull_request", id, jsonMapper.readTree(
                pullRequestPayload("opened", 23, "closed", false, linkedIssue.getId(), "2026-10-15T10:00:00Z"))));
        assertEquals(HttpStatus.BAD_REQUEST, inconsistentState.getStatusCode());
        assertTrue(pullRequestRepository.findAll().isEmpty());
    }

    @Test
    void unknownRepositoryAndCrossProjectIssueReferencesDoNotCreateTaskLinks() throws Exception {
        String unknownId = "pr-unmapped-" + UUID.randomUUID();
        var unknown = webhookService.process("pull_request", unknownId, jsonMapper.readTree(
            pullRequestPayloadFor("unmapped-org", "unmapped-repo", "opened", 24, "open", false,
                linkedIssue.getId(), "2026-10-15T10:00:00Z")));
        assertFalse(unknown.repositoryMatched());
        assertTrue(pullRequestRepository.findAll().isEmpty());

        String crossId = "pr-cross-" + UUID.randomUUID();
        deliveryIds.add(crossId);
        webhookService.process("pull_request", crossId, jsonMapper.readTree(pullRequestPayload(
            "opened", 25, "open", false, differentProjectIssue.getId(), "2026-10-15T10:00:00Z")));
        assertEquals(1, pullRequestRepository.findAll().size());
        assertTrue(pullRequestRepository.findDistinctByLinkedIssues_IdOrderByUpdatedAtDescIdDesc(
            differentProjectIssue.getId()).isEmpty());

        String invalidIssueId = "pr-invalid-task-" + UUID.randomUUID();
        deliveryIds.add(invalidIssueId);
        webhookService.process("pull_request", invalidIssueId, jsonMapper.readTree(pullRequestPayload(
            "opened", 26, "open", false, Long.MAX_VALUE, "2026-10-15T10:00:00Z")));
        assertEquals(2, pullRequestRepository.findAll().size());
        assertTrue(pullRequestRepository.findDistinctByLinkedIssues_IdOrderByUpdatedAtDescIdDesc(
            linkedIssue.getId()).stream().noneMatch(pr -> pr.getPullRequestNumber() == 26L));
    }

    private String pullRequestPayload(String action, long number, String state, boolean merged,
            long issueId, String updatedAt) {
        return pullRequestPayload("devflow-test-org", repositoryName, action, number, state, merged, issueId, updatedAt);
    }

    private String pullRequestPayloadFor(String owner, String repo, String action, long number, String state,
            boolean merged, long issueId, String updatedAt) {
        return pullRequestPayload(owner, repo, action, number, state, merged, issueId, updatedAt);
    }

    private String pullRequestPayload(String owner, String repo, String action, long number, String state,
            boolean merged, long issueId, String updatedAt) {
        String mergedAt = merged ? "\"merged_at\":\"" + updatedAt + "\"," : "\"merged_at\":null,";
        String closedAt = "closed".equals(state) ? "\"closed_at\":\"" + updatedAt + "\"," : "\"closed_at\":null,";
        return "{\"action\":\"" + action + "\",\"repository\":{\"owner\":{\"login\":\""
            + owner + "\"},\"name\":\"" + repo + "\"},\"pull_request\":{\"number\":" + number
            + ",\"title\":\"Fix #" + issueId + "\",\"body\":\"\",\"html_url\":\"https://github.com/"
            + owner + "/" + repo + "/pull/" + number + "\",\"state\":\"" + state + "\",\"merged\":" + merged
            + ",\"created_at\":\"2026-10-15T09:00:00Z\",\"updated_at\":\"" + updatedAt + "\"," + closedAt
            + mergedAt + "\"user\":{\"login\":\"contributor\"},\"head\":{\"ref\":\"feature/test\"},"
            + "\"base\":{\"ref\":\"main\"}}}";
    }

    @Test
    void unsupportedEventAndUnmappedRepositoryAreHandledWithoutCreatingActivity() throws Exception {
        ResponseStatusException unsupported = assertThrows(ResponseStatusException.class,
            () -> webhookService.process("issues", "unsupported-" + UUID.randomUUID(),
                jsonMapper.readTree("{}")));
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, unsupported.getStatusCode());

        String deliveryId = "unmapped-" + UUID.randomUUID();
        var result = webhookService.process("push", deliveryId, jsonMapper.readTree(
            "{\"repository\":{\"owner\":{\"login\":\"unmapped-org\"},\"name\":\"unmapped-repo\"},\"commits\":[]}"));
        assertFalse(result.repositoryMatched());
        assertTrue(activityRepository.findByIssueIdOrderByEventAtDescIdDesc(linkedIssue.getId()).isEmpty());
        assertFalse(deliveryRepository.existsByDeliveryId(deliveryId));
    }

    private Project saveProject(String owner, String name) {
        Project project = new Project();
        project.setName(name);
        project.setGithubRepositoryOwner(owner.toLowerCase(java.util.Locale.ROOT));
        project.setGithubRepositoryName(name.toLowerCase(java.util.Locale.ROOT));
        projects.add(projectRepository.saveAndFlush(project));
        return projects.get(projects.size() - 1);
    }

    private Issue saveIssue(Project project, String title) {
        Issue issue = new Issue();
        issue.setProject(project);
        issue.setTitle(title);
        issue.setStatus("OPEN");
        issues.add(issueRepository.saveAndFlush(issue));
        return issues.get(issues.size() - 1);
    }
}
