package com.devflow.devflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.web.server.ResponseStatusException;

import com.devflow.devflow.entity.Issue;
import com.devflow.devflow.entity.User;
import com.devflow.devflow.entity.AutomationTriggerType;
import com.devflow.devflow.dto.IssueUpdateRequestDTO;
import com.devflow.devflow.dto.IssueCreateRequestDTO;
import com.devflow.devflow.repository.IssueRepository;
import com.devflow.devflow.repository.IssueDependencyRepository;
import com.devflow.devflow.repository.ProjectRepository;
import com.devflow.devflow.repository.SprintRepository;
import com.devflow.devflow.repository.UserRepository;
import com.devflow.devflow.repository.CommentRepository;
import com.devflow.devflow.repository.TestExecutionRepository;
import com.devflow.devflow.service.AuditLogServices;
import com.devflow.devflow.service.IssueService;
import com.devflow.devflow.service.NotificationService;
import com.devflow.devflow.service.AutomationEventService;
import com.devflow.devflow.service.EffectivePermissionService;
import com.devflow.devflow.service.ProjectAccessService;
import org.springframework.test.util.ReflectionTestUtils;
import org.mockito.Mockito;

@ExtendWith(MockitoExtension.class)
class IssueLifecycleServiceTests {

    @Mock private IssueRepository issueRepository;
    @Mock private IssueDependencyRepository issueDependencyRepository;
    @Mock private ProjectRepository projectRepository;
    @Mock private SprintRepository sprintRepository;
    @Mock private UserRepository userRepository;
    @Mock private CommentRepository commentRepository;
    @Mock private TestExecutionRepository testExecutionRepository;
    @Mock private AuditLogServices auditLogService;
    @Mock private NotificationService notificationService;
    @Mock private AutomationEventService automationEventService;
    @Mock private EffectivePermissionService permissions;
    @Mock private ProjectAccessService projectAccessService;
    @InjectMocks private IssueService service;

