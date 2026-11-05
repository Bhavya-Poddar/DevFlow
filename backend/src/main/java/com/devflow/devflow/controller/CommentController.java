package com.devflow.devflow.controller;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import com.devflow.devflow.entity.Comment;
import com.devflow.devflow.dto.CommentCreateRequestDTO;
import com.devflow.devflow.service.CommentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.responses.ApiResponse;

@RestController
@RequestMapping("/api/comments")
@Tag(name = "Comments", description = "Task comments and comment history.")
@SecurityRequirement(name = "bearerAuth")
public class CommentController {
    @Autowired
    private CommentService commentService;

    @PostMapping
    @Operation(summary = "Add a task comment", description = "Creates a comment using the authenticated user as author.")
    @ApiResponse(responseCode = "201", description = "Comment created.")
    public ResponseEntity<Comment> createComment(@RequestBody CommentCreateRequestDTO comment, Authentication authentication) {
        return new ResponseEntity<>(commentService.createComment(comment, authentication.getName()), HttpStatus.CREATED);
    }

    @GetMapping("/issue/{issueId}")
    @Operation(summary = "List comments for a task", description = "Returns comments for the specified task in creation order.")
    public ResponseEntity<List<Comment>> getCommentsByIssue(@PathVariable Long issueId) {
        return ResponseEntity.ok(commentService.getCommentsByIssue(issueId));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a comment", description = "Deletes a comment when the caller has the required role.")
    public ResponseEntity<String> deleteComment(@PathVariable Long id, Authentication authentication) {
        commentService.deleteComment(id, authentication.getName());
        return ResponseEntity.ok("Comment deleted successfully");
    }
}
