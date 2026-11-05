package com.devflow.devflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doAnswer;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.devflow.devflow.dto.IssueDependencyCreateRequestDTO;
import com.devflow.devflow.entity.Issue;
import com.devflow.devflow.entity.IssueDependency;
import com.devflow.devflow.entity.User;
import com.devflow.devflow.repository.CommentRepository;
import com.devflow.devflow.repository.IssueDependencyRepository;
import com.devflow.devflow.repository.IssueRepository;
import com.devflow.devflow.repository.ProjectRepository;
import com.devflow.devflow.repository.SprintRepository;
import com.devflow.devflow.repository.TestExecutionRepository;
import com.devflow.devflow.repository.UserRepository;
import com.devflow.devflow.service.AuditLogServices;
import com.devflow.devflow.service.IssueService;
import com.devflow.devflow.service.NotificationService;
import com.devflow.devflow.service.AutomationEventService;
import com.devflow.devflow.service.ProjectAccessService;
import com.devflow.devflow.service.EffectivePermissionService;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class IssueDependencyServiceTests {
    @Mock private IssueRepository issueRepository;
    @Mock private IssueDependencyRepository dependencyRepository;
    @Mock private ProjectRepository projectRepository;
    @Mock private SprintRepository sprintRepository;
    @Mock private UserRepository userRepository;
    @Mock private AuditLogServices auditLogServices;
    @Mock private NotificationService notificationService;
    @Mock private AutomationEventService automationEventService;
    @Mock private ProjectAccessService projectAccessService;
    @Mock private EffectivePermissionService permissions;
    @Mock private CommentRepository commentRepository;
    @Mock private TestExecutionRepository testExecutionRepository;
    @InjectMocks private IssueService service;

    private final List<IssueDependency> stored = new ArrayList<>();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "projectAccessService", projectAccessService);
        ReflectionTestUtils.setField(service, "permissions", permissions);
        Mockito.lenient().when(projectAccessService.visibleProjectIds(Mockito.anyString())).thenReturn(List.of());
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
        Mockito.lenient().when(dependencyRepository.save(any(IssueDependency.class))).thenAnswer(call -> {
            IssueDependency dependency = call.getArgument(0);
            stored.add(dependency);
            return dependency;
        });
        Mockito.lenient().when(dependencyRepository.findAll()).thenAnswer(call -> List.copyOf(stored));
        Mockito.lenient().when(dependencyRepository.findByBlockedIssue_Id(any(Long.class))).thenAnswer(call -> stored.stream()
            .filter(item -> item.getBlockedIssue().getId().equals(call.getArgument(0))).toList());
        Mockito.lenient().when(dependencyRepository.findByBlockingIssue_Id(any(Long.class))).thenAnswer(call -> stored.stream()
            .filter(item -> item.getBlockingIssue().getId().equals(call.getArgument(0))).toList());
        Mockito.lenient().when(dependencyRepository.findByBlockedIssue_IdAndBlockingIssue_Id(any(Long.class), any(Long.class)))
            .thenAnswer(call -> stored.stream().filter(item ->
                item.getBlockedIssue().getId().equals(call.getArgument(0))
                    && item.getBlockingIssue().getId().equals(call.getArgument(1))).findFirst());
    }

    @Test
    void managerCanLinkBlockerAndResponseShowsLiveStatusAndReason() {
        User manager = user(1L, "PROJECT_MANAGER");
        Issue blocked = issue(10L, "Implement feature", "OPEN");
        Issue blocker = issue(20L, "Prepare API", "IN_PROGRESS");
        when(userRepository.findByEmail("project_manager@example.com")).thenReturn(Optional.of(manager));
        when(issueRepository.findAllByIdForUpdate(anyCollection())).thenReturn(List.of(blocked, blocker));
        IssueDependencyCreateRequestDTO request = request(20L, "Needs API contract first");

        var response = service.addDependency(10L, request, manager.getEmail());

        assertTrue(response.isBlocked());
        assertEquals(20L, response.getBlockedBy().getFirst().getIssueId());
        assertEquals("IN_PROGRESS", response.getBlockedBy().getFirst().getStatus());
        assertEquals("Needs API contract first", response.getBlockedBy().getFirst().getReason());
        verify(auditLogServices).createLog(eq("ISSUE_BLOCKER_ADDED"), any(), eq(10L), eq(1L));

        blocker.setStatus("RESOLVED");
        assertFalse(service.toIssueResponse(blocked).isBlocked());
        assertEquals("RESOLVED", service.toIssueResponse(blocked).getBlockedBy().getFirst().getStatus());
    }

    @Test
    void duplicateSelfAndCycleLinksAreRejected() {
        User admin = user(1L, "ADMIN");
        Issue first = issue(10L, "First", "OPEN");
        Issue second = issue(20L, "Second", "OPEN");
        when(userRepository.findByEmail(admin.getEmail())).thenReturn(Optional.of(admin));
        when(issueRepository.findAllByIdForUpdate(anyCollection())).thenReturn(List.of(first, second));
        addStoredDependency(second, first, "Existing path");

        ResponseStatusException cycle = assertThrows(ResponseStatusException.class,
            () -> service.addDependency(10L, request(20L, "Cycle"), admin.getEmail()));
        assertEquals(HttpStatus.CONFLICT, cycle.getStatusCode());

        ResponseStatusException self = assertThrows(ResponseStatusException.class,
            () -> service.addDependency(10L, request(10L, "Self"), admin.getEmail()));
        assertEquals(HttpStatus.BAD_REQUEST, self.getStatusCode());
        verify(dependencyRepository, never()).save(any(IssueDependency.class));
    }

    @Test
    void duplicateLinkAndInvalidReasonAreRejected() {
        User manager = user(1L, "PROJECT_MANAGER");
        Issue blocked = issue(10L, "First", "OPEN");
        Issue blocker = issue(20L, "Second", "OPEN");
        when(userRepository.findByEmail(manager.getEmail())).thenReturn(Optional.of(manager));
        when(issueRepository.findAllByIdForUpdate(anyCollection())).thenReturn(List.of(blocked, blocker));
        addStoredDependency(blocked, blocker, "Existing");

        ResponseStatusException duplicate = assertThrows(ResponseStatusException.class,
            () -> service.addDependency(10L, request(20L, "Duplicate"), manager.getEmail()));
        assertEquals(HttpStatus.CONFLICT, duplicate.getStatusCode());
        ResponseStatusException reason = assertThrows(ResponseStatusException.class,
            () -> service.addDependency(10L, request(30L, "  "), manager.getEmail()));
        assertEquals(HttpStatus.BAD_REQUEST, reason.getStatusCode());
        verify(dependencyRepository, never()).save(any(IssueDependency.class));
    }

    @Test
    void onlyAdminsAndProjectManagersCanAddOrRemoveBlockers() {
        User developer = user(1L, "DEVELOPER");
        when(userRepository.findByEmail(developer.getEmail())).thenReturn(Optional.of(developer));

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
            () -> service.addDependency(10L, request(20L, "Blocked"), developer.getEmail()));

        assertEquals(HttpStatus.FORBIDDEN, error.getStatusCode());
        verify(issueRepository, never()).findAllByIdForUpdate(any());
        verify(dependencyRepository, never()).delete(any(IssueDependency.class));
    }

    @Test
    void bothReferencedIssuesMustExist() {
        User admin = user(1L, "ADMIN");
        when(userRepository.findByEmail(admin.getEmail())).thenReturn(Optional.of(admin));
        when(issueRepository.findAllByIdForUpdate(anyCollection())).thenReturn(List.of(issue(10L, "Only issue", "OPEN")));

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
            () -> service.addDependency(10L, request(20L, "Missing blocker"), admin.getEmail()));

        assertEquals(HttpStatus.NOT_FOUND, error.getStatusCode());
        verify(dependencyRepository, never()).save(any(IssueDependency.class));
    }

    private void addStoredDependency(Issue blocked, Issue blocker, String reason) {
        IssueDependency dependency = new IssueDependency();
        dependency.setBlockedIssue(blocked);
        dependency.setBlockingIssue(blocker);
        dependency.setCreatedBy(user(1L, "ADMIN"));
        dependency.setReason(reason);
        stored.add(dependency);
    }

    private IssueDependencyCreateRequestDTO request(Long blockerId, String reason) {
        IssueDependencyCreateRequestDTO request = new IssueDependencyCreateRequestDTO();
        request.setBlockingIssueId(blockerId);
        request.setReason(reason);
        return request;
    }

    private Issue issue(Long id, String title, String status) {
        Issue issue = new Issue();
        issue.setId(id);
        issue.setTitle(title);
        issue.setStatus(status);
        return issue;
    }

    private User user(Long id, String role) {
        User user = new User();
        user.setId(id);
        user.setName("User " + id);
        user.setEmail(role.toLowerCase(java.util.Locale.ROOT) + "@example.com");
        user.setRole(role);
        return user;
    }

    private boolean allowed(String email, String permission) {
        return email != null && (email.startsWith("admin") || email.startsWith("project_manager"))
            && "ISSUE_DEPENDENCY_MANAGE".equals(permission);
    }
}
