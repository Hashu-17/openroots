package com.openroots.project.controller;

import com.openroots.auth.CurrentUser;
import com.openroots.openapi.api.ProjectsApi;
import com.openroots.openapi.model.ProjectApiResponse;
import com.openroots.openapi.model.ProjectCreateRequest;
import com.openroots.openapi.model.ProjectListApiResponse;
import com.openroots.openapi.model.ProjectResponse;
import com.openroots.project.model.ProjectEntity;
import com.openroots.project.service.ProjectService;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ProjectController implements ProjectsApi {

    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @Override
    public ResponseEntity<ProjectApiResponse> createProject(ProjectCreateRequest request) {
        ProjectEntity project = projectService.create(CurrentUser.email(), request.getName());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ProjectApiResponse(true, "Project created", toResponse(project), now()));
    }

    @Override
    public ResponseEntity<ProjectListApiResponse> listProjects() {
        List<ProjectResponse> projects = projectService.findAllForUser(CurrentUser.email()).stream()
                .map(this::toResponse)
                .toList();

        return ResponseEntity.ok(new ProjectListApiResponse(true, "Projects fetched", projects, now()));
    }

    @Override
    public ResponseEntity<ProjectApiResponse> getProject(UUID projectId) {
        ProjectResponse project = toResponse(projectService.findByIdForUser(CurrentUser.email(), projectId));
        return ResponseEntity.ok(new ProjectApiResponse(true, "Project fetched", project, now()));
    }

    private ProjectResponse toResponse(ProjectEntity project) {
        return new ProjectResponse(
                project.getId(),
                project.getName(),
                project.getCreatedAt().atOffset(ZoneOffset.UTC));
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }
}
