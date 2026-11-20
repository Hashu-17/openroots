package com.openroots.project.repository;

import com.openroots.project.model.ProjectMemberEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectMemberRepository extends JpaRepository<ProjectMemberEntity, UUID> {

    boolean existsByUser_IdAndProject_Id(UUID userId, UUID projectId);

    Optional<ProjectMemberEntity> findByUser_EmailAndProject_Id(String email, UUID projectId);

    Optional<ProjectMemberEntity> findByUser_IdAndProject_Id(UUID userId, UUID projectId);

    List<ProjectMemberEntity> findAllByUser_EmailOrderByProject_CreatedAtDesc(String email);

    List<ProjectMemberEntity> findAllByProject_IdOrderByUser_EmailAsc(UUID projectId);

    long countByProject_IdAndRoleIgnoreCase(UUID projectId, String role);
}
