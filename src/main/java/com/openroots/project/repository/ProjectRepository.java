package com.openroots.project.repository;

import com.openroots.project.model.ProjectEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectRepository extends JpaRepository<ProjectEntity, UUID> {

    List<ProjectEntity> findAllByOrderByCreatedAtDesc();

    Optional<ProjectEntity> findByName(String name);
}