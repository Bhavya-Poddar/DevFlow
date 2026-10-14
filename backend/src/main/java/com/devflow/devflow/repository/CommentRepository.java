package com.devflow.devflow.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import com.devflow.devflow.entity.Comment;

public interface CommentRepository extends JpaRepository<Comment, Long> {
    List<Comment> findByIssueIdOrderByCreatedAtAsc(Long issueId);
    boolean existsByIssueId(Long issueId);
}
