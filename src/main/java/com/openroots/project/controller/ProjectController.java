package com.openroots.project.controller;

import com.openroots.openapi.api.ProjectsApi;
import com.openroots.openapi.model.ProjectApiResponse;
import com.openroots.openapi.model.ProjectListApiResponse;
import com.openroots.openapi.model.ProjectResponse;
import com.openroots.project.model.ProjectEntity;
import com.openroots.project.service.ProjectService;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ProjectController implements ProjectsApi {

    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @Override
    public ResponseEntity<ProjectListApiResponse> listProjects() {
        List<ProjectResponse> projects = projectService.findAll().stream()
                .map(this::toResponse)
                .toList();

        return ResponseEntity.ok(new ProjectListApiResponse(
                true,
                "Projects fetched",
                projects,
                OffsetDateTime.now(ZoneOffset.UTC)));
    }

    @Override
    public ResponseEntity<ProjectApiResponse> getProject(UUID projectId) {
        ProjectResponse project = toResponse(projectService.findById(projectId));
        return ResponseEntity.ok(new ProjectApiResponse(
                true,
                "Project fetched",
                project,
                OffsetDateTime.now(ZoneOffset.UTC)));
    }

    private ProjectResponse toResponse(ProjectEntity project) {
        return new ProjectResponse(
                project.getId(),
                project.getName(),
                project.getCreatedAt().atOffset(ZoneOffset.UTC));
    }
}
