package com.devflow.devflow.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.devflow.devflow.entity.User;

public interface UserRecoveryRepository extends JpaRepository<User, Long> {
    long countByEnabledTrueAndAdminRecoveryTrue();
}
