package com.devflow.devflow.service;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.devflow.devflow.dto.GitHubIssueActivityResponseDTO;
import com.devflow.devflow.repository.GitHubIssueActivityRepository;
import com.devflow.devflow.repository.IssueRepository;

@Service
@ConditionalOnProperty(prefix = "devflow.github", name = "enabled", havingValue = "true")
public class GitHubIssueActivityService {
    private final IssueRepository issueRepository;
    private final GitHubIssueActivityRepository activityRepository;

    public GitHubIssueActivityService(IssueRepository issueRepository,
            GitHubIssueActivityRepository activityRepository) {
        this.issueRepository = issueRepository;
        this.activityRepository = activityRepository;
    }

    @Transactional(readOnly = true)
    public List<GitHubIssueActivityResponseDTO> getIssueActivity(Long issueId) {
        if (!issueRepository.existsById(issueId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found");
        }
        return activityRepository.findByIssueIdOrderByEventAtDescIdDesc(issueId).stream()
            .map(GitHubIssueActivityResponseDTO::new).toList();
    }
}
