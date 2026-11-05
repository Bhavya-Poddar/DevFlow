package com.devflow.devflow.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import com.devflow.devflow.entity.Sprint;

public interface SprintRepository extends JpaRepository<Sprint, Long> {
    long countByStatusIgnoreCase(String status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select sprint from Sprint sprint where upper(sprint.status) in ('ACTIVE', 'IN_PROGRESS') "
        + "and sprint.startDate <= :snapshotDate and sprint.endDate >= :snapshotDate order by sprint.id")
    List<Sprint> findActiveSprintsForSnapshot(@Param("snapshotDate") LocalDate snapshotDate);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select sprint from Sprint sprint where sprint.id = :sprintId")
    Optional<Sprint> findByIdForUpdate(@Param("sprintId") Long sprintId);

    @Query("select count(sprint) from Sprint sprint where upper(sprint.status) = 'ACTIVE' "
        + "and (:projectId is null or sprint.project.id = :projectId) "
        + "and (:sprintId is null or sprint.id = :sprintId)")
    long countDashboardActiveSprints(@Param("projectId") Long projectId, @Param("sprintId") Long sprintId);

    @Query("select sprint.id, sprint.name, sprint.status, project.id, project.name, sprint.startDate, sprint.endDate, "
        + "count(issue), sum(case when issue.status = 'RESOLVED' then 1 else 0 end) "
        + "from Sprint sprint left join sprint.project project left join Issue issue on issue.sprint = sprint "
        + "where upper(sprint.status) in ('ACTIVE', 'IN_PROGRESS') "
        + "and (:projectId is null or project.id = :projectId) "
        + "and (:sprintId is null or sprint.id = :sprintId) "
        + "group by sprint.id, sprint.name, sprint.status, project.id, project.name, sprint.startDate, sprint.endDate "
        + "order by sprint.startDate, sprint.id")
    List<Object[]> dashboardActiveSprints(@Param("projectId") Long projectId, @Param("sprintId") Long sprintId);

    @Query("select sprint.id, sprint.name, sprint.status, project.id, project.name, sprint.endDate, "
        + "count(issue), sum(case when issue.status = 'RESOLVED' then 1 else 0 end), "
        + "sum(case when issue.id is not null and issue.status <> 'RESOLVED' then 1 else 0 end), "
        + "sum(case when issue.id is not null and issue.status <> 'RESOLVED' and issue.dueDate < :today then 1 else 0 end), "
        + "sum(case when issue.id is not null and issue.status <> 'RESOLVED' and issue.dueDate >= :today and issue.dueDate <= :throughDate then 1 else 0 end) "
        + "from Sprint sprint left join sprint.project project left join Issue issue on issue.sprint = sprint "
        + "where upper(sprint.status) in ('ACTIVE', 'IN_PROGRESS') "
        + "and (:projectId is null or project.id = :projectId) "
        + "and (:sprintId is null or sprint.id = :sprintId) "
        + "group by sprint.id, sprint.name, sprint.status, project.id, project.name, sprint.endDate "
        + "order by sprint.endDate, sprint.id")
    List<Object[]> dashboardSprintWorkloadRisk(@Param("today") LocalDate today,
        @Param("throughDate") LocalDate throughDate, @Param("projectId") Long projectId,
        @Param("sprintId") Long sprintId);
}
