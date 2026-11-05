package com.devflow.devflow.service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.devflow.devflow.dto.UserMembershipsResponseDTO;
import com.devflow.devflow.dto.UserMembershipsResponseDTO.ProjectMembership;
import com.devflow.devflow.dto.UserMembershipsResponseDTO.TeamMembership;
import com.devflow.devflow.entity.Team;
import com.devflow.devflow.repository.ProjectRoleAssignmentRepository;
import com.devflow.devflow.repository.TeamRepository;
import com.devflow.devflow.repository.TeamRoleAssignmentRepository;
import com.devflow.devflow.repository.UserRepository;

@Service
public class UserMembershipService {
    private final UserRepository users;
    private final TeamRepository teams;
    private final ProjectRoleAssignmentRepository projectAssignments;
    private final TeamRoleAssignmentRepository teamAssignments;
    private final EffectivePermissionService permissions;

    public UserMembershipService(UserRepository users, TeamRepository teams,
            ProjectRoleAssignmentRepository projectAssignments,
            TeamRoleAssignmentRepository teamAssignments, EffectivePermissionService permissions) {
        this.users = users;
        this.teams = teams;
        this.projectAssignments = projectAssignments;
        this.teamAssignments = teamAssignments;
        this.permissions = permissions;
    }

    @Transactional(readOnly = true)
    public UserMembershipsResponseDTO getMemberships(Long userId, String actorEmail) {
        permissions.require(actorEmail, "USER_MANAGE", null, null);
        var target = users.findById(userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        List<ProjectMembership> projectMemberships = projectAssignments.findByUserId(userId).stream()
            .map(a -> new ProjectMembership(a.getProject().getId(), a.getProject().getName(),
                a.getRole().getId(), a.getRole().getName()))
            .sorted(java.util.Comparator.comparing(ProjectMembership::projectName,
                String.CASE_INSENSITIVE_ORDER)).toList();

        // Populate team memberships from the team-member relationship, which also includes
        // members who have not been given a separate scoped team role.
        var assignments = teamAssignments.findByUserId(userId);
        Map<Long, com.devflow.devflow.entity.TeamRoleAssignment> roleByTeam = assignments.stream()
            .collect(Collectors.toMap(a -> a.getTeam().getId(), Function.identity(), (first, ignored) -> first));
        List<TeamMembership> teamMemberships = teams.findAllDetailed().stream()
            .filter(team -> team.getMembers().stream().anyMatch(member -> member.getId().equals(target.getId())))
            .map(team -> teamMembership(team, roleByTeam.get(team.getId()), target.getId()))
            .sorted(java.util.Comparator.comparing(TeamMembership::teamName,
                String.CASE_INSENSITIVE_ORDER)).toList();

        return new UserMembershipsResponseDTO(projectMemberships, teamMemberships);
    }

    private TeamMembership teamMembership(Team team,
            com.devflow.devflow.entity.TeamRoleAssignment assignment, Long userId) {
        boolean manager = team.getManager() != null && userId.equals(team.getManager().getId());
        return new TeamMembership(team.getId(), team.getName(), assignment == null ? null : assignment.getRole().getId(),
            assignment == null ? null : assignment.getRole().getName(), manager);
    }
}
