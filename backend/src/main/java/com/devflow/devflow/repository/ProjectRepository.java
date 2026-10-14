package com.devflow.devflow.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.devflow.devflow.entity.Project;

public interface ProjectRepository extends JpaRepository<Project, Long>{

}
