package com.devflow.devflow.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.devflow.devflow.entity.ProjectRoleAssignment;

public interface ProjectRoleAssignmentRepository extends JpaRepository<ProjectRoleAssignment, Long> {
    List<ProjectRoleAssignment> findByUserIdAndProjectId(Long userId, Long projectId);
    List<ProjectRoleAssignment> findByUserId(Long userId);
    List<ProjectRoleAssignment> findByProjectId(Long projectId);
    boolean existsByRole_Id(Long roleId);
    long countByRole_Id(Long roleId);
    @Query("select a from ProjectRoleAssignment a where a.user.id = :userId and a.project.id = :projectId")
    Optional<ProjectRoleAssignment> findAssignment(@Param("userId") Long userId, @Param("projectId") Long projectId);
}
