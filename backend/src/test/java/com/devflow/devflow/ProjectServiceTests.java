package com.devflow.devflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.devflow.devflow.repository.ProjectRepository;
import com.devflow.devflow.service.ProjectService;

class ProjectServiceTests {

    @Test
    void deletingMissingProjectReturnsNotFoundAndDoesNotDeleteAnything() {
        ProjectRepository projectRepository = mock(ProjectRepository.class);
        when(projectRepository.existsById(404L)).thenReturn(false);
        ProjectService projectService = new ProjectService();
        org.springframework.test.util.ReflectionTestUtils.setField(projectService, "projectRepository", projectRepository);

        ResponseStatusException exception = assertThrows(
            ResponseStatusException.class,
            () -> projectService.deleteProject(404L));

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
        verify(projectRepository, never()).deleteById(404L);
    }
}
