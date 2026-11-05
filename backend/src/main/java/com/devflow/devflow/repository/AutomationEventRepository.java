package com.devflow.devflow.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.devflow.devflow.entity.AutomationEvent;

public interface AutomationEventRepository extends JpaRepository<AutomationEvent, Long> {
    boolean existsBySourceKey(String sourceKey);
    List<AutomationEvent> findByStateAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
        String state, LocalDateTime now, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select event from AutomationEvent event where event.id = :id")
    Optional<AutomationEvent> findByIdForUpdate(@Param("id") Long id);
}
