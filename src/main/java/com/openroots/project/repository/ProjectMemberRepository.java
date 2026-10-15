package com.openroots.project.repository;

import com.openroots.project.model.ProjectMemberEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectMemberRepository extends JpaRepository<ProjectMemberEntity, UUID> {

	boolean existsByUser_IdAndProject_Id(UUID userId, UUID projectId);
}