package com.openroots.project.service;

import com.openroots.common.exception.NotFoundException;
import com.openroots.project.model.ProjectMemberEntity;
import com.openroots.project.repository.ProjectMemberRepository;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

/**
 * Project-level authorization. Non-members always get {@link NotFoundException}
 * (BR-010) so callers cannot infer whether a project or its resources exist;
 * members whose role is too weak get {@link AccessDeniedException} (403).
 */
@Service
public class ProjectAuthorizationService {

    private final ProjectMemberRepository memberRepository;

    public ProjectAuthorizationService(ProjectMemberRepository memberRepository) {
        this.memberRepository = memberRepository;
    }

    public ProjectMemberEntity requireMember(String email, UUID projectId) {
        return memberRepository.findByUser_EmailAndProject_Id(email, projectId)
                .orElseThrow(() -> new NotFoundException("Project not found: " + projectId));
    }

    /** Any member may read project resources. */
    public ProjectMemberEntity requireRead(String email, UUID projectId) {
        return requireMember(email, projectId);
    }

    /** VIEWER may not use devices (for example ingest telemetry). */
    public ProjectMemberEntity requireDeviceUse(String email, UUID projectId) {
        ProjectMemberEntity member = requireMember(email, projectId);
        if (!member.getRole().canUseDevices()) {
            throw denied();
        }
        return member;
    }

    /** OWNER or ADMIN: modify devices, credentials and membership. */
    public ProjectMemberEntity requireManage(String email, UUID projectId) {
        ProjectMemberEntity member = requireMember(email, projectId);
        if (!member.getRole().canManage()) {
            throw denied();
        }
        return member;
    }

    private AccessDeniedException denied() {
        return new AccessDeniedException("Project membership does not permit this operation");
    }
}
