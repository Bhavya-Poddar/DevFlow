package com.devflow.devflow.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import com.devflow.devflow.entity.AuthorizationRole;

public interface AuthorizationRoleRepository extends JpaRepository<AuthorizationRole, Long> {
    @EntityGraph(attributePaths = "permissions")
    Optional<AuthorizationRole> findByCodeAndRetiredFalse(String code);
    @EntityGraph(attributePaths = "permissions")
    Optional<AuthorizationRole> findByCode(String code);
    @EntityGraph(attributePaths = "permissions")
    Optional<AuthorizationRole> findByIdAndRetiredFalse(Long id);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from AuthorizationRole r where r.id = :id")
    Optional<AuthorizationRole> findByIdForUpdate(@Param("id") Long id);
    boolean existsByCodeIgnoreCase(String code);
    boolean existsByCodeIgnoreCaseAndIdNot(String code, Long id);
}
