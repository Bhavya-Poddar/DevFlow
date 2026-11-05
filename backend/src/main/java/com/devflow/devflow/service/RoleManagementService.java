package com.devflow.devflow.service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.devflow.devflow.dto.RoleResponseDTO;
import com.devflow.devflow.dto.RoleUpsertRequestDTO;
import com.devflow.devflow.entity.AuthorizationRole;
import com.devflow.devflow.entity.PermissionDefinition;
import com.devflow.devflow.entity.PermissionScope;
import com.devflow.devflow.entity.Project;
import com.devflow.devflow.entity.ProjectRoleAssignment;
import com.devflow.devflow.entity.Team;
import com.devflow.devflow.entity.TeamRoleAssignment;
import com.devflow.devflow.entity.User;
import com.devflow.devflow.repository.AuthorizationRoleRepository;
import com.devflow.devflow.repository.PermissionDefinitionRepository;
import com.devflow.devflow.repository.ProjectRepository;
import com.devflow.devflow.repository.ProjectRoleAssignmentRepository;
import com.devflow.devflow.repository.TeamRepository;
import com.devflow.devflow.repository.TeamRoleAssignmentRepository;
import com.devflow.devflow.repository.UserRepository;

@Service
public class RoleManagementService {
    private static final Pattern ROLE_CODE = Pattern.compile("^[A-Z][A-Z0-9_]{1,63}$");
    private final AuthorizationRoleRepository roles;
    private final PermissionDefinitionRepository permissionDefinitions;
    private final ProjectRoleAssignmentRepository projectAssignments;
    private final TeamRoleAssignmentRepository teamAssignments;
    private final ProjectRepository projects;
    private final TeamRepository teams;
    private final UserRepository users;
    private final EffectivePermissionService effectivePermissions;
    private final AdminRecoveryCapability recoveryCapability;
    private final AdminRecoveryGuard recoveryGuard;
    private final AuditLogServices audit;
    @org.springframework.beans.factory.annotation.Autowired private NotificationService notifications;

