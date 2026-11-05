package com.devflow.devflow.service;

import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.devflow.devflow.dto.ProjectAssignableMemberDTO;
import com.devflow.devflow.entity.*;
import com.devflow.devflow.repository.*;

@Service
public class ProjectAssignableMemberService {
    private final ProjectAccessService access;
    private final EffectivePermissionService permissions;
    private final ProjectRepository projects;
    private final ProjectRoleAssignmentRepository projectRoles;
    private final TeamRoleAssignmentRepository teamRoles;
    private final UserRepository users;
    public ProjectAssignableMemberService(ProjectAccessService access,EffectivePermissionService permissions,
            ProjectRepository projects,ProjectRoleAssignmentRepository projectRoles,TeamRoleAssignmentRepository teamRoles,UserRepository users){
        this.access=access;this.permissions=permissions;this.projects=projects;this.projectRoles=projectRoles;this.teamRoles=teamRoles;this.users=users;
    }
    @Transactional(readOnly=true)
    public List<ProjectAssignableMemberDTO> list(Long projectId,String actorEmail){
        Project project=access.requireProjectAccess(projectId,actorEmail);
        List<User> candidates;
        if(project.getTeam()!=null){
            Team team=project.getTeam(); candidates=new ArrayList<>(team.getMembers());
            if(team.getManager()!=null&&!candidates.stream().anyMatch(u->u.getId().equals(team.getManager().getId())))candidates.add(team.getManager());
        } else candidates=users.findByRoleInAndEnabledTrue(List.of("DEVELOPER","TESTER"));
        Map<Long,String> projectRole=projectRoles.findByProjectId(projectId).stream().collect(java.util.stream.Collectors.toMap(a->a.getUser().getId(),a->a.getRole().getCode(),(a,b)->a));
        Map<Long,String> teamRole=project.getTeam()==null?Map.of():teamRoles.findByTeamId(project.getTeam().getId()).stream()
            .collect(java.util.stream.Collectors.toMap(a->a.getUser().getId(),a->a.getRole().getCode(),(a,b)->a));
        return candidates.stream().filter(User::isEnabled).map(user->{
            String pr=projectRole.get(user.getId()),tr=teamRole.get(user.getId());
            boolean dev=project.getTeam()!=null?"DEVELOPER_PROJECT".equals(pr)&&"DEVELOPER_TEAM".equals(tr):permissions.hasGlobal(user.getEmail(),"ASSIGNABLE_DEVELOPER");
            boolean tester=project.getTeam()!=null?"TESTER_PROJECT".equals(pr)&&"TESTER_TEAM".equals(tr):permissions.hasGlobal(user.getEmail(),"ASSIGNABLE_TESTER");
            return new ProjectAssignableMemberDTO(user.getId(),user.getName(),user.getRole(),user.getAvatarUrl(),dev,tester);
        }).filter(user->user.developer()||user.tester()).sorted(Comparator.comparing(ProjectAssignableMemberDTO::name,String.CASE_INSENSITIVE_ORDER)).toList();
    }
}
