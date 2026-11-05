package com.devflow.devflow.repository;

import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.devflow.devflow.entity.GitHubIssueActivity;

public interface GitHubIssueActivityRepository extends JpaRepository<GitHubIssueActivity, Long> {
    @EntityGraph(attributePaths = { "delivery" })
    List<GitHubIssueActivity> findByIssueIdOrderByEventAtDescIdDesc(Long issueId);

    @EntityGraph(attributePaths = { "delivery", "issue" })
    @Query("select activity from GitHubIssueActivity activity "
        + "where activity.issue.project.team.id = :teamId")
    List<GitHubIssueActivity> findTeamActivity(@Param("teamId") Long teamId, Pageable pageable);
}
