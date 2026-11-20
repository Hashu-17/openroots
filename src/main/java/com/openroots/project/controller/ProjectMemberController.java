package com.openroots.project.controller;

import com.openroots.auth.CurrentUser;
import com.openroots.openapi.api.MembersApi;
import com.openroots.openapi.model.MemberAddRequest;
import com.openroots.openapi.model.MemberApiResponse;
import com.openroots.openapi.model.MemberListApiResponse;
import com.openroots.openapi.model.MemberResponse;
import com.openroots.openapi.model.MemberRoleRequest;
import com.openroots.openapi.model.VoidApiResponse;
import com.openroots.project.model.ProjectMemberEntity;
import com.openroots.project.model.ProjectRole;
import com.openroots.project.service.ProjectMemberService;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ProjectMemberController implements MembersApi {

    private final ProjectMemberService memberService;

    public ProjectMemberController(ProjectMemberService memberService) {
        this.memberService = memberService;
    }

    @Override
    public ResponseEntity<MemberListApiResponse> listProjectMembers(UUID projectId) {
        List<MemberResponse> members = memberService.list(CurrentUser.email(), projectId).stream()
                .map(this::toResponse)
                .toList();
        return ResponseEntity.ok(new MemberListApiResponse(true, "Members fetched", members, now()));
    }

    @Override
    public ResponseEntity<MemberApiResponse> addProjectMember(UUID projectId, MemberAddRequest request) {
        ProjectMemberEntity member = memberService.add(
                CurrentUser.email(), projectId, request.getEmail(), toDomain(request.getRole()));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new MemberApiResponse(true, "Member added", toResponse(member), now()));
    }

    @Override
    public ResponseEntity<MemberApiResponse> updateProjectMemberRole(
            UUID projectId, UUID userId, MemberRoleRequest request) {
        ProjectMemberEntity member = memberService.updateRole(
                CurrentUser.email(), projectId, userId, toDomain(request.getRole()));
        return ResponseEntity.ok(new MemberApiResponse(true, "Member role updated", toResponse(member), now()));
    }

    @Override
    public ResponseEntity<VoidApiResponse> removeProjectMember(UUID projectId, UUID userId) {
        memberService.remove(CurrentUser.email(), projectId, userId);
        return ResponseEntity.ok(new VoidApiResponse(true, "Member removed", null, now()));
    }

    private ProjectRole toDomain(com.openroots.openapi.model.ProjectRole role) {
        return ProjectRole.valueOf(role.name());
    }

    private MemberResponse toResponse(ProjectMemberEntity member) {
        return new MemberResponse(
                member.getUser().getId(),
                member.getUser().getEmail(),
                com.openroots.openapi.model.ProjectRole.valueOf(member.getRole().name()));
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }
}
