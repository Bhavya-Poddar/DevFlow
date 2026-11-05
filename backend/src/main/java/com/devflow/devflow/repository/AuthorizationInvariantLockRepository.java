package com.devflow.devflow.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import com.devflow.devflow.entity.AuthorizationInvariantLock;

public interface AuthorizationInvariantLockRepository extends JpaRepository<AuthorizationInvariantLock, Integer> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from AuthorizationInvariantLock l where l.id = :id")
    AuthorizationInvariantLock lockById(@Param("id") Integer id);
}
