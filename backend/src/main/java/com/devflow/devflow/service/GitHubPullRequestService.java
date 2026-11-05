package com.devflow.devflow.service;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.devflow.devflow.dto.GitHubPullRequestResponseDTO;
import com.devflow.devflow.repository.GitHubPullRequestRepository;
import com.devflow.devflow.repository.IssueRepository;

@Service
@ConditionalOnProperty(prefix = "devflow.github", name = "enabled", havingValue = "true")
public class GitHubPullRequestService {
    private final IssueRepository issueRepository;
    private final GitHubPullRequestRepository pullRequestRepository;

    public GitHubPullRequestService(IssueRepository issueRepository,
            GitHubPullRequestRepository pullRequestRepository) {
        this.issueRepository = issueRepository;
        this.pullRequestRepository = pullRequestRepository;
    }

    @Transactional(readOnly = true)
    public List<GitHubPullRequestResponseDTO> getPullRequestsForIssue(Long issueId) {
        if (!issueRepository.existsById(issueId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found");
        }
        return pullRequestRepository.findDistinctByLinkedIssues_IdOrderByUpdatedAtDescIdDesc(issueId)
            .stream().map(GitHubPullRequestResponseDTO::new).toList();
    }
}
