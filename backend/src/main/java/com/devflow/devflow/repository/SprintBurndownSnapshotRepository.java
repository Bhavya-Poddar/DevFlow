package com.devflow.devflow.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.devflow.devflow.entity.SprintBurndownSnapshot;

public interface SprintBurndownSnapshotRepository extends JpaRepository<SprintBurndownSnapshot, Long> {
    boolean existsBySprint_IdAndSnapshotDate(Long sprintId, LocalDate snapshotDate);

    List<SprintBurndownSnapshot> findBySprint_IdOrderBySnapshotDateAsc(Long sprintId);

    boolean existsBySprint_Id(Long sprintId);

    long countBySprint_Id(Long sprintId);
}
