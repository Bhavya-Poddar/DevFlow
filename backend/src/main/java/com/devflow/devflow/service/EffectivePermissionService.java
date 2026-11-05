package com.devflow.devflow.service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.devflow.devflow.dto.EffectivePermissionsResponseDTO;
import com.devflow.devflow.entity.AuthorizationRole;
import com.devflow.devflow.entity.Project;
import com.devflow.devflow.entity.Team;
import com.devflow.devflow.entity.User;
import com.devflow.devflow.repository.AuthorizationRoleRepository;
import com.devflow.devflow.repository.ProjectRepository;
import com.devflow.devflow.repository.ProjectRoleAssignmentRepository;
import com.devflow.devflow.repository.TeamRepository;
import com.devflow.devflow.repository.TeamRoleAssignmentRepository;
import com.devflow.devflow.repository.UserRepository;

/** Resolves database-backed capabilities at request time; role names are never authorization rules. */
@Service
public class EffectivePermissionService {
    private final UserRepository users;
    private final AuthorizationRoleRepository roles;
    private final ProjectRoleAssignmentRepository projectAssignments;
    private final TeamRoleAssignmentRepository teamAssignments;
    private final ProjectRepository projects;
    private final TeamRepository teams;
    private final AdminRecoveryCapability recoveryCapability;

    public EffectivePermissionService(UserRepository users, AuthorizationRoleRepository roles,
            ProjectRoleAssignmentRepository projectAssignments, TeamRoleAssignmentRepository teamAssignments,
            ProjectRepository projects, TeamRepository teams, AdminRecoveryCapability recoveryCapability) {
        this.users = users; this.roles = roles; this.projectAssignments = projectAssignments;
        this.teamAssignments = teamAssignments; this.projects = projects; this.teams = teams;
        this.recoveryCapability = recoveryCapability;
    }

    @Transactional(readOnly = true)
    public User requireEnabledUser(String email) {
        User user = users.findByEmailIgnoreCase(email)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authenticated account is unavailable"));
        if (!user.isEnabled()) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authenticated account is disabled");
        return user;
    }

