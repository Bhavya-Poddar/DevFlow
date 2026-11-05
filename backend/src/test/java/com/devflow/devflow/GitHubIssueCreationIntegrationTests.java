package com.devflow.devflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import com.devflow.devflow.entity.GitHubIssueLink;
import com.devflow.devflow.entity.Issue;
import com.devflow.devflow.entity.Project;
import com.devflow.devflow.entity.User;
import com.devflow.devflow.repository.AuditLogsRepository;
import com.devflow.devflow.repository.GitHubIssueLinkRepository;
import com.devflow.devflow.repository.IssueRepository;
import com.devflow.devflow.repository.ProjectRepository;
import com.devflow.devflow.repository.UserRepository;
import com.devflow.devflow.security.JwtService;
import com.devflow.devflow.service.GitHubIssueClient;
import com.devflow.devflow.service.GitHubIssueCreationService;
import com.devflow.devflow.service.GitHubIssueLinkCoordinator;
import com.devflow.devflow.service.GitHubIssueOutcomeUnknownException;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "devflow.github.enabled=true")
class GitHubIssueCreationIntegrationTests {
    @Autowired private MockMvc mockMvc;
    @Autowired private JwtService jwtService;
    @Autowired private GitHubIssueCreationService creationService;
    @Autowired private GitHubIssueLinkCoordinator linkCoordinator;
    @Autowired private GitHubIssueLinkRepository linkRepository;
    @Autowired private IssueRepository issueRepository;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private AuditLogsRepository auditLogsRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @MockitoBean private GitHubIssueClient githubClient;

    private User actor;
    private Project project;
    private Issue issue;
    private String owner;
    private String repository;
    private String email;

    @BeforeEach
    void createTaskAndMappedProject() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        owner = "devflow-test-" + suffix.substring(0, 8);
        repository = "app-" + suffix;
        email = "github-issue-" + suffix + "@example.test";
        actor = new User();
        actor.setName("GitHub integration test");
        actor.setEmail(email);
        actor.setPassword("test-only-hash");
        actor.setRole("ADMIN");
        actor = userRepository.save(actor);

        project = new Project();
        project.setName("GitHub issue test project");
        project.setStatus("ACTIVE");
        project.setGithubRepositoryOwner(owner);
        project.setGithubRepositoryName(repository);
        project = projectRepository.save(project);

