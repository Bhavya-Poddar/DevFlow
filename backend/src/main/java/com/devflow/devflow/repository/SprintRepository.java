package com.devflow.devflow.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.devflow.devflow.entity.Sprint;

public interface SprintRepository extends JpaRepository<Sprint, Long> {
    long countByStatusIgnoreCase(String status);
}
