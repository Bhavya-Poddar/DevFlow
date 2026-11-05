package com.devflow.devflow.service;

import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import com.devflow.devflow.dto.TeamCompositionRequestDTO;
import com.devflow.devflow.entity.*;
import com.devflow.devflow.repository.*;

/** Creates the one explicit, role-complete team associated with a project. */
@Service
public class ProjectTeamProvisioningService {
    private final TeamRepository teams;
    private final ProjectRepository projects;
    private final UserRepository users;
    private final AuthorizationRoleRepository roles;
    private final ProjectRoleAssignmentRepository projectAssignments;
    private final TeamRoleAssignmentRepository teamAssignments;

    public ProjectTeamProvisioningService(TeamRepository teams, ProjectRepository projects, UserRepository users,
            AuthorizationRoleRepository roles, ProjectRoleAssignmentRepository projectAssignments,
            TeamRoleAssignmentRepository teamAssignments) {
        this.teams=teams; this.projects=projects; this.users=users; this.roles=roles;
        this.projectAssignments=projectAssignments; this.teamAssignments=teamAssignments;
    }

    @Transactional
    public Team createForProject(Project project, String name, TeamCompositionRequestDTO composition) {
        if (project == null || project.getId() == null) badRequest("A saved project is required for a team");
        if (project.getTeam() != null) throw new ResponseStatusException(HttpStatus.CONFLICT, "Project already has a team");
        if (name == null || name.isBlank() || name.trim().length() > 120) badRequest("Team name is required");
        if (teams.existsByNameIgnoreCase(name.trim())) throw new ResponseStatusException(HttpStatus.CONFLICT, "A team with this name already exists");
        validateComposition(composition);

        List<Long> all = new ArrayList<>();
        all.addAll(composition.getProjectManagerIds()); all.addAll(composition.getTeamLeadIds());
        all.addAll(composition.getDeveloperIds()); all.addAll(composition.getTesterIds());
        if (all.stream().anyMatch(Objects::isNull) || new HashSet<>(all).size() != all.size()) {
            badRequest("Each person must have one responsibility in this project team");
        }
        if (!composition.getProjectManagerIds().contains(composition.getManagerUserId())) {
            badRequest("The designated team manager must also be selected as a project manager");
        }

        Map<Long, User> selected = new LinkedHashMap<>();
        for (Long id : all) {
            User user = users.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Selected team member not found"));
            if (!user.isEnabled()) badRequest("Disabled users cannot be assigned to a project team");
            selected.put(id, user);
        }
        Team team = new Team();
        team.setName(name.trim());
        team.setManager(selected.get(composition.getManagerUserId()));
        team.setMembers(new LinkedHashSet<>(selected.values()));
        team = teams.saveAndFlush(team);
        project.setTeam(team);
        team.setProject(project);
        projects.save(project);

        for (Long id : composition.getProjectManagerIds()) assignProject(project, selected.get(id), "PROJECT_MANAGER_PROJECT");
        for (Long id : composition.getTeamLeadIds()) {
            assignProject(project, selected.get(id), "TEAM_LEAD_PROJECT");
            assignTeam(team, selected.get(id), "TEAM_LEAD_TEAM");
        }
        for (Long id : composition.getDeveloperIds()) {
            assignProject(project, selected.get(id), "DEVELOPER_PROJECT");
            assignTeam(team, selected.get(id), "DEVELOPER_TEAM");
        }
        for (Long id : composition.getTesterIds()) {
            assignProject(project, selected.get(id), "TESTER_PROJECT");
            assignTeam(team, selected.get(id), "TESTER_TEAM");
        }
        assignTeam(team, selected.get(composition.getManagerUserId()), "PROJECT_MANAGER_TEAM");
        return team;
    }

