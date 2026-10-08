package com.openroots.ingestion.project.repository;

import com.openroots.ingestion.project.model.ProjectMemberEntity;
import java.util.UUID;
import java.util.Optional;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectMemberRepository extends JpaRepository<ProjectMemberEntity, UUID> {

	boolean existsByUser_IdAndProject_Id(UUID userId, UUID projectId);

	Optional<ProjectMemberEntity> findByUser_EmailAndProject_Id(String email, UUID projectId);

	List<ProjectMemberEntity> findAllByUser_EmailOrderByProject_CreatedAtDesc(String email);
}