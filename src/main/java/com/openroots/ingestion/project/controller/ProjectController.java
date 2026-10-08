package com.openroots.ingestion.project.controller;

import com.openroots.ingestion.common.response.ApiResponse;
import com.openroots.ingestion.project.dto.ProjectResponse;
import com.openroots.ingestion.project.model.ProjectEntity;
import com.openroots.ingestion.project.service.ProjectService;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.Authentication;

@RestController
@RequestMapping("/api/v1/projects")
public class ProjectController {

    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @GetMapping
    public ApiResponse<List<ProjectResponse>> listProjects(Authentication authentication) {
        return ApiResponse.success("Projects fetched", projectService.findAllForUser(authentication.getName()).stream()
                .map(this::toResponse)
                .toList());
    }

    @GetMapping("/{projectId}")
    public ApiResponse<ProjectResponse> getProject(
            @PathVariable UUID projectId,
            Authentication authentication) {
        return ApiResponse.success("Project fetched",
                toResponse(projectService.findByIdForUser(authentication.getName(), projectId)));
    }

    private ProjectResponse toResponse(ProjectEntity project) {
        return new ProjectResponse(project.getId(), project.getName(), project.getCreatedAt());
    }
}