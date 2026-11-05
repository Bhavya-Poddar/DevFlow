package com.devflow.devflow.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.devflow.devflow.entity.TeamRoleAssignment;

public interface TeamRoleAssignmentRepository extends JpaRepository<TeamRoleAssignment, Long> {
    List<TeamRoleAssignment> findByUserIdAndTeamId(Long userId, Long teamId);
    List<TeamRoleAssignment> findByUserId(Long userId);
    List<TeamRoleAssignment> findByTeamId(Long teamId);
    @Query("select a from TeamRoleAssignment a where a.user.id = :userId and a.team.id = :teamId")
    Optional<TeamRoleAssignment> findAssignment(@Param("userId") Long userId, @Param("teamId") Long teamId);
    boolean existsByRole_Id(Long roleId);
    long countByRole_Id(Long roleId);
}