    @Transactional
    public void addMember(Team team, User user, String responsibility) {
        Project project=projects.findByTeam_Id(team.getId()).orElseThrow(
            () -> new ResponseStatusException(HttpStatus.CONFLICT,"This legacy team has not been mapped to a project"));
        if(!user.isEnabled())badRequest("Disabled users cannot be assigned to a project team");
        String teamCode=responsibility==null?"":responsibility.trim().toUpperCase(Locale.ROOT);
        String projectCode=switch(teamCode){case "PROJECT_MANAGER_TEAM"->"PROJECT_MANAGER_PROJECT";case "TEAM_LEAD_TEAM"->"TEAM_LEAD_PROJECT";case "DEVELOPER_TEAM"->"DEVELOPER_PROJECT";case "TESTER_TEAM"->"TESTER_PROJECT";default->throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Choose a supported team responsibility");};
        AuthorizationRole projectRole=role(projectCode,PermissionScope.PROJECT);
        AuthorizationRole teamRole=role(teamCode,PermissionScope.PROJECT_TEAM);
        ProjectRoleAssignment projectAssignment=projectAssignments.findAssignment(user.getId(),project.getId())
            .orElseGet(()->new ProjectRoleAssignment(project,user,projectRole));
        projectAssignment.setRole(projectRole);projectAssignments.save(projectAssignment);
        TeamRoleAssignment teamAssignment=teamAssignments.findAssignment(user.getId(),team.getId())
            .orElseGet(()->new TeamRoleAssignment(team,user,teamRole));
        teamAssignment.setRole(teamRole);teamAssignments.save(teamAssignment);
        team.getMembers().add(user);teams.save(team);
    }

    @Transactional(readOnly=true)
    public void validateExistingMapping(Project project, Team team) {
        validateExistingMapping(project, team, null);
    }

    @Transactional(readOnly=true)
    public void validateMemberRemoval(Team team, Long removedUserId) {
        projects.findByTeam_Id(team.getId()).ifPresent(project ->
            validateExistingMapping(project, team, removedUserId));
    }

    @Transactional(readOnly=true)
    public void validateUserDeactivation(Long userId) {
        Set<Long> teamIds = new HashSet<>();
        teamAssignments.findByUserId(userId).stream().map(a -> a.getTeam().getId()).forEach(teamIds::add);
        teams.findManagedByUser(userId).stream().map(Team::getId).forEach(teamIds::add);
        for (Long teamId : teamIds) {
            Team team = teams.findDetailedById(teamId).orElse(null);
            if (team == null || team.getMembers().stream().noneMatch(m -> m.getId().equals(userId))) continue;
            projects.findByTeam_Id(teamId).ifPresent(project ->
                validateExistingMapping(project, team, userId));
        }
    }

    private void validateExistingMapping(Project project, Team team, Long excludedUserId) {
        List<TeamRoleAssignment> assignments=teamAssignments.findAll().stream()
            .filter(a->a.getTeam().getId().equals(team.getId())).toList();
        List<ProjectRoleAssignment> projectRolesForTeam=projectAssignments.findByProjectId(project.getId());
        boolean manager=team.getManager()!=null && isEligible(team.getManager(), excludedUserId)
            && team.getMembers().stream().anyMatch(m->m.getId().equals(team.getManager().getId()))
            && projectRolesForTeam.stream().anyMatch(a->a.getUser().getId().equals(team.getManager().getId())
                && isEligible(a.getUser(), excludedUserId)
                && "PROJECT_MANAGER_PROJECT".equals(a.getRole().getCode()));
        boolean lead=matchesResponsibility(assignments,projectRolesForTeam,"TEAM_LEAD_TEAM","TEAM_LEAD_PROJECT",excludedUserId);
        boolean developer=matchesResponsibility(assignments,projectRolesForTeam,"DEVELOPER_TEAM","DEVELOPER_PROJECT",excludedUserId);
        boolean tester=matchesResponsibility(assignments,projectRolesForTeam,"TESTER_TEAM","TESTER_PROJECT",excludedUserId);
        if(!manager||!lead||!developer||!tester)throw new ResponseStatusException(HttpStatus.CONFLICT,
            "This change would leave the active project team without an enabled Project Manager, Team Lead, Developer, or Tester.");
    }

    private boolean matchesResponsibility(List<TeamRoleAssignment> team,List<ProjectRoleAssignment> project,String teamCode,String projectCode,Long excludedUserId){
        return team.stream().filter(a->teamCode.equals(a.getRole().getCode()) && isEligible(a.getUser(),excludedUserId))
            .anyMatch(ta->ta.getTeam().getMembers().stream().anyMatch(member->member.getId().equals(ta.getUser().getId()))
                && project.stream().anyMatch(pa->ta.getUser().getId().equals(pa.getUser().getId())
                    && isEligible(pa.getUser(),excludedUserId)
                    && projectCode.equals(pa.getRole().getCode())));
    }

    private boolean isEligible(User user, Long excludedUserId) {
        return user != null && user.isEnabled() && !Objects.equals(user.getId(), excludedUserId);
    }

    public void validateComposition(TeamCompositionRequestDTO c) {
        if (c == null || c.getManagerUserId() == null || empty(c.getProjectManagerIds())
                || empty(c.getTeamLeadIds()) || empty(c.getDeveloperIds()) || empty(c.getTesterIds())) {
            badRequest("Team requires at least one Project Manager, Team Lead, Developer, and Tester");
        }
    }
    private boolean empty(List<Long> values) { return values == null || values.isEmpty(); }
    private void assignProject(Project project, User user, String code) {
        projectAssignments.save(new ProjectRoleAssignment(project, user, role(code, PermissionScope.PROJECT)));
    }
    private void assignTeam(Team team, User user, String code) {
        teamAssignments.save(new TeamRoleAssignment(team, user, role(code, PermissionScope.PROJECT_TEAM)));
    }
    private AuthorizationRole role(String code, PermissionScope expected) {
        AuthorizationRole role=roles.findByCodeAndRetiredFalse(code)
            .orElseThrow(() -> new IllegalStateException("Required built-in scoped role is unavailable: " + code));
        if (role.getScope()!=expected) throw new IllegalStateException("Required built-in scoped role has an invalid scope: " + code);
        return role;
    }
    private void badRequest(String message) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
}
