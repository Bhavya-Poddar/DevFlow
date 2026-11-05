package com.devflow.devflow;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.devflow.devflow.dto.ProjectCreateRequestDTO;
import com.devflow.devflow.dto.ProjectUpdateRequestDTO;
import com.devflow.devflow.dto.TeamCompositionRequestDTO;
import com.devflow.devflow.entity.Project;
import com.devflow.devflow.entity.Team;
import com.devflow.devflow.repository.ProjectRepository;
import com.devflow.devflow.service.AuditLogServices;
import com.devflow.devflow.service.ProjectService;
import com.devflow.devflow.service.ProjectAccessService;
import com.devflow.devflow.service.ProjectTeamProvisioningService;

class ProjectAuditTests {

    @Test
    void projectLifecycleAuditsAuthenticatedActorAndTargetIds() {
        ProjectRepository projects = mock(ProjectRepository.class);
        AuditLogServices audit = mock(AuditLogServices.class);
        when(audit.findActorId("manager@example.test")).thenReturn(6L);
        when(projects.save(any(Project.class))).thenAnswer(call -> {
            Project project = call.getArgument(0);
            if (project.getId() == null) project.setId(22L);
            return project;
        });
        when(projects.findById(22L)).thenReturn(Optional.of(existingProject()));
        when(projects.existsById(22L)).thenReturn(true);

        ProjectService service = new ProjectService();
        ReflectionTestUtils.setField(service, "projectRepository", projects);
        ReflectionTestUtils.setField(service, "auditLogService", audit);
        ReflectionTestUtils.setField(service, "projectAccessService", mock(ProjectAccessService.class));
        ProjectTeamProvisioningService provisioning = mock(ProjectTeamProvisioningService.class);
        ReflectionTestUtils.setField(service, "teamProvisioningService", provisioning);
        org.mockito.Mockito.when(provisioning.createForProject(any(), org.mockito.ArgumentMatchers.any(), any()))
            .thenAnswer(call -> {
                Project project = call.getArgument(0);
                Team team = new Team(); team.setId(33L); project.setTeam(team); team.setProject(project);
                return team;
            });

        ProjectCreateRequestDTO create = new ProjectCreateRequestDTO();
        create.setName("Private project title");
        TeamCompositionRequestDTO composition = new TeamCompositionRequestDTO();
        composition.setManagerUserId(1L); composition.setProjectManagerIds(java.util.List.of(1L));
        composition.setTeamLeadIds(java.util.List.of(2L)); composition.setDeveloperIds(java.util.List.of(3L));
        composition.setTesterIds(java.util.List.of(4L)); create.setTeamComposition(composition);
        service.createProject(create, "manager@example.test");

        ProjectUpdateRequestDTO update = new ProjectUpdateRequestDTO();
        update.setName("Renamed project");
        service.updateProject(22L, update, "manager@example.test");
        service.deleteProject(22L, "manager@example.test");

        verify(audit).createLog("PROJECT_CREATED",
                "targetType=PROJECT; targetId=22; project created", null, 6L);
        verify(audit).createLog("PROJECT_UPDATED",
                "targetType=PROJECT; targetId=22; updatedFields=name", null, 6L);
        verify(audit).createLog("PROJECT_DELETED",
                "targetType=PROJECT; targetId=22; project deleted", null, 6L);
    }

    private Project existingProject() {
        Project project = new Project();
        project.setId(22L);
        project.setName("Old project");
        return project;
    }
}
