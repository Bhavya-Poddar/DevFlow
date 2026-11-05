package com.devflow.devflow.repository;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.devflow.devflow.entity.AutomationExecution;

public interface AutomationExecutionRepository extends JpaRepository<AutomationExecution, Long> {
    boolean existsByEvent_IdAndRule_Id(Long eventId, Long ruleId);
    boolean existsByEvent_IdAndRuleIsNullAndOutcome(Long eventId, String outcome);
    List<AutomationExecution> findAllByOrderByCreatedAtDescIdDesc(Pageable pageable);
}