    @Transactional(readOnly = true)
    public Set<String> globalPermissions(String email) {
        User user = requireEnabledUser(email);
        AuthorizationRole role = roles.findByCode(user.getRole())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Account role is unavailable"));
        if (role.getScope() != com.devflow.devflow.entity.PermissionScope.GLOBAL) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Account role is unavailable");
        }
        Set<String> keys = permissionKeys(role);
        // Role management is the protected recovery capability, not an ordinary editable grant.
        // Keeping the catalogue key visible preserves the role DTO contract, but no role definition
        // can confer it on an account that lacks the separately managed recovery flag.
        keys.remove("ROLE_MANAGE");
        if (recoveryCapability.hasCapability(user)) keys.add("ROLE_MANAGE");
        return keys;
    }

    @Transactional(readOnly = true)
    public boolean has(String email, String permission, Long projectId, Long teamId) {
        User user = requireEnabledUser(email);
        if ("ROLE_MANAGE".equals(permission)) return recoveryCapability.hasCapability(user);
        Set<String> global = globalPermissions(email);
        Project project = null;
        if (projectId != null) {
            project = projects.findById(projectId).orElse(null);
            if (project == null) return false;
            // Project-scoped grants must not outlive access to a private team's project.
            if (project.getTeam() != null && !global.contains("PROJECT_READ_ALL")
                    && !isMember(project.getTeam(), user.getId())) return false;
            if (global.contains(permission)) return true;
            if (projectAssignments.findByUserIdAndProjectId(user.getId(), projectId).stream()
                    .anyMatch(a -> a.getRole().getScope() == com.devflow.devflow.entity.PermissionScope.PROJECT
                        && permissionKeys(a.getRole()).contains(permission))) return true;
            if (project != null && project.getTeam() != null) {
                teamId = project.getTeam().getId();
            }
        }
        if (projectId == null && global.contains(permission)) return true;
        if (teamId != null) {
            Team team = teams.findDetailedById(teamId).orElse(null);
            if (team == null || !isMember(team, user.getId())) return false;
            // Membership has always included read-only access to team activity; preserve that
            // contract while allowing the configurable role grants to add other capabilities.
            if (Set.of("TEAM_READ", "TEAM_ACTIVITY_READ").contains(permission)) return true;
            if (team.getManager() != null && user.getId().equals(team.getManager().getId())
                    && Set.of("TEAM_MANAGE", "TEAM_ROLE_MANAGE", "TEAM_ACTIVITY_READ").contains(permission)) {
                return true;
            }
            return teamAssignments.findByUserIdAndTeamId(user.getId(), teamId).stream()
                .anyMatch(a -> a.getRole().getScope() == com.devflow.devflow.entity.PermissionScope.PROJECT_TEAM
                    && permissionKeys(a.getRole()).contains(permission));
        }
        return false;
    }

    @Transactional(readOnly = true)
    public void require(String email, String permission, Long projectId, Long teamId) {
        if (!has(email, permission, projectId, teamId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Required permission is not granted");
        }
    }

    @Transactional(readOnly = true)
    public Set<String> permissionsAt(String email, Long projectId, Long teamId) {
        Set<String> result = new java.util.HashSet<>(globalPermissions(email));
        User user = requireEnabledUser(email);
        if (projectId != null) {
            Project project = projects.findById(projectId).orElse(null);
            if (project != null && (project.getTeam() == null || hasGlobal(email, "PROJECT_READ_ALL")
                    || isMember(project.getTeam(), user.getId()))) {
                projectAssignments.findByUserIdAndProjectId(user.getId(), projectId)
                    .forEach(a -> result.addAll(permissionKeys(a.getRole())));
                if (project.getTeam() != null) teamId = project.getTeam().getId();
            } else if (project != null) {
                return result;
            }
        }
        if (teamId != null) {
            Team team = teams.findDetailedById(teamId).orElse(null);
            if (team != null && isMember(team, user.getId())) {
                result.add("TEAM_READ");
                if (team.getManager() != null && user.getId().equals(team.getManager().getId())) {
                    result.addAll(Set.of("TEAM_MANAGE", "TEAM_ROLE_MANAGE", "TEAM_ACTIVITY_READ"));
                }
                teamAssignments.findByUserIdAndTeamId(user.getId(), teamId)
                    .forEach(a -> result.addAll(permissionKeys(a.getRole())));
            }
        }
        return result;
    }

    @Transactional(readOnly = true)
    public boolean hasAnywhere(String email, String permission) {
        if (globalPermissions(email).contains(permission)) return true;
        User user = requireEnabledUser(email);
        return projectAssignments.findByUserId(user.getId()).stream()
                .anyMatch(a -> permissionKeys(a.getRole()).contains(permission))
            || teamAssignments.findByUserId(user.getId()).stream()
                .anyMatch(a -> permissionKeys(a.getRole()).contains(permission));
    }

    @Transactional(readOnly = true)
    public boolean hasGlobal(String email, String permission) {
        return globalPermissions(email).contains(permission);
    }

    @Transactional(readOnly = true)
    public EffectivePermissionsResponseDTO effective(String email) {
        User user = requireEnabledUser(email);
        Set<String> global = globalPermissions(email);
        List<EffectivePermissionsResponseDTO.ScopedPermissions> project = projectAssignments.findByUserId(user.getId())
            .stream().filter(a -> a.getProject().getTeam() == null
                || global.contains("PROJECT_READ_ALL") || isMember(a.getProject().getTeam(), user.getId()))
            .map(a -> new EffectivePermissionsResponseDTO.ScopedPermissions(a.getProject().getId(),
                a.getRole().getCode(), permissionKeys(a.getRole()))).toList();
        List<EffectivePermissionsResponseDTO.ScopedPermissions> team = teamAssignments.findByUserId(user.getId())
            .stream().filter(a -> isMember(a.getTeam(), user.getId()))
            .map(a -> new EffectivePermissionsResponseDTO.ScopedPermissions(a.getTeam().getId(),
                a.getRole().getCode(), permissionKeys(a.getRole()))).toList();
        return new EffectivePermissionsResponseDTO(global, project, team, recoveryCapability.hasCapability(user));
    }

    @Transactional(readOnly = true)
    public boolean hasRecoveryCapability(String email) {
        return recoveryCapability.hasCapability(requireEnabledUser(email));
    }

    @Transactional(readOnly = true)
    public boolean hasProjectRole(String email, Long projectId, String roleCode) {
        User user = requireEnabledUser(email);
        return projectAssignments.findByUserIdAndProjectId(user.getId(), projectId).stream()
            .anyMatch(a -> !a.getRole().isRetired() && roleCode.equals(a.getRole().getCode()));
    }

    @Transactional(readOnly = true)
    public boolean hasTeamRole(String email, Long teamId, String roleCode) {
        User user = requireEnabledUser(email);
        return teamAssignments.findByUserIdAndTeamId(user.getId(), teamId).stream()
            .anyMatch(a -> !a.getRole().isRetired() && roleCode.equals(a.getRole().getCode()));
    }

    private Set<String> permissionKeys(AuthorizationRole role) {
        return role.getPermissions().stream().map(p -> p.getKey()).collect(Collectors.toSet());
    }

    private boolean isMember(Team team, Long userId) {
        if (team.getManager() != null && userId.equals(team.getManager().getId())) return true;
        return team.getMembers().stream().anyMatch(member -> userId.equals(member.getId()));
    }
}
