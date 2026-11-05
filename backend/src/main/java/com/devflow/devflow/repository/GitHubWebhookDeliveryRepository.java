package com.devflow.devflow.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.devflow.devflow.entity.GitHubWebhookDelivery;

public interface GitHubWebhookDeliveryRepository extends JpaRepository<GitHubWebhookDelivery, Long> {
    Optional<GitHubWebhookDelivery> findByDeliveryId(String deliveryId);
    boolean existsByDeliveryId(String deliveryId);
}
