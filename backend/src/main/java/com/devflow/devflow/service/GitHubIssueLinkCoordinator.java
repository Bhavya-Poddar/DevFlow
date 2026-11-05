package com.devflow.devflow.service;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.devflow.devflow.dto.GitHubIssueLinkResponseDTO;
import com.devflow.devflow.entity.GitHubIssueLink;
import com.devflow.devflow.entity.Issue;
import com.devflow.devflow.entity.Project;
import com.devflow.devflow.entity.User;
import com.devflow.devflow.repository.GitHubIssueLinkRepository;
import com.devflow.devflow.repository.IssueRepository;
import com.devflow.devflow.repository.UserRepository;

/** Database transactions are kept separate from the remote HTTP call. */
@Service
@ConditionalOnProperty(prefix = "devflow.github", name = "enabled", havingValue = "true")
public class GitHubIssueLinkCoordinator {
    private final IssueRepository issueRepository;
    private final UserRepository userRepository;
    private final GitHubIssueLinkRepository linkRepository;
    private final AuditLogServices auditLogService;

    public GitHubIssueLinkCoordinator(IssueRepository issueRepository, UserRepository userRepository,
            GitHubIssueLinkRepository linkRepository, AuditLogServices auditLogService) {
        this.issueRepository = issueRepository;
        this.userRepository = userRepository;
        this.linkRepository = linkRepository;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public Prepared prepare(Long issueId, String authenticatedEmail) {
        User actor = userRepository.findByEmailIgnoreCase(authenticatedEmail)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                "Authenticated account is no longer available"));
        if (!"ADMIN".equals(actor.getRole()) && !"PROJECT_MANAGER".equals(actor.getRole())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "Only admins and project managers can create GitHub issues");
        }
        Issue issue = issueRepository.findByIdForUpdate(issueId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found"));
        Project project = issue.getProject();
        GitHubIssueLink link = linkRepository.findByIssue_Id(issueId).orElse(null);
        if (link != null && "LINKED".equals(link.getState())) {
            return new Prepared(link.getId(), issueId, link.getRepositoryOwner(), link.getRepositoryName(),
                issue.getTitle(), "", link.getState(), actor.getId());
        }

        String mappedOwner = project == null ? null : project.getGithubRepositoryOwner();
        String mappedRepository = project == null ? null : project.getGithubRepositoryName();
        if (mappedOwner == null || mappedRepository == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                link == null ? "Link a GitHub repository to this task's project first"
                    : "The project repository changed after a GitHub issue creation attempt. Reconcile the previous repository first.");
        }
        String owner = mappedOwner.toLowerCase(Locale.ROOT);
        String repository = mappedRepository.toLowerCase(Locale.ROOT);
        if (issue.getTitle() == null || issue.getTitle().isBlank() || issue.getTitle().length() > 256) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Task title must contain 1 to 256 characters before creating a GitHub issue");
        }
        String body = buildBody(issue);
        if (body.length() > 65_536) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Task details exceed GitHub's issue body size limit");
        }
        LocalDateTime now = LocalDateTime.now();
        boolean reconcile = link != null && ("CREATING".equals(link.getState()) || "UNKNOWN".equals(link.getState()));
        if (link == null) {
            link = new GitHubIssueLink();
            link.setIssue(issue);
            link.setRepositoryOwner(owner);
            link.setRepositoryName(repository);
            link.setCreatedAt(now);
        } else if (!owner.equals(link.getRepositoryOwner()) || !repository.equals(link.getRepositoryName())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "The linked repository changed after a GitHub issue creation attempt. Reconcile the previous repository first.");
        }
        link.setState("CREATING");
        link.setUpdatedAt(now);
        link.setGithubIssueNumber(null);
        link.setHtmlUrl(null);
        link.setTitle(null);
        link = linkRepository.saveAndFlush(link);

        return new Prepared(link.getId(), issueId, owner, repository, issue.getTitle(), body,
            reconcile ? "RECONCILE" : "CREATE", actor.getId());
    }

    @Transactional
    public GitHubIssueLinkResponseDTO markLinked(Long linkId, GitHubIssueClient.RemoteIssue remote, Long actorId) {
        return markLinked(linkId, remote, actorId, false);
    }

    @Transactional
    public GitHubIssueLinkResponseDTO markReconciled(Long linkId, GitHubIssueClient.RemoteIssue remote, Long actorId) {
        return markLinked(linkId, remote, actorId, true);
    }

    private GitHubIssueLinkResponseDTO markLinked(Long linkId, GitHubIssueClient.RemoteIssue remote, Long actorId,
            boolean reconciled) {
        GitHubIssueLink link = linkRepository.findByIdForUpdate(linkId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "GitHub issue attempt not found"));
        if ("LINKED".equals(link.getState())) {
            if (!Long.valueOf(remote.number()).equals(link.getGithubIssueNumber())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This task is already linked to a different GitHub issue.");
            }
            return new GitHubIssueLinkResponseDTO(link);
        }
        link.setGithubIssueNumber(remote.number());
        link.setTitle(remote.title());
        link.setHtmlUrl(remote.htmlUrl());
        link.setState("LINKED");
        link.setUpdatedAt(LocalDateTime.now());
        GitHubIssueLink saved = linkRepository.save(link);
        auditLogService.createLog(reconciled ? "GITHUB_ISSUE_RECONCILED" : "GITHUB_ISSUE_CREATED",
            "targetType=ISSUE; targetId=" + link.getIssue().getId() + "; repository="
                + link.getRepositoryOwner() + "/" + link.getRepositoryName() + "; githubIssueNumber="
                + remote.number() + (reconciled ? "; recovered existing GitHub issue" : "; created GitHub issue"),
            link.getIssue().getId(), actorId);
        return new GitHubIssueLinkResponseDTO(saved);
    }

    @Transactional
    public void markState(Long linkId, String state) {
        GitHubIssueLink link = requireLink(linkId);
        link.setState(state);
        link.setUpdatedAt(LocalDateTime.now());
        linkRepository.save(link);
    }

    @Transactional(readOnly = true)
    public Optional<GitHubIssueLinkResponseDTO> getLink(Long issueId) {
        if (!issueRepository.existsById(issueId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found");
        }
        return linkRepository.findByIssue_Id(issueId).map(GitHubIssueLinkResponseDTO::new);
    }

    private GitHubIssueLink requireLink(Long id) {
        return linkRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "GitHub issue attempt not found"));
    }

    private String buildBody(Issue issue) {
        StringBuilder body = new StringBuilder(GitHubIssueClient.marker(issue.getId())).append("\n\n");
        if (issue.getDescription() != null && !issue.getDescription().isBlank()) {
            body.append(issue.getDescription().trim()).append("\n\n");
        }
        if (issue.getAcceptanceCriteria() != null && !issue.getAcceptanceCriteria().isBlank()) {
            body.append("Acceptance criteria:\n").append(issue.getAcceptanceCriteria().trim()).append("\n\n");
        }
        body.append("---\nCreated from DevFlow task #").append(issue.getId());
        return body.toString();
    }

    public record Prepared(Long linkId, Long issueId, String owner, String repository,
            String title, String body, String action, Long actorId) {}
}
