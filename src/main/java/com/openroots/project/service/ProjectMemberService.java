package com.openroots.project.service;

import com.openroots.common.exception.NotFoundException;
import com.openroots.project.model.ProjectMemberEntity;
import com.openroots.project.model.ProjectRole;
import com.openroots.project.repository.ProjectMemberRepository;
import com.openroots.user.model.UserEntity;
import com.openroots.user.repository.UserRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Project membership management (BR-004, BR-005, BR-008, BR-009).
 * OWNER and ADMIN manage members; only an OWNER may grant, change or remove the
 * OWNER role, and a project must always keep at least one OWNER.
 */
@Service
public class ProjectMemberService {

    private final ProjectMemberRepository memberRepository;
    private final UserRepository userRepository;
    private final ProjectAuthorizationService authorizationService;

    public ProjectMemberService(
            ProjectMemberRepository memberRepository,
            UserRepository userRepository,
            ProjectAuthorizationService authorizationService) {
        this.memberRepository = memberRepository;
        this.userRepository = userRepository;
        this.authorizationService = authorizationService;
    }

    @Transactional(readOnly = true)
    public List<ProjectMemberEntity> list(String callerEmail, UUID projectId) {
        authorizationService.requireRead(callerEmail, projectId);
        return memberRepository.findAllByProject_IdOrderByUser_EmailAsc(projectId);
    }

    @Transactional
    public ProjectMemberEntity add(String callerEmail, UUID projectId, String email, ProjectRole role) {
        ProjectMemberEntity caller = authorizationService.requireManage(callerEmail, projectId);
        requireOwnerToTouchOwnerRole(caller.getRole(), role);
        UserEntity user = userRepository.findByEmail(email.trim().toLowerCase())
                .orElseThrow(() -> new NotFoundException("User not found: " + email));
        if (memberRepository.existsByUser_IdAndProject_Id(user.getId(), projectId)) {
            throw new IllegalArgumentException("User is already a member of this project");
        }
        return memberRepository.save(new ProjectMemberEntity(user, caller.getProject(), role));
    }

    @Transactional
    public ProjectMemberEntity updateRole(String callerEmail, UUID projectId, UUID userId, ProjectRole newRole) {
        ProjectMemberEntity caller = authorizationService.requireManage(callerEmail, projectId);
        ProjectMemberEntity target = findMember(projectId, userId);
        ProjectRole oldRole = target.getRole();
        requireOwnerToTouchOwnerRole(caller.getRole(), oldRole);
        requireOwnerToTouchOwnerRole(caller.getRole(), newRole);
        if (oldRole == ProjectRole.OWNER && newRole != ProjectRole.OWNER) {
            requireAnotherOwner(projectId);
        }
        target.setRole(newRole);
        return target;
    }

    @Transactional
    public void remove(String callerEmail, UUID projectId, UUID userId) {
        ProjectMemberEntity caller = authorizationService.requireManage(callerEmail, projectId);
        ProjectMemberEntity target = findMember(projectId, userId);
        requireOwnerToTouchOwnerRole(caller.getRole(), target.getRole());
        if (target.getRole() == ProjectRole.OWNER) {
            requireAnotherOwner(projectId);
        }
        memberRepository.delete(target);
    }

    private ProjectMemberEntity findMember(UUID projectId, UUID userId) {
        return memberRepository.findByUser_IdAndProject_Id(userId, projectId)
                .orElseThrow(() -> new NotFoundException("Member not found: " + userId));
    }

    private void requireOwnerToTouchOwnerRole(ProjectRole callerRole, ProjectRole affectedRole) {
        if (affectedRole == ProjectRole.OWNER && callerRole != ProjectRole.OWNER) {
            throw new AccessDeniedException("Only a project owner may grant, change or remove the owner role");
        }
    }

    private void requireAnotherOwner(UUID projectId) {
        if (memberRepository.countByProject_IdAndRoleIgnoreCase(projectId, ProjectRole.OWNER.name()) <= 1) {
            throw new IllegalArgumentException("A project must keep at least one owner");
        }
    }
}
