package com.openroots.project.controller;

import com.openroots.common.response.ApiResponse;
import com.openroots.project.dto.ProjectResponse;
import com.openroots.project.model.ProjectEntity;
import com.openroots.project.service.ProjectService;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/projects")
public class ProjectController {

    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @GetMapping
    public ApiResponse<List<ProjectResponse>> listProjects() {
        return ApiResponse.success("Projects fetched", projectService.findAll().stream()
                .map(this::toResponse)
                .toList());
    }

    @GetMapping("/{projectId}")
    public ApiResponse<ProjectResponse> getProject(@PathVariable UUID projectId) {
        return ApiResponse.success("Project fetched", toResponse(projectService.findById(projectId)));
    }

    private ProjectResponse toResponse(ProjectEntity project) {
        return new ProjectResponse(project.getId(), project.getName(), project.getCreatedAt());
    }
}