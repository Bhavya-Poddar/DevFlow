package com.devflow.devflow.entity;

import java.time.LocalDateTime;
import jakarta.persistence.*;

@Entity
public class Comment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String content;
    private LocalDateTime createdAt;

    @ManyToOne
    @JoinColumn(name = "issue_id")
    private Issue issue;

    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;

    public Comment() {}

    public Comment(Long id, String content, LocalDateTime createdAt, Issue issue, User user) {
        this.id = id;
        this.content = content;
        this.createdAt = createdAt;
        this.issue = issue;
        this.user = user;
    }

    public Long getId() { 
    	return id;
    	}
    public void setId(Long id) {
    	this.id = id;
    	}
    public String getContent() { 
    	return content; 
    	}
    public void setContent(String content) { 
    	this.content = content;
    	}
    public LocalDateTime getCreatedAt() { 
    	return createdAt;
    	}
    public void setCreatedAt(LocalDateTime createdAt) {
    	this.createdAt = createdAt;
    	}
    public Issue getIssue() {
    	return issue;
    	}
    public void setIssue(Issue issue) { 
    	this.issue = issue; 
    	}
    public User getUser() { 
    	return user;
    	}
    public void setUser(User user) { 
    	this.user = user;
    	}
}