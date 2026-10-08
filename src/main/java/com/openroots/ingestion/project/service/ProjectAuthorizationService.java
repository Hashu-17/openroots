package com.openroots.ingestion.project.service;

import com.openroots.ingestion.common.exception.NotFoundException;
import com.openroots.ingestion.project.model.ProjectMemberEntity;
import com.openroots.ingestion.project.repository.ProjectMemberRepository;
import java.util.Locale;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

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

    public void requireRead(String email, UUID projectId) {
        requireMember(email, projectId);
    }

    public void requireDeviceUse(String email, UUID projectId) {
        String role = role(email, projectId);
        if (!role.equals("VIEWER")) {
            return;
        }
        throw new AccessDeniedException("Project membership does not permit this operation");
    }

    public void requireManage(String email, UUID projectId) {
        String role = role(email, projectId);
        if (role.equals("OWNER") || role.equals("ADMIN")) {
            return;
        }
        throw new AccessDeniedException("Project membership does not permit this operation");
    }

    private String role(String email, UUID projectId) {
        return requireMember(email, projectId).getRole().toUpperCase(Locale.ROOT);
    }
}
