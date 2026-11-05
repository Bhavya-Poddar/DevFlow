package com.devflow.devflow.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.devflow.devflow.entity.AutomationRule;

public interface AutomationRuleRepository extends JpaRepository<AutomationRule, Long> {
    long countByTriggerEventIgnoreCase(String triggerEvent);
    long countByActionTypeIgnoreCase(String actionType);
    List<AutomationRule> findAllByDeletedFalseOrderByUpdatedAtDescIdDesc();
    List<AutomationRule> findByEnabledTrueAndDeletedFalseAndTriggerEventOrderByIdAsc(String triggerEvent);
    boolean existsByBuiltInKey(String builtInKey);
    Optional<AutomationRule> findByBuiltInKey(String builtInKey);
    Optional<AutomationRule> findByIdAndDeletedFalse(Long id);
}