    public RoleManagementService(AuthorizationRoleRepository roles,
            PermissionDefinitionRepository permissionDefinitions,
            ProjectRoleAssignmentRepository projectAssignments, TeamRoleAssignmentRepository teamAssignments,
            ProjectRepository projects, TeamRepository teams, UserRepository users,
            EffectivePermissionService effectivePermissions, AdminRecoveryCapability recoveryCapability,
            AdminRecoveryGuard recoveryGuard,
            AuditLogServices audit) {
        this.roles = roles; this.permissionDefinitions = permissionDefinitions;
        this.projectAssignments = projectAssignments; this.teamAssignments = teamAssignments;
        this.projects = projects; this.teams = teams; this.users = users;
        this.effectivePermissions = effectivePermissions; this.recoveryCapability = recoveryCapability;
        this.recoveryGuard = recoveryGuard; this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<RoleResponseDTO> list(String actorEmail) {
        requireRoleManager(actorEmail);
        return roles.findAll().stream().map(this::response).toList();
    }

    @Transactional
    public RoleResponseDTO create(RoleUpsertRequestDTO request, String actorEmail) {
        recoveryGuard.lockInvariant();
        requireRoleManager(actorEmail);
        recoveryGuard.requireRecoveryAccount();
        if (request == null || request.scope() == null) badRequest("Role scope is required");
        String code = normalizeCode(request.code());
        if (!ROLE_CODE.matcher(code).matches()) badRequest("Role code must contain 2 to 64 uppercase letters, digits, or underscores");
        if (roles.existsByCodeIgnoreCase(code)) throw new ResponseStatusException(HttpStatus.CONFLICT, "Role code already exists");
        String name = normalizeName(request.name());
        AuthorizationRole role = new AuthorizationRole(code, name, request.scope(), false);
        role.setDescription(normalizeDescription(request.description()));
        role.setPermissions(resolvePermissions(request.permissions(), request.scope()));
        AuthorizationRole saved = roles.saveAndFlush(role);
        audit.createLog("ROLE_CREATED", "targetType=ROLE; roleId=" + saved.getId() + "; code=" + code,
            null, actorId(actorEmail));
        return response(saved);
    }

    @Transactional
    public RoleResponseDTO update(Long roleId, RoleUpsertRequestDTO request, String actorEmail) {
        recoveryGuard.lockInvariant();
        requireRoleManager(actorEmail);
        recoveryGuard.requireRecoveryAccount();
        if (request == null) badRequest("Role details are required");
        AuthorizationRole role = roles.findById(roleId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Role not found"));
        if (request.code() != null && !request.code().equalsIgnoreCase(role.getCode())) {
            badRequest("Role codes are immutable");
        }
        if (request.scope() != null && request.scope() != role.getScope()) {
            badRequest("Role scope is immutable");
        }
        String name = normalizeName(request.name());
        Set<PermissionDefinition> next = resolvePermissions(request.permissions(), role.getScope());
        // The legacy ADMIN row may contain a ROLE_MANAGE join entry, but it is not
        // an editable grant. Preserve it without exposing it or accepting changes;
        // recovery remains independently protected by the recovery-capability flag.
        role.getPermissions().stream()
            .filter(permission -> "ROLE_MANAGE".equals(permission.getKey()))
            .forEach(next::add);
        Set<String> before = permissionKeys(role);
        Set<String> after = keys(next);
        role.setName(name);
        if (request.description() != null) role.setDescription(normalizeDescription(request.description()));
        role.setPermissions(next);
        AuthorizationRole saved = roles.saveAndFlush(role);
        audit.createLog("ROLE_UPDATED", "targetType=ROLE; roleId=" + roleId
            + "; addedPermissions=" + difference(after, before)
            + "; removedPermissions=" + difference(before, after), null, actorId(actorEmail));
        return response(saved);
    }

    @Transactional
    public RoleResponseDTO retire(Long roleId, String actorEmail) {
        recoveryGuard.lockInvariant();
        requireRoleManager(actorEmail);
        recoveryGuard.requireRecoveryAccount();
        AuthorizationRole role = roles.findByIdForUpdate(roleId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Role not found"));
        if ("ADMIN".equalsIgnoreCase(role.getCode())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "The protected ADMIN recovery role cannot be deleted");
        }
        long globalUsers = users.countByRole(role.getCode());
        long projectMembers = projectAssignments.countByRole_Id(role.getId());
        long teamMembers = teamAssignments.countByRole_Id(role.getId());
        if (globalUsers + projectMembers + teamMembers > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Role is still assigned (" + globalUsers + " global accounts, " + projectMembers
                    + " project assignments, " + teamMembers
                    + " team assignments). Reassign or remove those assignments before deleting it.");
        }
        if (!role.isRetired()) {
            role.setRetired(true);
            roles.save(role);
            audit.createLog("ROLE_RETIRED", "targetType=ROLE; roleId=" + roleId + "; code=" + role.getCode(),
                null, actorId(actorEmail));
        }
        return response(role);
    }

    @Transactional
    public void assignGlobalRole(Long userId, Long roleId, String actorEmail) {
        recoveryGuard.lockInvariant();
        requireRoleManager(actorEmail);
        recoveryGuard.requireRecoveryAccount();
        User actor = requireUser(actorEmail);
        User target = users.findById(userId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        if (actor.getId().equals(target.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Administrators cannot change their own global role");
        }
        AuthorizationRole role = assignableRole(roleId, PermissionScope.GLOBAL);
        String old = target.getRole();
        if (!role.getCode().equals(old)) {
            boolean targetWasRecovery = recoveryCapability.hasCapability(target);
            target.setRole(role.getCode());
            users.saveAndFlush(target);
            if (targetWasRecovery && !recoveryCapability.hasCapability(target)) {
                recoveryGuard.requireRecoveryAccount();
            }
            audit.createLog("ROLE_CHANGED", "targetType=USER; targetId=" + userId
                + "; oldRole=" + old + "; newRole=" + role.getCode(), null, actor.getId());
            if (notifications != null && target.isEnabled()) notifications.createNotification(target,
                "ROLE_CHANGED", "Your workspace role changed to '" + role.getName() + "'.", "/team");
        }
    }

    @Transactional
    public RoleResponseDTO assignProjectRole(Long projectId, Long userId, Long roleId, String actorEmail) {
        effectivePermissions.require(actorEmail, "PROJECT_ROLE_MANAGE", projectId, null);
        Project project = projects.findById(projectId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found"));
        User target = requireTarget(userId);
        AuthorizationRole role = assignableRole(roleId, PermissionScope.PROJECT);
        if (project.getTeam() != null && !isTeamMember(project.getTeam(), userId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "A user must already belong to the project's team before receiving a project role");
        }
        requireNoPrivilegeEscalation(actorEmail, role, projectId, null);
        ProjectRoleAssignment assignment = projectAssignments.findAssignment(userId, projectId)
            .orElseGet(() -> new ProjectRoleAssignment(project, target, role));
        assignment.setRole(role);
        projectAssignments.save(assignment);
        audit.createLog("PROJECT_ROLE_ASSIGNED", "targetType=PROJECT; targetId=" + projectId
            + "; userId=" + userId + "; roleCode=" + role.getCode(), null, actorId(actorEmail));
        if (notifications != null && target.isEnabled() && !target.getId().equals(actorId(actorEmail))) {
            notifications.createNotification(target, "PROJECT_ROLE_ASSIGNED",
                "You were assigned the '" + role.getName() + "' role in project '" + project.getName() + "'.",
                NotificationTargetPaths.project(projectId));
        }
        return new RoleResponseDTO(role);
    }

    @Transactional
    public void removeProjectRole(Long projectId, Long userId, String actorEmail) {
        effectivePermissions.require(actorEmail, "PROJECT_ROLE_MANAGE", projectId, null);
        projectAssignments.findAssignment(userId, projectId).ifPresent(assignment -> {
            projectAssignments.delete(assignment);
            audit.createLog("PROJECT_ROLE_REMOVED", "targetType=PROJECT; targetId=" + projectId
                + "; userId=" + userId + "; roleId=" + assignment.getRole().getId(), null, actorId(actorEmail));
            if (notifications != null && assignment.getUser().isEnabled()
                    && !assignment.getUser().getId().equals(actorId(actorEmail))) notifications.createNotification(
                assignment.getUser(), "PROJECT_ROLE_REMOVED",
                "Your project role was removed from project '" + assignment.getProject().getName() + "'.",
                NotificationTargetPaths.project(projectId));
        });
    }

    @Transactional
    public RoleResponseDTO assignTeamRole(Long teamId, Long userId, Long roleId, String actorEmail) {
        effectivePermissions.require(actorEmail, "TEAM_ROLE_MANAGE", null, teamId);
        Team team = teams.findDetailedById(teamId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Team not found"));
        User target = requireTarget(userId);
        if (!isTeamMember(team, userId)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "User must already be a team member");
        AuthorizationRole role = assignableRole(roleId, PermissionScope.PROJECT_TEAM);
        requireNoPrivilegeEscalation(actorEmail, role, null, teamId);
        TeamRoleAssignment assignment = teamAssignments.findAssignment(userId, teamId)
            .orElseGet(() -> new TeamRoleAssignment(team, target, role));
        assignment.setRole(role);
        teamAssignments.save(assignment);
        audit.createLog("TEAM_ROLE_ASSIGNED", "targetType=TEAM; targetId=" + teamId
            + "; userId=" + userId + "; roleCode=" + role.getCode(), null, actorId(actorEmail));
        if (notifications != null && target.isEnabled() && !target.getId().equals(actorId(actorEmail))) {
            notifications.createNotification(target, "TEAM_ROLE_ASSIGNED",
                "You were assigned the '" + role.getName() + "' role in team '" + team.getName() + "'.",
                NotificationTargetPaths.team(teamId));
        }
        return new RoleResponseDTO(role);
    }

    @Transactional
    public void removeTeamRole(Long teamId, Long userId, String actorEmail) {
        effectivePermissions.require(actorEmail, "TEAM_ROLE_MANAGE", null, teamId);
        teamAssignments.findAssignment(userId, teamId).ifPresent(assignment -> {
            teamAssignments.delete(assignment);
            audit.createLog("TEAM_ROLE_REMOVED", "targetType=TEAM; targetId=" + teamId
                + "; userId=" + userId + "; roleId=" + assignment.getRole().getId(), null, actorId(actorEmail));
            if (notifications != null && assignment.getUser().isEnabled()
                    && !assignment.getUser().getId().equals(actorId(actorEmail))) notifications.createNotification(
                assignment.getUser(), "TEAM_ROLE_REMOVED",
                "Your team role was removed from team '" + assignment.getTeam().getName() + "'.",
                NotificationTargetPaths.team(teamId));
        });
    }

    private void requireNoPrivilegeEscalation(String actorEmail, AuthorizationRole targetRole, Long projectId, Long teamId) {
        User actor = requireUser(actorEmail);
        if (recoveryCapability.hasCapability(actor)) return;
        Set<String> actorPermissions = effectivePermissions.permissionsAt(actorEmail, projectId, teamId);
        if (!actorPermissions.containsAll(keys(targetRole.getPermissions()))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "You cannot assign a role with permissions you do not hold in this scope");
        }
    }

    @Transactional
    public void setRecoveryCapability(Long targetUserId, boolean enabled, String actorEmail) {
        recoveryGuard.lockInvariant();
        User actor = requireUser(actorEmail);
        if (!recoveryCapability.hasCapability(actor)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "Only an existing recovery-capable administrator can change recovery assignments");
        }
        User target = users.findById(targetUserId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        if (enabled && !target.isEnabled()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Recovery capability can only be assigned to an enabled account");
        }
        if (!enabled && recoveryCapability.isConfiguredBootstrapAdmin(target)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "The configured bootstrap administrator retains protected recovery capability");
        }
        if (target.hasAdminRecovery() == enabled) return;
        target.setAdminRecovery(enabled);
        users.saveAndFlush(target);
        recoveryGuard.requireRecoveryAccount();
        audit.createLog("ADMIN_RECOVERY_CAPABILITY_CHANGED", "targetType=USER; targetId=" + targetUserId
            + "; enabled=" + enabled, null, actor.getId());
    }

    private Set<PermissionDefinition> resolvePermissions(Set<String> requested, PermissionScope roleScope) {
        if (requested == null) badRequest("Permission keys are required");
        Set<PermissionDefinition> result = new LinkedHashSet<>();
        for (String key : requested) {
            if ("ROLE_MANAGE".equals(key)) {
                badRequest("Role management is granted only through the protected recovery capability");
            }
            PermissionCatalogue.Entry supported = PermissionCatalogue.find(key);
            if (supported == null) badRequest("Unsupported permission key: " + key);
            if (roleScope != PermissionScope.GLOBAL && supported.scope() != roleScope) {
                badRequest("Permission " + key + " is not valid for scope " + roleScope);
            }
            PermissionDefinition definition = permissionDefinitions.findById(key)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                    "Supported permission catalogue is not initialized"));
            result.add(definition);
        }
        return result;
    }

    private AuthorizationRole assignableRole(Long roleId, PermissionScope scope) {
        AuthorizationRole role = roles.findByIdForUpdate(roleId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Role not found"));
        if (role.isRetired() || role.getScope() != scope) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Role is retired or has the wrong scope");
        }
        return role;
    }

    private User requireTarget(Long userId) {
        User user = users.findById(userId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        if (!user.isEnabled()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Disabled users cannot receive roles");
        return user;
    }

    private boolean isTeamMember(Team team, Long userId) {
        return (team.getManager() != null && userId.equals(team.getManager().getId()))
            || team.getMembers().stream().anyMatch(member -> userId.equals(member.getId()));
    }

    private User requireUser(String email) { return effectivePermissions.requireEnabledUser(email); }

    private void requireRoleManager(String email) {
        effectivePermissions.require(email, "ROLE_MANAGE", null, null);
    }

    private Long actorId(String email) { return requireUser(email).getId(); }
    private String normalizeCode(String code) { return code == null ? "" : code.trim().toUpperCase(Locale.ROOT); }
    private String normalizeName(String name) {
        if (name == null || name.isBlank() || name.trim().length() > 120) badRequest("Role name is required and must be at most 120 characters");
        return name.trim();
    }
    private String normalizeDescription(String description) {
        if (description == null || description.isBlank()) return null;
        if (description.trim().length() > 250) badRequest("Role description must be at most 250 characters");
        return description.trim();
    }
    private RoleResponseDTO response(AuthorizationRole role) {
        return new RoleResponseDTO(role.getId(), role.getCode(), role.getName(), role.getScopeType(), role.isRetired(),
            role.isSystemRole(), permissionKeys(role).stream().filter(key -> !"ROLE_MANAGE".equals(key))
                .collect(java.util.stream.Collectors.toSet()), role.getCreatedAt(), role.getUpdatedAt(), role.getDescription(),
            users.countByRole(role.getCode()), projectAssignments.countByRole_Id(role.getId()),
            teamAssignments.countByRole_Id(role.getId()));
    }
    private Set<String> permissionKeys(AuthorizationRole role) {
        return role.getPermissions().stream().map(PermissionDefinition::getKey).collect(java.util.stream.Collectors.toSet());
    }
    private Set<String> keys(Set<PermissionDefinition> definitions) {
        return definitions.stream().map(PermissionDefinition::getKey).collect(java.util.stream.Collectors.toSet());
    }
    private String difference(Set<String> left, Set<String> right) {
        Set<String> result = new java.util.TreeSet<>(left);
        result.removeAll(right);
        return String.join(",", result);
    }
    private void badRequest(String message) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
}
