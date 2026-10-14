package com.devflow.devflow.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.devflow.devflow.entity.Issue;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;


public interface IssueRepository extends JpaRepository<Issue, Long> {

    List<Issue> findByProjectId(Long projectId);

    List<Issue> findBySprintId(Long sprintId);

    List<Issue> findByStatusAndTesterIsNotNullOrderByUpdatedAtAsc(String status);

    List<Issue> findByTesterIdAndStatusInOrderByUpdatedAtAsc(Long testerId, Collection<String> statuses);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select issue from Issue issue where issue.id = :issueId")
    Optional<Issue> findByIdForUpdate(@Param("issueId") Long issueId);

    @Query("select issue.status, count(issue) from Issue issue group by issue.status")
    List<Object[]> dashboardStatusCounts();

    @Query("select project.id, project.name, count(issue), "
        + "sum(case when issue.status = 'RESOLVED' then 1 else 0 end) "
        + "from Project project left join Issue issue on issue.project = project "
        + "group by project.id, project.name order by project.name")
    List<Object[]> dashboardProjectProgress();

    @Query("select user.id, user.name, count(issue), user.avatarKey from Issue issue join issue.assignedUser user "
        + "where user.role = 'DEVELOPER' group by user.id, user.name")
    List<Object[]> dashboardDeveloperWorkload();

    @Query("select user.id, user.name, count(issue), user.avatarKey from Issue issue join issue.tester user "
        + "where user.role = 'TESTER' group by user.id, user.name")
    List<Object[]> dashboardTesterWorkload();

}
