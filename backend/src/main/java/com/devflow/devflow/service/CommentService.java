package com.devflow.devflow.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import com.devflow.devflow.dto.CommentCreateRequestDTO;
import com.devflow.devflow.entity.Comment;
import com.devflow.devflow.entity.User;
import com.devflow.devflow.repository.CommentRepository;
import com.devflow.devflow.repository.IssueRepository;
import com.devflow.devflow.repository.UserRepository;

@Service
public class CommentService {
    private static final Logger logger = LoggerFactory.getLogger(CommentService.class);

    @Autowired private CommentRepository commentRepository;
    @Autowired private IssueRepository issueRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private AuditLogServices auditLogService;
    @Autowired private NotificationService notificationService;
    @Autowired private ProjectAccessService projectAccessService;
    @Autowired private EffectivePermissionService permissions;

    @Transactional
    public Comment createComment(CommentCreateRequestDTO request, String userEmail) {
        User user = userRepository.findByEmail(userEmail)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        if (request.getContent() == null || request.getContent().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Comment content is required");
        }
        if (request.getIssue() == null || request.getIssue().getId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Task id is required");
        }
        var linkedIssue = issueRepository.findById(request.getIssue().getId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found"));
        projectAccessService.requireProjectAccess(linkedIssue.getProject(), userEmail);
        permissions.require(userEmail, "COMMENT_CREATE",
            linkedIssue.getProject() == null ? null : linkedIssue.getProject().getId(), null);

        Comment comment = new Comment();
        comment.setContent(request.getContent());
        comment.setIssue(linkedIssue);
        comment.setUser(user);
        comment.setCreatedAt(LocalDateTime.now());

        Comment savedComment = commentRepository.save(comment);

        auditLogService.createLog(
            "COMMENT_ADDED",
            "A comment was added to task",
            savedComment.getIssue() != null ? savedComment.getIssue().getId() : null,
            user.getId()
        );

        if (notificationService != null) notificationService.notifyIssueParticipants(linkedIssue, user,
            "COMMENT_ADDED", user.getName() + " commented on task '" + linkedIssue.getTitle() + "'.",
            NotificationTargetPaths.issue(linkedIssue.getId()));

        return savedComment;
    }

    public List<Comment> getCommentsByIssue(Long issueId) {
        return commentRepository.findByIssueIdOrderByCreatedAtAsc(issueId);
    }

    @Transactional
    public void deleteComment(Long id, String userEmail) {
        Comment comment = commentRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Comment not found"));

        User actor = userRepository.findByEmail(userEmail)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        Long issueId = comment.getIssue() != null ? comment.getIssue().getId() : null;
        if (comment.getIssue() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Comment is not associated with a task");
        }
        projectAccessService.requireProjectAccess(comment.getIssue().getProject(), userEmail);
        permissions.require(userEmail, "COMMENT_DELETE",
            comment.getIssue().getProject() == null ? null : comment.getIssue().getProject().getId(), null);

        commentRepository.deleteById(id);

        auditLogService.createLog(
            "COMMENT_DELETED",
            "A comment was deleted from task",
            issueId,
            actor.getId()
        );
    }
}
