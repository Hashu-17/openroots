package com.openroots.project.service;

import com.openroots.common.exception.NotFoundException;
import com.openroots.project.model.ProjectEntity;
import com.openroots.project.model.ProjectMemberEntity;
import com.openroots.project.model.ProjectRole;
import com.openroots.project.repository.ProjectMemberRepository;
import com.openroots.project.repository.ProjectRepository;
import com.openroots.user.model.UserEntity;
import com.openroots.user.repository.UserRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository memberRepository;
    private final UserRepository userRepository;
    private final ProjectAuthorizationService authorizationService;

    public ProjectService(
            ProjectRepository projectRepository,
            ProjectMemberRepository memberRepository,
            UserRepository userRepository,
            ProjectAuthorizationService authorizationService) {
        this.projectRepository = projectRepository;
        this.memberRepository = memberRepository;
        this.userRepository = userRepository;
        this.authorizationService = authorizationService;
    }

    /** Creates a project and makes the caller its OWNER (BR-004). */
    @Transactional
    public ProjectEntity create(String email, String name) {
        UserEntity user = userRepository.findByEmail(email)
                .orElseThrow(() -> new NotFoundException("User not found"));
        ProjectEntity project = projectRepository.save(new ProjectEntity(name.trim()));
        memberRepository.save(new ProjectMemberEntity(user, project, ProjectRole.OWNER));
        return project;
    }

    @Transactional(readOnly = true)
    public List<ProjectEntity> findAllForUser(String email) {
        return memberRepository.findAllByUser_EmailOrderByProject_CreatedAtDesc(email).stream()
                .map(ProjectMemberEntity::getProject)
                .toList();
    }

    @Transactional(readOnly = true)
    public ProjectEntity findByIdForUser(String email, UUID projectId) {
        authorizationService.requireRead(email, projectId);
        return findById(projectId);
    }

    public ProjectEntity findById(UUID projectId) {
        return projectRepository.findById(projectId)
                .orElseThrow(() -> new NotFoundException("Project not found: " + projectId));
    }
}
