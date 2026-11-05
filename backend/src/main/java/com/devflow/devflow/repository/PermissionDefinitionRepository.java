package com.devflow.devflow.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.devflow.devflow.entity.PermissionDefinition;

public interface PermissionDefinitionRepository extends JpaRepository<PermissionDefinition, String> {}
