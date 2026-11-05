package com.devflow.devflow.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.repository.query.Param;

import com.devflow.devflow.entity.Notification;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    @EntityGraph(attributePaths = "user")
    List<Notification> findByUserIdOrderByCreatedAtDesc(Long userId);

    @EntityGraph(attributePaths = "user")
    List<Notification> findByUserIdAndReadFalseOrderByCreatedAtDesc(Long userId);

    @EntityGraph(attributePaths = "user")
    @Query("select notification from Notification notification "
        + "where notification.user.id = :userId "
        + "and (:readState is null or notification.read = :readState) "
        + "and (:search is null or lower(notification.message) like lower(concat('%', :search, '%')) "
        + "or lower(notification.type) like lower(concat('%', :search, '%'))) ")
    Page<Notification> findHistoryPage(@Param("userId") Long userId,
            @Param("readState") Boolean readState, @Param("search") String search, Pageable pageable);

    long countByUserIdAndReadFalse(Long userId);

    @Modifying
    @Transactional
    @Query("update Notification notification set notification.read = true where notification.user.id = :userId and notification.read = false")
    int markAllUnreadRead(@Param("userId") Long userId);

    @EntityGraph(attributePaths = "user")
    Optional<Notification> findByIdAndUserId(Long id, Long userId);
}
