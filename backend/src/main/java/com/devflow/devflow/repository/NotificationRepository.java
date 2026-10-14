package com.devflow.devflow.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;

import com.devflow.devflow.entity.Notification;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    @EntityGraph(attributePaths = "user")
    List<Notification> findByUserIdOrderByCreatedAtDesc(Long userId);

    @EntityGraph(attributePaths = "user")
    List<Notification> findByUserIdAndReadFalseOrderByCreatedAtDesc(Long userId);

    @EntityGraph(attributePaths = "user")
    Optional<Notification> findByIdAndUserId(Long id, Long userId);
}
