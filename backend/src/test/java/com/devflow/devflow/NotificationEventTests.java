package com.devflow.devflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.devflow.devflow.entity.Issue;
import com.devflow.devflow.entity.Project;
import com.devflow.devflow.entity.Sprint;
import com.devflow.devflow.entity.User;
import com.devflow.devflow.dto.IssueUpdateRequestDTO;
import com.devflow.devflow.dto.IssueCreateRequestDTO;
import com.devflow.devflow.dto.CommentCreateRequestDTO;
import com.devflow.devflow.dto.IdReferenceRequestDTO;
import com.devflow.devflow.repository.CommentRepository;
import com.devflow.devflow.repository.IssueRepository;
import com.devflow.devflow.repository.ProjectRepository;
import com.devflow.devflow.repository.SprintRepository;
import com.devflow.devflow.repository.UserRepository;
import com.devflow.devflow.service.AuditLogServices;
import com.devflow.devflow.service.CommentService;
import com.devflow.devflow.service.IssueService;
import com.devflow.devflow.service.NotificationService;

@ExtendWith(MockitoExtension.class)
class NotificationEventTests {

    @Mock private IssueRepository issueRepository;
    @Mock private ProjectRepository projectRepository;
    @Mock private SprintRepository sprintRepository;
    @Mock private UserRepository userRepository;
    @Mock private AuditLogServices auditLogService;
    @Mock private NotificationService notificationService;
    @Mock private CommentRepository commentRepository;

    @InjectMocks private IssueService issueService;
    @InjectMocks private CommentService commentService;

    @Test
    void issueAssignmentCreatesNotificationForNewAssignee() {
        User actor = user(1L, "manager@example.com", "PROJECT_MANAGER");
        User assignee = user(2L, "developer@example.com", "DEVELOPER");
        when(userRepository.findByEmail(actor.getEmail())).thenReturn(Optional.of(actor));
        when(userRepository.findById(2L)).thenReturn(Optional.of(assignee));
        when(issueRepository.save(any(Issue.class))).thenAnswer(invocation -> invocation.getArgument(0));

        issueService.createIssue(issueCreateRequest("Fix login bug", 2L), actor.getEmail());

        verify(notificationService).createNotification(assignee, "ISSUE_ASSIGNED", "You were assigned issue 'Fix login bug'");
    }

    @Test
    void statusChangeNotifiesAssigneeButUnchangedStatusDoesNot() {
        User actor = user(1L, "manager@example.com", "PROJECT_MANAGER");
        User assignee = user(2L, "developer@example.com", "DEVELOPER");
        Issue existing = issue("Fix login bug", assignee, "OPEN");
        existing.setId(11L);
        when(userRepository.findByEmail(actor.getEmail())).thenReturn(Optional.of(actor));
        when(userRepository.findById(2L)).thenReturn(Optional.of(assignee));
        when(projectRepository.findById(5L)).thenReturn(Optional.of(existing.getProject()));
        when(sprintRepository.findById(9L)).thenReturn(Optional.of(existing.getSprint()));
        when(issueRepository.findByIdForUpdate(11L)).thenReturn(Optional.of(existing));
        when(issueRepository.save(any(Issue.class))).thenAnswer(invocation -> invocation.getArgument(0));

        issueService.updateIssue(11L, updateRequest("Fix login bug", assignee, "IN_PROGRESS"), actor.getEmail());
        verify(notificationService).createNotification(assignee, "ISSUE_STATUS_CHANGED",
            "Issue 'Fix login bug' changed status to IN_PROGRESS");

        org.mockito.Mockito.clearInvocations(notificationService);
        issueService.updateIssue(11L, updateRequest("Fix login bug", assignee, "IN_PROGRESS"), actor.getEmail());
        verify(notificationService, never()).createNotification(eq(assignee), eq("ISSUE_STATUS_CHANGED"), any());
    }

    @Test
    void unchangedAssigneeDoesNotCreateAssignmentNotification() {
        User actor = user(1L, "manager@example.com", "PROJECT_MANAGER");
        User assignee = user(2L, "developer@example.com", "DEVELOPER");
        Issue existing = issue("Fix login bug", assignee, "OPEN");
        existing.setId(11L);
        when(userRepository.findByEmail(actor.getEmail())).thenReturn(Optional.of(actor));
        when(userRepository.findById(2L)).thenReturn(Optional.of(assignee));
        when(projectRepository.findById(5L)).thenReturn(Optional.of(existing.getProject()));
        when(sprintRepository.findById(9L)).thenReturn(Optional.of(existing.getSprint()));
        when(issueRepository.findByIdForUpdate(11L)).thenReturn(Optional.of(existing));
        when(issueRepository.save(any(Issue.class))).thenAnswer(invocation -> invocation.getArgument(0));

        issueService.updateIssue(11L, updateRequest("Fix login bug", assignee, "OPEN"), actor.getEmail());

        verify(notificationService, never()).createNotification(eq(assignee), eq("ISSUE_ASSIGNED"), any());
    }

