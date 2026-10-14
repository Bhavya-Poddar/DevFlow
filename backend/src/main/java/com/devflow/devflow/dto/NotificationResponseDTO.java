package com.devflow.devflow.dto;

import java.time.LocalDateTime;

public class NotificationResponseDTO {

    private Long id;
    private String message;
    private String type;
    private boolean read;
    private LocalDateTime createdAt;
    private String recipientAvatarUrl;

    public NotificationResponseDTO(Long id, String message, String type, boolean read, LocalDateTime createdAt) {
        this(id, message, type, read, createdAt, null);
    }

    public NotificationResponseDTO(Long id, String message, String type, boolean read, LocalDateTime createdAt,
            String recipientAvatarUrl) {
        this.id = id;
        this.message = message;
        this.type = type;
        this.read = read;
        this.createdAt = createdAt;
        this.recipientAvatarUrl = recipientAvatarUrl;
    }

    public Long getId() { return id; }
    public String getMessage() { return message; }
    public String getType() { return type; }
    public boolean isRead() { return read; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public String getRecipientAvatarUrl() { return recipientAvatarUrl; }
}
