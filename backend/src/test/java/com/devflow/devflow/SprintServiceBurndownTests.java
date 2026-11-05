package com.devflow.devflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.devflow.devflow.repository.IssueRepository;
import com.devflow.devflow.repository.ProjectRepository;
import com.devflow.devflow.repository.SprintBurndownSnapshotRepository;
import com.devflow.devflow.repository.SprintRepository;
import com.devflow.devflow.service.SprintService;

@ExtendWith(MockitoExtension.class)
class SprintServiceBurndownTests {
    @Mock private SprintRepository sprintRepository;
    @Mock private ProjectRepository projectRepository;
    @Mock private IssueRepository issueRepository;
    @Mock private SprintBurndownSnapshotRepository snapshotRepository;
    @InjectMocks private SprintService sprintService;

    @Test
    void sprintWithSnapshotHistoryCannotBeDeleted() {
        when(sprintRepository.findByIdForUpdate(4L)).thenReturn(Optional.of(new com.devflow.devflow.entity.Sprint()));
        when(snapshotRepository.existsBySprint_Id(4L)).thenReturn(true);

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
            () -> sprintService.deleteSprint(4L));

        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
    }

    @Test
    void sprintWithoutHistoryCanStillBeDeleted() {
        when(sprintRepository.findByIdForUpdate(4L)).thenReturn(Optional.of(new com.devflow.devflow.entity.Sprint()));
        when(snapshotRepository.existsBySprint_Id(4L)).thenReturn(false);

        sprintService.deleteSprint(4L);

        verify(sprintRepository).deleteById(4L);
    }
}