    @BeforeEach
    void injectCurrentAuthorizationCollaborators() {
        ReflectionTestUtils.setField(service, "permissions", permissions);
        ReflectionTestUtils.setField(service, "projectAccessService", projectAccessService);
        Mockito.lenient().when(permissions.has(Mockito.anyString(), Mockito.anyString(),
                Mockito.nullable(Long.class), Mockito.nullable(Long.class)))
            .thenAnswer(call -> allowed(call.getArgument(0), call.getArgument(1)));
        Mockito.lenient().when(permissions.hasGlobal(Mockito.anyString(), Mockito.anyString()))
            .thenAnswer(call -> allowed(call.getArgument(0), call.getArgument(1)));
        Mockito.lenient().when(permissions.hasAnywhere(Mockito.anyString(), Mockito.anyString()))
            .thenAnswer(call -> allowed(call.getArgument(0), call.getArgument(1)));
        Mockito.lenient().doAnswer(call -> {
            if (!allowed(call.getArgument(0), call.getArgument(1))) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Required permission is not granted");
            }
            return null;
        }).when(permissions).require(Mockito.anyString(), Mockito.anyString(),
            Mockito.nullable(Long.class), Mockito.nullable(Long.class));
    }

    @Test
    void issueCreatorIsRecordedAsReporterAndGeneratedFieldsStartUnset() {
        User actor = user(1L, "reporter@example.com", "DEVELOPER");
        when(userRepository.findByEmail(actor.getEmail())).thenReturn(Optional.of(actor));
        when(issueRepository.save(any(Issue.class))).thenAnswer(call -> call.getArgument(0));
        IssueCreateRequestDTO request = new IssueCreateRequestDTO();
        request.setTitle("New defect");

        Issue created = service.createIssue(request, actor.getEmail());

        assertEquals(actor, created.getReportedBy());
        assertEquals(null, created.getId());
        assertEquals(null, created.getTester());
        verify(auditLogService).createLog(eq("ISSUE_CREATED"), any(), any(), eq(1L));
    }

    @Test
    void developerCannotSkipLifecycleStatusOrEditAnotherUsersIssue() {
        User developer = user(1L, "developer@example.com", "DEVELOPER");
        User otherDeveloper = user(2L, "other@example.com", "DEVELOPER");
        Issue existing = new Issue();
        existing.setId(3L);
        existing.setAssignedUser(developer);
        existing.setStatus("OPEN");
        IssueUpdateRequestDTO requested = new IssueUpdateRequestDTO();
        requested.setStatus("TESTING");
        when(userRepository.findByEmail(developer.getEmail())).thenReturn(Optional.of(developer));
        when(issueRepository.findByIdForUpdate(3L)).thenReturn(Optional.of(existing));

        ResponseStatusException transitionError = assertThrows(ResponseStatusException.class,
            () -> service.updateIssue(3L, requested, developer.getEmail()));
        assertEquals(HttpStatus.CONFLICT, transitionError.getStatusCode());
        verify(issueRepository, never()).save(any(Issue.class));

        existing.setAssignedUser(otherDeveloper);
        ResponseStatusException ownershipError = assertThrows(ResponseStatusException.class,
            () -> service.updateIssue(3L, requested, developer.getEmail()));
        assertEquals(HttpStatus.FORBIDDEN, ownershipError.getStatusCode());
    }

    @Test
    void managerCanAssignOnlyAUserWithTesterRoleAndLogsActor() {
        User manager = user(7L, "manager@example.com", "PROJECT_MANAGER");
        User tester = user(8L, "tester@example.com", "TESTER");
        Issue issue = new Issue();
        issue.setId(12L);
        issue.setTitle("Queue issue");
        when(userRepository.findByEmail(manager.getEmail())).thenReturn(Optional.of(manager));
        when(permissions.hasGlobal(tester.getEmail(), "ASSIGNABLE_TESTER")).thenReturn(true);
        when(issueRepository.findByIdForUpdate(12L)).thenReturn(Optional.of(issue));
        when(issueRepository.save(issue)).thenReturn(issue);
        when(userRepository.findById(8L)).thenReturn(Optional.of(tester));

        var response = service.assignTester(12L, 8L, manager.getEmail());

        assertEquals("tester@example.com", response.getTester().getEmail());
        verify(auditLogService).createLog(eq("TESTER_ASSIGNED"), any(), eq(12L), eq(7L));
        verify(notificationService).createNotification(eq(tester), eq("TESTER_ASSIGNED"), any(), eq("/issues?issueId=12"));
    }

    @Test
    void changingTesterNotifiesBothNewAndFormerTester() {
        User manager = user(7L, "manager@example.com", "PROJECT_MANAGER");
        User oldTester = user(8L, "old-tester@example.com", "TESTER");
        User newTester = user(9L, "new-tester@example.com", "TESTER");
        Issue issue = new Issue();
        issue.setId(12L);
        issue.setTitle("Queue issue");
        issue.setTester(oldTester);
        when(userRepository.findByEmail(manager.getEmail())).thenReturn(Optional.of(manager));
        when(userRepository.findById(newTester.getId())).thenReturn(Optional.of(newTester));
        when(userRepository.findById(oldTester.getId())).thenReturn(Optional.of(oldTester));
        when(permissions.hasGlobal(newTester.getEmail(), "ASSIGNABLE_TESTER")).thenReturn(true);
        when(issueRepository.findByIdForUpdate(issue.getId())).thenReturn(Optional.of(issue));
        when(issueRepository.save(issue)).thenReturn(issue);

        service.assignTester(issue.getId(), newTester.getId(), manager.getEmail());

        verify(notificationService).createNotification(eq(newTester), eq("TESTER_ASSIGNED"), any(), eq("/issues?issueId=12"));
        verify(notificationService).createNotification(eq(oldTester), eq("TESTER_UNASSIGNED"), any(), eq("/issues?issueId=12"));
    }

    @Test
    void assignmentRejectsNonTesterAndDeveloperActor() {
        User manager = user(7L, "manager@example.com", "PROJECT_MANAGER");
        User developer = user(8L, "developer@example.com", "DEVELOPER");
        Issue issue = new Issue();
        issue.setId(12L);
        when(userRepository.findByEmail(manager.getEmail())).thenReturn(Optional.of(manager));
        when(userRepository.findById(8L)).thenReturn(Optional.of(developer));
        when(issueRepository.findByIdForUpdate(12L)).thenReturn(Optional.of(issue));

        ResponseStatusException wrongRole = assertThrows(ResponseStatusException.class,
            () -> service.assignTester(12L, 8L, manager.getEmail()));
        assertEquals(HttpStatus.BAD_REQUEST, wrongRole.getStatusCode());

        when(userRepository.findByEmail(developer.getEmail())).thenReturn(Optional.of(developer));
        ResponseStatusException forbidden = assertThrows(ResponseStatusException.class,
            () -> service.assignTester(12L, 8L, developer.getEmail()));
        assertEquals(HttpStatus.FORBIDDEN, forbidden.getStatusCode());
        verify(issueRepository, never()).save(any(Issue.class));
    }

    @Test
    void testerAssignmentRejectsRoleValuesThatAreNotExactlyTester() {
        User manager = user(7L, "manager@example.com", "PROJECT_MANAGER");
        User incorrectlyCasedTester = user(8L, "tester@example.com", "tester");
        Issue issue = new Issue();
        issue.setId(12L);
        when(userRepository.findByEmail(manager.getEmail())).thenReturn(Optional.of(manager));
        when(issueRepository.findByIdForUpdate(12L)).thenReturn(Optional.of(issue));
        when(userRepository.findById(8L)).thenReturn(Optional.of(incorrectlyCasedTester));
        when(permissions.hasGlobal(incorrectlyCasedTester.getEmail(), "ASSIGNABLE_TESTER")).thenReturn(false);

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
            () -> service.assignTester(12L, 8L, manager.getEmail()));

        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
        verify(issueRepository, never()).save(any(Issue.class));
    }

    @Test
    void issueDeletionRetainsAuditRowsAndDeletesOnlyWhenCommentsAndTestHistoryAreAbsent() {
        User admin = user(1L, "admin@example.com", "ADMIN");
        Issue issue = new Issue();
        issue.setId(45L);
        issue.setTitle("Unused issue");
        when(userRepository.findByEmail(admin.getEmail())).thenReturn(Optional.of(admin));
        when(issueRepository.findById(45L)).thenReturn(Optional.of(issue));

        service.deleteIssue(45L, admin.getEmail());

        verify(auditLogService).detachIssueReference(45L);
        verify(issueRepository).deleteById(45L);
        verify(auditLogService).createLog(eq("ISSUE_DELETED"), any(), eq(null), eq(1L));
    }

    @Test
    void issueDeletionReturnsConflictInsteadOfBreakingCommentOrTestHistory() {
        User manager = user(2L, "manager@example.com", "PROJECT_MANAGER");
        Issue issue = new Issue();
        issue.setId(46L);
        when(userRepository.findByEmail(manager.getEmail())).thenReturn(Optional.of(manager));
        when(issueRepository.findById(46L)).thenReturn(Optional.of(issue));
        when(commentRepository.existsByIssueId(46L)).thenReturn(true);

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
            () -> service.deleteIssue(46L, manager.getEmail()));

        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
        verify(issueRepository, never()).deleteById(46L);
        verify(auditLogService, never()).detachIssueReference(46L);
    }

    @Test
    void serviceRejectsIssueDeleteWhenActorIsNotAdminOrProjectManager() {
        User developer = user(3L, "developer@example.com", "DEVELOPER");
        when(userRepository.findByEmail(developer.getEmail())).thenReturn(Optional.of(developer));

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
            () -> service.deleteIssue(47L, developer.getEmail()));

        assertEquals(HttpStatus.FORBIDDEN, error.getStatusCode());
        verify(issueRepository, never()).findById(47L);
    }

    @Test
    void assignedDeveloperCanFollowTheExistingForwardWorkflow() {
        User developer = user(1L, "developer@example.com", "DEVELOPER");
        Issue existing = new Issue();
        existing.setId(3L);
        existing.setAssignedUser(developer);
        existing.setStatus("OPEN");
        when(userRepository.findByEmail(developer.getEmail())).thenReturn(Optional.of(developer));
        when(issueRepository.findByIdForUpdate(3L)).thenReturn(Optional.of(existing));
        when(issueRepository.save(existing)).thenReturn(existing);

        for (String status : List.of("IN_PROGRESS", "CODE_REVIEW", "READY_FOR_TESTING")) {
            IssueUpdateRequestDTO request = new IssueUpdateRequestDTO();
            request.setStatus(status);
            service.updateIssue(3L, request, developer.getEmail());
            assertEquals(status, existing.getStatus());
        }
        verify(automationEventService).enqueue(eq(AutomationTriggerType.TASK_STATUS_CHANGED), eq(existing),
            eq("OPEN"), eq("IN_PROGRESS"), any());
        verify(automationEventService).enqueue(eq(AutomationTriggerType.TASK_STATUS_CHANGED), eq(existing),
            eq("CODE_REVIEW"), eq("READY_FOR_TESTING"), any());
    }

    @Test
    void managersCannotManuallyStartOrResolveTestingAndCannotInterruptActiveTesting() {
        User manager = user(7L, "manager@example.com", "PROJECT_MANAGER");
        Issue issue = new Issue();
        issue.setId(12L);
        issue.setStatus("READY_FOR_TESTING");
        when(userRepository.findByEmail(manager.getEmail())).thenReturn(Optional.of(manager));
        when(issueRepository.findByIdForUpdate(12L)).thenReturn(Optional.of(issue));

        for (String status : List.of("TESTING", "RESOLVED")) {
            IssueUpdateRequestDTO request = new IssueUpdateRequestDTO();
            request.setStatus(status);
            ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.updateIssue(12L, request, manager.getEmail()));
            assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
        }

        issue.setStatus("TESTING");
        IssueUpdateRequestDTO interrupt = new IssueUpdateRequestDTO();
        interrupt.setStatus("IN_PROGRESS");
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
            () -> service.updateIssue(12L, interrupt, manager.getEmail()));
        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
        verify(issueRepository, never()).save(any(Issue.class));
    }

    private User user(Long id, String email, String role) {
        User user = new User();
        user.setId(id);
        user.setName(email);
        user.setEmail(email);
        user.setRole(role);
        return user;
    }

    private boolean allowed(String email, String key) {
        if (email != null && email.startsWith("admin")) return true;
        if (email != null && email.startsWith("manager")) return !"TESTING_OVERRIDE".equals(key);
        if (email != null && email.startsWith("developer")) {
            return List.of("ISSUE_CREATE", "ISSUE_UPDATE", "ISSUE_UPDATE_ASSIGNED", "ISSUE_STATUS_ADVANCE",
                "ASSIGNABLE_DEVELOPER").contains(key);
        }
        if (email != null && email.startsWith("reporter")) return "ISSUE_CREATE".equals(key);
        if (email != null && email.startsWith("tester")) return List.of("ASSIGNABLE_TESTER", "TEST_EXECUTE").contains(key);
        return false;
    }
}
