package com.devflow.devflow.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** New comment input; author, ID, and creation time are set by the backend. */
@JsonIgnoreProperties(ignoreUnknown = true)
public class CommentCreateRequestDTO {
    private String content;
    private IdReferenceRequestDTO issue;

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public IdReferenceRequestDTO getIssue() { return issue; }
    public void setIssue(IdReferenceRequestDTO issue) { this.issue = issue; }
}
