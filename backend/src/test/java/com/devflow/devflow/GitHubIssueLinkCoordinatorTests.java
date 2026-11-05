package com.devflow.devflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.devflow.devflow.entity.Issue;
import com.devflow.devflow.entity.GitHubIssueLink;
import com.devflow.devflow.entity.Project;
import com.devflow.devflow.entity.User;
import com.devflow.devflow.repository.GitHubIssueLinkRepository;
import com.devflow.devflow.repository.IssueRepository;
import com.devflow.devflow.repository.UserRepository;
import com.devflow.devflow.service.AuditLogServices;
import com.devflow.devflow.service.GitHubIssueLinkCoordinator;
import com.devflow.devflow.service.GitHubIssueClient;

@ExtendWith(MockitoExtension.class)
class GitHubIssueLinkCoordinatorTests {
    @Mock private IssueRepository issueRepository;
    @Mock private UserRepository userRepository;
    @Mock private GitHubIssueLinkRepository linkRepository;
    @Mock private AuditLogServices auditLogService;

    @Test
    void authorizedManagerUsesTaskProjectMappingAndCreatesStableMarker() {
        User actor = user("PROJECT_MANAGER");
        Project project = project("Acme", "DevFlow");
        Issue issue = issue(51L, project);
        when(userRepository.findByEmailIgnoreCase("manager@example.test")).thenReturn(Optional.of(actor));
        when(issueRepository.findByIdForUpdate(51L)).thenReturn(Optional.of(issue));
        when(linkRepository.findByIssue_Id(51L)).thenReturn(Optional.empty());
        when(linkRepository.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));

        var prepared = new GitHubIssueLinkCoordinator(issueRepository, userRepository, linkRepository, auditLogService)
            .prepare(51L, "manager@example.test");

        assertEquals("acme", prepared.owner());
        assertEquals("devflow", prepared.repository());
        assertEquals("CREATE", prepared.action());
        assertEquals("Fix parser", prepared.title());
        org.junit.jupiter.api.Assertions.assertTrue(prepared.body().startsWith("<!-- DevFlow-Task-ID:51 -->"));
        verify(linkRepository).saveAndFlush(any());
    }

    @Test
    void developersCannotCreateGitHubIssuesEvenIfTheyCanEditDevFlowTasks() {
        when(userRepository.findByEmailIgnoreCase("dev@example.test"))
            .thenReturn(Optional.of(user("DEVELOPER")));
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
            () -> coordinator().prepare(51L, "dev@example.test"));
        assertEquals(HttpStatus.FORBIDDEN, error.getStatusCode());
        verifyNoInteractions(issueRepository, linkRepository);
    }

    @Test
    void missingRepositoryMappingIsRejectedBeforeAnyAttemptIsStored() {
        when(userRepository.findByEmailIgnoreCase("manager@example.test"))
            .thenReturn(Optional.of(user("PROJECT_MANAGER")));
        when(issueRepository.findByIdForUpdate(51L)).thenReturn(Optional.of(issue(51L, project(null, null))));
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
            () -> coordinator().prepare(51L, "manager@example.test"));
        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
        verify(linkRepository).findByIssue_Id(51L);
        org.mockito.Mockito.verify(linkRepository, org.mockito.Mockito.never()).saveAndFlush(any());
    }

    @Test
    void existingLinkIsReturnedEvenIfProjectMappingWasLaterRemoved() {
        when(userRepository.findByEmailIgnoreCase("manager@example.test"))
            .thenReturn(Optional.of(user("PROJECT_MANAGER")));
        when(issueRepository.findByIdForUpdate(51L)).thenReturn(Optional.of(issue(51L, project(null, null))));
        GitHubIssueLink existing = new GitHubIssueLink();
        existing.setIssue(issue(51L, project(null, null)));
        existing.setRepositoryOwner("old-org");
        existing.setRepositoryName("old-repo");
        existing.setState("LINKED");
        when(linkRepository.findByIssue_Id(51L)).thenReturn(Optional.of(existing));

        var prepared = coordinator().prepare(51L, "manager@example.test");

        assertEquals("LINKED", prepared.action());
        assertEquals("old-org", prepared.owner());
        assertEquals("old-repo", prepared.repository());
        verifyNoInteractions(auditLogService);
    }

    @Test
    void recoveredGitHubIssueIsRecordedAsReconciliationRatherThanCreation() {
        Issue issue = issue(51L, project("acme", "devflow"));
        GitHubIssueLink link = new GitHubIssueLink();
        org.springframework.test.util.ReflectionTestUtils.setField(link, "id", 91L);
        link.setIssue(issue);
        link.setRepositoryOwner("acme");
        link.setRepositoryName("devflow");
        link.setState("CREATING");
        when(linkRepository.findByIdForUpdate(91L)).thenReturn(Optional.of(link));
        when(linkRepository.save(any())).thenAnswer(call -> call.getArgument(0));

        coordinator().markReconciled(91L,
            new GitHubIssueClient.RemoteIssue(17L, "Existing task", "https://github.com/acme/devflow/issues/17"), 4L);

        verify(auditLogService).createLog(eq("GITHUB_ISSUE_RECONCILED"), anyString(), eq(51L), eq(4L));
    }

    private GitHubIssueLinkCoordinator coordinator() {
        return new GitHubIssueLinkCoordinator(issueRepository, userRepository, linkRepository, auditLogService);
    }

    private User user(String role) {
        User user = new User();
        user.setId(4L); user.setEmail("manager@example.test"); user.setRole(role);
        return user;
    }

    private Project project(String owner, String name) {
        Project project = new Project();
        project.setGithubRepositoryOwner(owner); project.setGithubRepositoryName(name);
        return project;
    }

    private Issue issue(Long id, Project project) {
        Issue issue = new Issue(); issue.setId(id); issue.setProject(project);
        issue.setTitle("Fix parser"); issue.setDescription("Handle invalid input");
        issue.setAcceptanceCriteria("Reject malformed JSON");
        return issue;
    }

}
