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

    @Transactional
    public Comment createComment(CommentCreateRequestDTO request, String userEmail) {
        User user = userRepository.findByEmail(userEmail)
            .orElseThrow(() -> new RuntimeException("User not found"));

        if (request.getContent() == null || request.getContent().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Comment content is required");
        }
        if (request.getIssue() == null || request.getIssue().getId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Issue id is required");
        }
        var linkedIssue = issueRepository.findById(request.getIssue().getId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Issue not found"));

        Comment comment = new Comment();
        comment.setContent(request.getContent());
        comment.setIssue(linkedIssue);
        comment.setUser(user);
        comment.setCreatedAt(LocalDateTime.now());

        Comment savedComment = commentRepository.save(comment);

        auditLogService.createLog(
            "COMMENT_ADDED",
            "A comment was added to issue",
            savedComment.getIssue() != null ? savedComment.getIssue().getId() : null,
            user.getId()
        );

        Long issueId = savedComment.getIssue() != null ? savedComment.getIssue().getId() : null;
        if (issueId != null) {
            try {
                issueRepository.findById(issueId).ifPresent(foundIssue -> {
                    User assignee = foundIssue.getAssignedUser();
                    if (assignee != null && !Objects.equals(assignee.getId(), user.getId())) {
                        notificationService.createNotification(assignee, "COMMENT_ADDED",
                            "New comment added to issue '" + foundIssue.getTitle() + "'");
                    }
                });
            } catch (RuntimeException exception) {
                logger.warn("Could not create a comment notification for issue {}", issueId, exception);
            }
        }

        return savedComment;
    }

    public List<Comment> getCommentsByIssue(Long issueId) {
        return commentRepository.findByIssueIdOrderByCreatedAtAsc(issueId);
    }

    @Transactional
    public void deleteComment(Long id, String userEmail) {
        Comment comment = commentRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Comment not found"));

        User actor = userRepository.findByEmail(userEmail)
            .orElseThrow(() -> new RuntimeException("User not found"));

        Long issueId = comment.getIssue() != null ? comment.getIssue().getId() : null;

        commentRepository.deleteById(id);

        auditLogService.createLog(
            "COMMENT_DELETED",
            "A comment was deleted from issue",
            issueId,
            actor.getId()
        );
    }
}
