package com.devflow.devflow.service;

import java.util.LinkedHashSet;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.devflow.devflow.dto.TeamCreateRequestDTO;
import com.devflow.devflow.dto.TeamResponseDTO;
import com.devflow.devflow.entity.Team;
import com.devflow.devflow.entity.User;
import com.devflow.devflow.repository.TeamRepository;
import com.devflow.devflow.repository.UserRepository;
import com.devflow.devflow.repository.ProjectRepository;
import com.devflow.devflow.entity.Project;
import com.devflow.devflow.dto.TeamCompositionRequestDTO;

@Service
public class TeamService {
    private final TeamRepository teamRepository;
    private final UserRepository userRepository;
    private final ProjectAccessService accessService;
    private final ProjectRepository projectRepository;
    private final EffectivePermissionService permissions;
    private final ProjectTeamProvisioningService teamProvisioningService;
    @org.springframework.beans.factory.annotation.Autowired private NotificationService notificationService;

    public TeamService(TeamRepository teamRepository, UserRepository userRepository,
            ProjectAccessService accessService, ProjectRepository projectRepository,
            EffectivePermissionService permissions, ProjectTeamProvisioningService teamProvisioningService) {
        this.teamRepository = teamRepository;
        this.userRepository = userRepository;
        this.accessService = accessService;
        this.projectRepository = projectRepository;
        this.permissions = permissions;
        this.teamProvisioningService = teamProvisioningService;
    }

    @Transactional(readOnly = true)
    public List<TeamResponseDTO> listTeams(String actorEmail) {
        User actor = accessService.requireUser(actorEmail);
        List<Team> teams = accessService.isTeamAdministrator(actor)
            ? teamRepository.findAllDetailed()
            : teamRepository.findVisibleToUser(actor.getId());
        return teams.stream().map(TeamResponseDTO::new).toList();
    }

    @Transactional(readOnly = true)
    public TeamResponseDTO getTeam(Long teamId, String actorEmail) {
        return new TeamResponseDTO(accessService.requireTeamAccess(teamId, actorEmail));
    }

    @Transactional
    public TeamResponseDTO createTeam(TeamCreateRequestDTO request, String actorEmail) {
        User actor = accessService.requireUser(actorEmail);
        accessService.requireGlobalPermission(actorEmail, "TEAM_CREATE");
        if (request == null || request.getProjectId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Select a project for this team");
        }
        Project project = projectRepository.findById(request.getProjectId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found"));
        accessService.requireProjectAccess(project, actorEmail);
        permissions.require(actorEmail, "PROJECT_UPDATE", project.getId(), null);
        if (project.getTeam() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This project already has a team");
        }
        String name = request == null || request.getName() == null ? "" : request.getName().trim();
        if (name.isBlank() || name.length() > 120) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Team name is required and must be at most 120 characters");
        }
        if (teamRepository.existsByNameIgnoreCase(name)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A team with this name already exists");
        }
        TeamCompositionRequestDTO composition = request.getComposition();
        if (composition == null) {
            composition = new TeamCompositionRequestDTO();
            composition.setManagerUserId(actor.getId());
            composition.setProjectManagerIds(List.of(actor.getId()));
        }
        Team team = teamProvisioningService.createForProject(project, name, composition);
        Team saved = teamRepository.findDetailedById(team.getId()).orElse(team);
        if (notificationService != null) notificationService.notifyTeamParticipants(saved, actor, "TEAM_CREATED",
            "Team '" + saved.getName() + "' was created for project '" + project.getName() + "'.",
            NotificationTargetPaths.team(saved.getId()));
        return new TeamResponseDTO(saved);
    }

    @Transactional
    public TeamResponseDTO addDeveloper(Long teamId, Long developerId, String responsibility, String actorEmail) {
        Team team = team(teamId);
        User actor = accessService.requireUser(actorEmail);
        accessService.requireCanManageTeam(team, actor);
        User developer = userRepository.findById(developerId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Developer not found"));
        if (team.getMembers().stream().anyMatch(member -> developerId.equals(member.getId()))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "User is already a team member; change their responsibility explicitly");
        }
        teamProvisioningService.addMember(team,developer,responsibility);
        if (notificationService != null) {
            String role = responsibility.replace('_', ' ').toLowerCase();
            notificationService.notifyUsers(List.of(developer), actor, "TEAM_MEMBERSHIP_ADDED",
                "You were added to team '" + team.getName() + "' as a " + role + ".",
                NotificationTargetPaths.team(team.getId()));
            notificationService.notifyTeamParticipantsExcept(team, actor, developer.getId(), "TEAM_MEMBER_ADDED",
                developer.getName() + " joined team '" + team.getName() + "' as a " + role + ".",
                NotificationTargetPaths.team(team.getId()));
        }
        return new TeamResponseDTO(teamRepository.findDetailedById(teamId).orElse(team));
    }

    @Transactional
    public TeamResponseDTO removeDeveloper(Long teamId, Long developerId, String actorEmail) {
        Team team = team(teamId);
        User actor = accessService.requireUser(actorEmail);
        accessService.requireCanManageTeam(team, actor);
        if (team.getManager() != null && developerId.equals(team.getManager().getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "The team manager must remain a member");
        }
        if (team.getMembers().stream().noneMatch(member -> developerId.equals(member.getId()))) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Team developer not found");
        }
        teamProvisioningService.validateMemberRemoval(team, developerId);
        User removedUser = team.getMembers().stream().filter(member -> developerId.equals(member.getId())).findFirst().orElse(null);
        boolean removed = team.getMembers().removeIf(member -> developerId.equals(member.getId()));
        if (!removed) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Team developer not found");
        team = teamRepository.save(team);
        if (notificationService != null) notificationService.notifyUsers(removedUser == null ? List.of() : List.of(removedUser), actor,
            "TEAM_MEMBERSHIP_REMOVED", "You were removed from team '" + team.getName() + "'.", null);
        if (notificationService != null) notificationService.notifyTeamParticipants(team, actor, "TEAM_MEMBERSHIP_CHANGED",
            (removedUser == null ? "A member" : removedUser.getName()) + " was removed from team '" + team.getName() + "'.",
            NotificationTargetPaths.team(team.getId()));
        return new TeamResponseDTO(team);
    }

    private Team team(Long id) {
        return teamRepository.findDetailedById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Team not found"));
    }
}