    @Test
    void commentNotifiesAssignedUserButNotCommentAuthor() {
        User author = user(1L, "author@example.com", "DEVELOPER");
        User assignee = user(2L, "assignee@example.com", "DEVELOPER");
        Issue issue = issue("Fix login bug", assignee, "OPEN");
        issue.setId(11L);
        CommentCreateRequestDTO comment = commentRequest(issue.getId());
        when(userRepository.findByEmail(author.getEmail())).thenReturn(Optional.of(author));
        when(commentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(issueRepository.findById(11L)).thenReturn(Optional.of(issue));

        commentService.createComment(comment, author.getEmail());

        verify(notificationService).createNotification(assignee, "COMMENT_ADDED",
            "New comment added to issue 'Fix login bug'");

        org.mockito.Mockito.clearInvocations(notificationService);
        when(userRepository.findByEmail(assignee.getEmail())).thenReturn(Optional.of(assignee));
        CommentCreateRequestDTO ownComment = commentRequest(issue.getId());

        commentService.createComment(ownComment, assignee.getEmail());

        verify(notificationService, never()).createNotification(any(), eq("COMMENT_ADDED"), any());
    }

    @Test
    void issueCreationKeepsExistingAuditActorAndAssignsNotificationRecipient() {
        User actor = user(1L, "manager@example.com", "PROJECT_MANAGER");
        User assignee = user(2L, "developer@example.com", "DEVELOPER");
        when(userRepository.findByEmail(actor.getEmail())).thenReturn(Optional.of(actor));
        when(userRepository.findById(2L)).thenReturn(Optional.of(assignee));
        when(issueRepository.save(any(Issue.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Issue issue = issueService.createIssue(issueCreateRequest("Fix login bug", 2L), actor.getEmail());

        verify(auditLogService).createLog(eq("ISSUE_CREATED"), any(), any(), eq(1L));
        assertEquals(assignee, issue.getAssignedUser());
    }

    private Issue issue(String title, User assignee, String status) {
        Issue issue = new Issue();
        issue.setTitle(title);
        issue.setStatus(status);
        issue.setPriority("MEDIUM");
        Project project = new Project();
        project.setId(5L);
        Sprint sprint = new Sprint();
        sprint.setId(9L);
        sprint.setProject(project);
        issue.setProject(project);
        issue.setSprint(sprint);
        issue.setAssignedUser(assignee);
        return issue;
    }

    private IssueUpdateRequestDTO updateRequest(String title, User assignee, String status) {
        IssueUpdateRequestDTO request = new IssueUpdateRequestDTO();
        request.setTitle(title);
        request.setStatus(status);
        request.setPriority("MEDIUM");
        request.setProject(reference(5L));
        request.setSprint(reference(9L));
        request.setAssignedUser(reference(assignee.getId()));
        return request;
    }

    private IssueCreateRequestDTO issueCreateRequest(String title, Long assignedUserId) {
        IssueCreateRequestDTO request = new IssueCreateRequestDTO();
        request.setTitle(title);
        request.setPriority("MEDIUM");
        request.setStatus("OPEN");
        request.setProject(reference(5L));
        request.setSprint(reference(9L));
        request.setAssignedUser(reference(assignedUserId));
        when(projectRepository.findById(5L)).thenReturn(Optional.of(project(5L)));
        when(sprintRepository.findById(9L)).thenReturn(Optional.of(sprint(9L)));
        return request;
    }

    private CommentCreateRequestDTO commentRequest(Long issueId) {
        CommentCreateRequestDTO request = new CommentCreateRequestDTO();
        request.setContent("A useful update");
        request.setIssue(reference(issueId));
        return request;
    }

    private IdReferenceRequestDTO reference(Long id) {
        IdReferenceRequestDTO reference = new IdReferenceRequestDTO();
        reference.setId(id);
        return reference;
    }

    private Project project(Long id) {
        Project project = new Project();
        project.setId(id);
        return project;
    }

    private Sprint sprint(Long id) {
        Sprint sprint = new Sprint();
        sprint.setId(id);
        sprint.setProject(project(5L));
        return sprint;
    }

    private User user(Long id, String email, String role) {
        User user = new User();
        user.setId(id);
        user.setEmail(email);
        user.setName(email);
        user.setRole(role);
        return user;
    }
}
