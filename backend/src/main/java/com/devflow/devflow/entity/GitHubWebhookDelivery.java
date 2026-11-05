package com.devflow.devflow.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "github_webhook_delivery", uniqueConstraints =
    @UniqueConstraint(name = "uk_github_webhook_delivery_id", columnNames = "delivery_id"))
public class GitHubWebhookDelivery {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "delivery_id", nullable = false, length = 64)
    private String deliveryId;

    @Column(name = "event_type", nullable = false, length = 32)
    private String eventType;

    @Column(name = "repository_owner", nullable = false, length = 39)
    private String repositoryOwner;

    @Column(name = "repository_name", nullable = false, length = 100)
    private String repositoryName;

    @Column(name = "received_at", nullable = false)
    private LocalDateTime receivedAt;

    @PrePersist
    void setReceivedAtIfMissing() {
        if (receivedAt == null) receivedAt = LocalDateTime.now(java.time.Clock.systemUTC());
    }

    public Long getId() { return id; }
    public String getDeliveryId() { return deliveryId; }
    public void setDeliveryId(String deliveryId) { this.deliveryId = deliveryId; }
    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    public String getRepositoryOwner() { return repositoryOwner; }
    public void setRepositoryOwner(String repositoryOwner) { this.repositoryOwner = repositoryOwner; }
    public String getRepositoryName() { return repositoryName; }
    public void setRepositoryName(String repositoryName) { this.repositoryName = repositoryName; }
    public LocalDateTime getReceivedAt() { return receivedAt; }
    public void setReceivedAt(LocalDateTime receivedAt) { this.receivedAt = receivedAt; }
}
