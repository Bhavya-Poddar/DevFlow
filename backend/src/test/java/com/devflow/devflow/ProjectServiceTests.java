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
import com.devflow.devflow.entity.Project;
import com.devflow.devflow.dto.ProjectCreateRequestDTO;
import com.devflow.devflow.dto.ProjectUpdateRequestDTO;
import com.devflow.devflow.service.AdminConfigurationService;

class ProjectServiceTests {

    @Test
    void createProjectDoesNotCreateExternalRepositoryConfiguration() {
        ProjectRepository projectRepository = mock(ProjectRepository.class);
        when(projectRepository.save(org.mockito.ArgumentMatchers.any(Project.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        ProjectService projectService = service(projectRepository);
        ProjectCreateRequestDTO request = new ProjectCreateRequestDTO();
        request.setName("Project");

        Project result = projectService.createProject(request);

        assertEquals(null, result.getGithubRepositoryOwner());
        assertEquals(null, result.getGithubRepositoryName());
    }

    @Test
    void createProjectPersistsConfiguredCategory() {
        ProjectRepository projectRepository = mock(ProjectRepository.class);
        AdminConfigurationService configuration = mock(AdminConfigurationService.class);
        when(projectRepository.save(org.mockito.ArgumentMatchers.any(Project.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        ProjectService projectService = service(projectRepository);
        org.springframework.test.util.ReflectionTestUtils.setField(projectService, "adminConfigurationService", configuration);
        ProjectCreateRequestDTO request = new ProjectCreateRequestDTO();
        request.setName("Project");
        request.setCategory("internal");

        Project result = projectService.createProject(request);

        assertEquals("INTERNAL", result.getCategory());
        verify(configuration).validateProjectCategory("internal", null);
    }

    @Test
    void partialProjectUpdatePreservesCategoryUnlessCategoryWasSupplied() {
        ProjectRepository projectRepository = mock(ProjectRepository.class);
        Project existing = new Project();
        existing.setId(7L);
        existing.setCategory("INTERNAL");
        when(projectRepository.findById(7L)).thenReturn(java.util.Optional.of(existing));
        when(projectRepository.save(existing)).thenReturn(existing);
        ProjectService projectService = service(projectRepository);
        AdminConfigurationService configuration = mock(AdminConfigurationService.class);
        org.springframework.test.util.ReflectionTestUtils.setField(projectService, "adminConfigurationService", configuration);

        Project unchanged = projectService.updateProject(7L, new ProjectUpdateRequestDTO());
        assertEquals("INTERNAL", unchanged.getCategory());
        verify(configuration, org.mockito.Mockito.never()).validateProjectCategory(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());

        ProjectUpdateRequestDTO update = new ProjectUpdateRequestDTO();
        update.setCategory("external");
        Project changed = projectService.updateProject(7L, update);
        assertEquals("EXTERNAL", changed.getCategory());
        verify(configuration).validateProjectCategory("EXTERNAL", "INTERNAL");
    }

    @Test
    void updatePreservesLegacyRepositoryColumnsWithoutUsingThem() {
        ProjectRepository projectRepository = mock(ProjectRepository.class);
        Project existing = new Project();
        existing.setId(7L);
        existing.setGithubRepositoryOwner("team");
        existing.setGithubRepositoryName("app");
        when(projectRepository.findById(7L)).thenReturn(java.util.Optional.of(existing));
        when(projectRepository.save(existing)).thenReturn(existing);

        Project result = service(projectRepository).updateProject(7L, new ProjectUpdateRequestDTO());

        assertEquals("team", result.getGithubRepositoryOwner());
        assertEquals("app", result.getGithubRepositoryName());
    }

    @Test
    void deletingMissingProjectReturnsNotFoundAndDoesNotDeleteAnything() {
        ProjectRepository projectRepository = mock(ProjectRepository.class);
        when(projectRepository.existsById(404L)).thenReturn(false);
        ProjectService projectService = service(projectRepository);

        ResponseStatusException exception = assertThrows(
            ResponseStatusException.class,
            () -> projectService.deleteProject(404L));

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
        verify(projectRepository, never()).deleteById(404L);
    }

    private ProjectService service(ProjectRepository repository) {
        ProjectService projectService = new ProjectService();
        org.springframework.test.util.ReflectionTestUtils.setField(projectService, "projectRepository", repository);
        return projectService;
    }
}
