package com.openroots.ingestion.project.service;

import com.openroots.ingestion.common.exception.NotFoundException;
import com.openroots.ingestion.project.model.ProjectEntity;
import com.openroots.ingestion.project.repository.ProjectRepository;
import com.openroots.ingestion.project.repository.ProjectMemberRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final ProjectAuthorizationService authorizationService;
    private final ProjectMemberRepository memberRepository;

    public ProjectService(
            ProjectRepository projectRepository,
            ProjectAuthorizationService authorizationService,
            ProjectMemberRepository memberRepository) {
        this.projectRepository = projectRepository;
        this.authorizationService = authorizationService;
        this.memberRepository = memberRepository;
    }

    public List<ProjectEntity> findAll() {
        return projectRepository.findAllByOrderByCreatedAtDesc();
    }

    public ProjectEntity findById(UUID projectId) {
        return projectRepository.findById(projectId)
                .orElseThrow(() -> new NotFoundException("Project not found: " + projectId));
    }

    @Transactional(readOnly = true)
    public List<ProjectEntity> findAllForUser(String email) {
        return memberRepository.findAllByUser_EmailOrderByProject_CreatedAtDesc(email)
                .stream()
                .map(member -> member.getProject())
                .toList();
    }

    public ProjectEntity findByIdForUser(String email, UUID projectId) {
        authorizationService.requireRead(email, projectId);
        return findById(projectId);
    }
}