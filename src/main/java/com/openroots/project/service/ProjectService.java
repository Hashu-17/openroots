package com.openroots.project.service;

import com.openroots.common.exception.NotFoundException;
import com.openroots.project.model.ProjectEntity;
import com.openroots.project.repository.ProjectRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class ProjectService {

    private final ProjectRepository projectRepository;

    public ProjectService(ProjectRepository projectRepository) {
        this.projectRepository = projectRepository;
    }

    public List<ProjectEntity> findAll() {
        return projectRepository.findAllByOrderByCreatedAtDesc();
    }

    public ProjectEntity findById(UUID projectId) {
        return projectRepository.findById(projectId)
                .orElseThrow(() -> new NotFoundException("Project not found: " + projectId));
    }
}