        issue = new Issue();
        issue.setTitle("A test task");
        issue.setDescription("Task description");
        issue.setAcceptanceCriteria("Acceptance checks");
        issue.setStatus("OPEN");
        issue.setProject(project);
        issue.setReportedBy(actor);
        issue = issueRepository.save(issue);
    }

    @AfterEach
    void deleteOnlyFixtureRows() {
        if (issue != null && issue.getId() != null) {
            linkRepository.findByIssue_Id(issue.getId()).ifPresent(linkRepository::delete);
            auditLogsRepository.deleteAll(auditLogsRepository.findByIssueIdOrderByCreatedAtAsc(issue.getId()));
            issueRepository.deleteById(issue.getId());
        }
        if (project != null && project.getId() != null) projectRepository.deleteById(project.getId());
        if (actor != null && actor.getId() != null) userRepository.deleteById(actor.getId());
    }

    @Test
    void successfulCreationIsPersistedAndRepeatedRequestsReturnTheSameLink() {
        GitHubIssueClient.RemoteIssue remote = new GitHubIssueClient.RemoteIssue(27, "A test task",
            "https://github.com/" + owner + "/" + repository + "/issues/27");
        when(githubClient.create(eq(owner), eq(repository), eq("A test task"), anyString())).thenReturn(remote);

        var created = creationService.createOrReconcile(issue.getId(), email);
        var repeated = creationService.createOrReconcile(issue.getId(), email);

        assertEquals("LINKED", created.getState());
        assertEquals(27L, created.getGithubIssueNumber());
        assertEquals(created.getHtmlUrl(), repeated.getHtmlUrl());
        assertTrue(linkRepository.findByIssue_Id(issue.getId()).isPresent());
        verify(githubClient, times(1)).create(eq(owner), eq(repository), eq("A test task"), anyString());
    }

    @Test
    void retiredGithubIssueHttpRouteIsNotRegistered() throws Exception {
        String authorization = "Bearer " + jwtService.generateToken(email, "ADMIN");
        mockMvc.perform(post("/api/issues/{id}/github-issue", issue.getId())
                .header("Authorization", authorization))
            .andExpect(status().isForbidden());
        assertTrue(linkRepository.findByIssue_Id(issue.getId()).isEmpty());
        verify(githubClient, times(0)).create(eq(owner), eq(repository), anyString(), anyString());
    }

    @Test
    void concurrentRequestsNeverPostTwiceForTheSameTask() throws Exception {
        CountDownLatch firstPostStarted = new CountDownLatch(1);
        CountDownLatch allowFirstPostToFinish = new CountDownLatch(1);
        AtomicInteger remotePostCount = new AtomicInteger();
        GitHubIssueClient.RemoteIssue remote = new GitHubIssueClient.RemoteIssue(28, "A test task",
            "https://github.com/" + owner + "/" + repository + "/issues/28");
        when(githubClient.create(eq(owner), eq(repository), eq("A test task"), anyString())).thenAnswer(call -> {
            remotePostCount.incrementAndGet();
            firstPostStarted.countDown();
            assertTrue(allowFirstPostToFinish.await(10, TimeUnit.SECONDS));
            return remote;
        });
        when(githubClient.findByTaskMarker(eq(owner), eq(repository), eq(issue.getId()))).thenReturn(List.of());

        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            Future<?> first = workers.submit(() -> creationService.createOrReconcile(issue.getId(), email));
            assertTrue(firstPostStarted.await(10, TimeUnit.SECONDS));
            Future<?> second = workers.submit(() -> creationService.createOrReconcile(issue.getId(), email));
            ExecutionException conflict = org.junit.jupiter.api.Assertions.assertThrows(ExecutionException.class,
                () -> second.get(10, TimeUnit.SECONDS));
            assertTrue(conflict.getCause() instanceof ResponseStatusException);
            assertEquals(HttpStatus.CONFLICT,
                ((ResponseStatusException) conflict.getCause()).getStatusCode());
            allowFirstPostToFinish.countDown();
            first.get(10, TimeUnit.SECONDS);
        } finally {
            allowFirstPostToFinish.countDown();
            workers.shutdownNow();
        }

        assertEquals(1, remotePostCount.get());
        GitHubIssueLink stored = linkRepository.findByIssue_Id(issue.getId()).orElseThrow();
        assertEquals("LINKED", stored.getState());
        assertEquals(28L, stored.getGithubIssueNumber());
        verify(githubClient, times(1)).create(eq(owner), eq(repository), eq("A test task"), anyString());
    }

    @Test
    void concurrentReconciliationFinalizationWritesOnlyOneCreationAuditEvent() throws Exception {
        GitHubIssueLink link = new GitHubIssueLink();
        link.setIssue(issue);
        link.setRepositoryOwner(owner);
        link.setRepositoryName(repository);
        link.setState("UNKNOWN");
        link.setCreatedAt(java.time.LocalDateTime.now());
        link.setUpdatedAt(java.time.LocalDateTime.now());
        link = linkRepository.saveAndFlush(link);
        Long linkId = link.getId();
        GitHubIssueClient.RemoteIssue remote = new GitHubIssueClient.RemoteIssue(29, "A test task",
            "https://github.com/" + owner + "/" + repository + "/issues/29");
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            Future<?> first = workers.submit(() -> finalizeAfterStart(ready, start, linkId, remote));
            Future<?> second = workers.submit(() -> finalizeAfterStart(ready, start, linkId, remote));
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            first.get(10, TimeUnit.SECONDS);
            second.get(10, TimeUnit.SECONDS);
        } finally {
            start.countDown();
            workers.shutdownNow();
        }

        long creationEvents = auditLogsRepository.findByIssueIdOrderByCreatedAtAsc(issue.getId()).stream()
            .filter(log -> "GITHUB_ISSUE_CREATED".equals(log.getAction())).count();
        assertEquals(1L, creationEvents);
        assertEquals("LINKED", linkRepository.findByIssue_Id(issue.getId()).orElseThrow().getState());
    }

    private void finalizeAfterStart(CountDownLatch ready, CountDownLatch start, Long linkId,
            GitHubIssueClient.RemoteIssue remote) {
        ready.countDown();
        try {
            if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Test start timed out");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Test was interrupted", exception);
        }
        linkCoordinator.markLinked(linkId, remote, actor.getId());
    }

    @Test
    void generatedLinkSchemaMatchesV10IndexesAndCascadeMapping() throws SQLException {
        Set<String> columns = new HashSet<>();
        Map<String, Integer> types = new HashMap<>();
        Map<String, Integer> sizes = new HashMap<>();
        Map<String, Integer> precisions = new HashMap<>();
        Map<String, Boolean> nullable = new HashMap<>();
        Set<String> indexes = new HashSet<>();
        Map<String, Set<String>> indexColumns = new HashMap<>();
        Map<String, Set<String>> uniqueIndexColumns = new HashMap<>();
        Map<String, Short> foreignKeys = new HashMap<>();

        try (Connection connection = jdbcTemplate.getDataSource().getConnection()) {
            DatabaseMetaData metadata = connection.getMetaData();
            try (ResultSet result = metadata.getColumns(null, null, "GITHUB_ISSUE_LINK", null)) {
                while (result.next()) {
                    String name = result.getString("COLUMN_NAME").toLowerCase();
                    columns.add(name);
                    types.put(name, result.getInt("DATA_TYPE"));
                    sizes.put(name, result.getInt("COLUMN_SIZE"));
                    precisions.put(name, result.getInt("DECIMAL_DIGITS"));
                    nullable.put(name, result.getInt("NULLABLE") != DatabaseMetaData.columnNoNulls);
                }
            }
            try (ResultSet result = metadata.getIndexInfo(null, null, "GITHUB_ISSUE_LINK", false, false)) {
                while (result.next()) {
                    String name = result.getString("INDEX_NAME");
                    if (name != null) {
                        name = name.toLowerCase();
                        indexes.add(name);
                        indexColumns.computeIfAbsent(name, ignored -> new HashSet<>())
                            .add(result.getString("COLUMN_NAME").toLowerCase());
                        if (!result.getBoolean("NON_UNIQUE")) {
                            uniqueIndexColumns.computeIfAbsent(name, ignored -> new HashSet<>())
                                .add(result.getString("COLUMN_NAME").toLowerCase());
                        }
                    }
                }
            }
            try (ResultSet result = metadata.getImportedKeys(null, null, "GITHUB_ISSUE_LINK")) {
                while (result.next()) {
                    foreignKeys.put(result.getString("FK_NAME").toLowerCase(), result.getShort("DELETE_RULE"));
                }
            }
        }

        assertEquals(Set.of("id", "issue_id", "repository_owner", "repository_name", "github_issue_number",
            "html_url", "title", "state", "created_at", "updated_at"), columns);
        assertEquals(Types.BIGINT, types.get("id"));
        assertEquals(Types.BIGINT, types.get("issue_id"));
        assertEquals(39, sizes.get("repository_owner"));
        assertEquals(100, sizes.get("repository_name"));
        assertEquals(Types.BIGINT, types.get("github_issue_number"));
        assertEquals(2048, sizes.get("html_url"));
        assertEquals(512, sizes.get("title"));
        assertEquals(16, sizes.get("state"));
        assertEquals(Types.TIMESTAMP, types.get("created_at"));
        assertEquals(Types.TIMESTAMP, types.get("updated_at"));
        assertEquals(6, precisions.get("created_at"));
        assertEquals(6, precisions.get("updated_at"));
        assertEquals(false, nullable.get("issue_id"));
        assertEquals(false, nullable.get("state"));
        assertEquals(false, nullable.get("created_at"));
        assertEquals(false, nullable.get("updated_at"));
        assertEquals(true, nullable.get("github_issue_number"));
        assertTrue(uniqueIndexColumns.containsValue(Set.of("issue_id")), uniqueIndexColumns.toString());
        assertTrue(uniqueIndexColumns.containsValue(Set.of("repository_owner", "repository_name", "github_issue_number")),
            uniqueIndexColumns.toString());
        assertTrue(indexes.contains("ix_github_issue_link_repository"), indexes.toString());
        assertEquals(Set.of("repository_owner", "repository_name", "state"),
            indexColumns.get("ix_github_issue_link_repository"));
        assertTrue(foreignKeys.containsKey("fk_github_issue_link_task"), foreignKeys.toString());
        assertEquals(DatabaseMetaData.importedKeyCascade,
            foreignKeys.get("fk_github_issue_link_task").shortValue());
    }

    @Test
    void deletingTaskCascadesItsGitHubLinkAsV10Specifies() {
        GitHubIssueLink link = new GitHubIssueLink();
        link.setIssue(issue);
        link.setRepositoryOwner(owner);
        link.setRepositoryName(repository);
        link.setState("CREATING");
        link.setCreatedAt(java.time.LocalDateTime.now());
        link.setUpdatedAt(java.time.LocalDateTime.now());
        linkRepository.saveAndFlush(link);

        issueRepository.deleteById(issue.getId());

        assertTrue(linkRepository.findByIssue_Id(issue.getId()).isEmpty());
    }
}
