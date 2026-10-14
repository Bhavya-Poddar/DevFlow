package com.devflow.devflow.controller;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import com.devflow.devflow.entity.Comment;
import com.devflow.devflow.dto.CommentCreateRequestDTO;
import com.devflow.devflow.service.CommentService;

@RestController
@RequestMapping("/api/comments")
public class CommentController {
    @Autowired
    private CommentService commentService;

    @PostMapping
    public ResponseEntity<Comment> createComment(@RequestBody CommentCreateRequestDTO comment, Authentication authentication) {
        return new ResponseEntity<>(commentService.createComment(comment, authentication.getName()), HttpStatus.CREATED);
    }

    @GetMapping("/issue/{issueId}")
    public ResponseEntity<List<Comment>> getCommentsByIssue(@PathVariable Long issueId) {
        return ResponseEntity.ok(commentService.getCommentsByIssue(issueId));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<String> deleteComment(@PathVariable Long id, Authentication authentication) {
        commentService.deleteComment(id, authentication.getName());
        return ResponseEntity.ok("Comment deleted successfully");
    }
}
