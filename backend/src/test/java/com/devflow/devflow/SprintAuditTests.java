package com.devflow.devflow;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.devflow.devflow.dto.SprintCreateRequestDTO;
import com.devflow.devflow.dto.SprintUpdateRequestDTO;
import com.devflow.devflow.entity.Sprint;
import com.devflow.devflow.repository.IssueRepository;
import com.devflow.devflow.repository.ProjectRepository;
import com.devflow.devflow.repository.SprintBurndownSnapshotRepository;
import com.devflow.devflow.repository.SprintRepository;
import com.devflow.devflow.service.AuditLogServices;
import com.devflow.devflow.service.SprintService;
import com.devflow.devflow.service.ProjectAccessService;

class SprintAuditTests {

    @Test
    void sprintLifecycleAuditsAuthenticatedActorAndTargetIds() {
        SprintRepository sprints = mock(SprintRepository.class);
        ProjectRepository projects = mock(ProjectRepository.class);
        IssueRepository issues = mock(IssueRepository.class);
        SprintBurndownSnapshotRepository snapshots = mock(SprintBurndownSnapshotRepository.class);
        AuditLogServices audit = mock(AuditLogServices.class);
        when(audit.findActorId("manager@example.test")).thenReturn(8L);
        when(sprints.save(any(Sprint.class))).thenAnswer(call -> {
            Sprint sprint = call.getArgument(0);
            if (sprint.getId() == null) sprint.setId(31L);
            return sprint;
        });
        when(sprints.findById(31L)).thenAnswer(call -> Optional.of(existingSprint()));
        when(sprints.findByIdForUpdate(31L)).thenAnswer(call -> Optional.of(existingSprint()));
        when(snapshots.existsBySprint_Id(31L)).thenReturn(false);

        SprintService service = new SprintService();
        ReflectionTestUtils.setField(service, "sprintRepository", sprints);
        ReflectionTestUtils.setField(service, "projectRepository", projects);
        ReflectionTestUtils.setField(service, "issueRepository", issues);
        ReflectionTestUtils.setField(service, "burndownSnapshotRepository", snapshots);
        ReflectionTestUtils.setField(service, "auditLogService", audit);
        ReflectionTestUtils.setField(service, "projectAccessService", mock(ProjectAccessService.class));

        SprintCreateRequestDTO create = new SprintCreateRequestDTO();
        create.setName("Private sprint goal");
        service.createSprint(create, "manager@example.test");

        SprintUpdateRequestDTO update = new SprintUpdateRequestDTO();
        update.setName("Renamed sprint");
        service.updateSprint(31L, update, "manager@example.test");
        service.deleteSprint(31L, "manager@example.test");

        verify(audit).createLog("SPRINT_CREATED", "targetType=SPRINT; targetId=31; sprint created", null, 8L);
        verify(audit).createLog("SPRINT_UPDATED",
                "targetType=SPRINT; targetId=31; updatedFields=name", null, 8L);
        verify(audit).createLog("SPRINT_DELETED", "targetType=SPRINT; targetId=31; sprint deleted", null, 8L);
    }

    private Sprint existingSprint() {
        Sprint sprint = new Sprint();
        sprint.setId(31L);
        sprint.setName("Old sprint");
        return sprint;
    }
}
