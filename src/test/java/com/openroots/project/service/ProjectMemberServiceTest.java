package com.openroots.project.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.openroots.common.exception.NotFoundException;
import com.openroots.project.model.ProjectEntity;
import com.openroots.project.model.ProjectMemberEntity;
import com.openroots.project.model.ProjectRole;
import com.openroots.project.repository.ProjectMemberRepository;
import com.openroots.user.model.UserEntity;
import com.openroots.user.repository.UserRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ProjectMemberServiceTest {

    private static final String CALLER = "caller@example.com";

    @Mock
    private ProjectMemberRepository memberRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ProjectAuthorizationService authorizationService;

    @InjectMocks
    private ProjectMemberService service;

    private final UUID projectId = UUID.randomUUID();
    private final ProjectEntity project = new ProjectEntity("Project");

    private ProjectMemberEntity member(String email, ProjectRole role) {
        UserEntity user = new UserEntity(email, "hash");
        ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
        return new ProjectMemberEntity(user, project, role);
    }

    private ProjectMemberEntity callerWithRole(ProjectRole role) {
        ProjectMemberEntity caller = member(CALLER, role);
        when(authorizationService.requireManage(CALLER, projectId)).thenReturn(caller);
        return caller;
    }

    @Test
    void viewerAndMemberCannotManageMembers() {
        when(authorizationService.requireManage(CALLER, projectId)).thenThrow(new AccessDeniedException("no"));

        assertThatThrownBy(() -> service.add(CALLER, projectId, "x@example.com", ProjectRole.VIEWER))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.remove(CALLER, projectId, UUID.randomUUID()))
                .isInstanceOf(AccessDeniedException.class);
        verify(memberRepository, never()).save(any());
    }

    @Test
    void ownerAddsExistingUserWithNormalizedEmail() {
        callerWithRole(ProjectRole.OWNER);
        UserEntity user = new UserEntity("new@example.com", "hash");
        ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
        when(userRepository.findByEmail("new@example.com")).thenReturn(Optional.of(user));
        when(memberRepository.existsByUser_IdAndProject_Id(user.getId(), projectId)).thenReturn(false);
        when(memberRepository.save(any(ProjectMemberEntity.class))).thenAnswer(call -> call.getArgument(0));

        ProjectMemberEntity added = service.add(CALLER, projectId, " New@Example.com ", ProjectRole.VIEWER);

        assertThat(added.getRole()).isEqualTo(ProjectRole.VIEWER);
        assertThat(added.getUser().getEmail()).isEqualTo("new@example.com");
    }

    @Test
    void addingUnknownUserIsNotFoundAndDuplicateIsBadRequest() {
        callerWithRole(ProjectRole.ADMIN);
        when(userRepository.findByEmail("ghost@example.com")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.add(CALLER, projectId, "ghost@example.com", ProjectRole.MEMBER))
                .isInstanceOf(NotFoundException.class);

        UserEntity user = new UserEntity("dup@example.com", "hash");
        ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
        when(userRepository.findByEmail("dup@example.com")).thenReturn(Optional.of(user));
        when(memberRepository.existsByUser_IdAndProject_Id(user.getId(), projectId)).thenReturn(true);
        assertThatThrownBy(() -> service.add(CALLER, projectId, "dup@example.com", ProjectRole.MEMBER))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void adminCannotGrantOwnerRole() {
        callerWithRole(ProjectRole.ADMIN);

        assertThatThrownBy(() -> service.add(CALLER, projectId, "x@example.com", ProjectRole.OWNER))
                .isInstanceOf(AccessDeniedException.class);
        verify(memberRepository, never()).save(any());
    }

    @Test
    void adminCannotDemoteOrRemoveAnOwner() {
        callerWithRole(ProjectRole.ADMIN);
        ProjectMemberEntity owner = member("owner@example.com", ProjectRole.OWNER);
        UUID ownerId = owner.getUser().getId();
        when(memberRepository.findByUser_IdAndProject_Id(ownerId, projectId)).thenReturn(Optional.of(owner));

        assertThatThrownBy(() -> service.updateRole(CALLER, projectId, ownerId, ProjectRole.MEMBER))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.remove(CALLER, projectId, ownerId))
                .isInstanceOf(AccessDeniedException.class);
        assertThat(owner.getRole()).isEqualTo(ProjectRole.OWNER);
    }

    @Test
    void lastOwnerCannotBeDemotedOrRemoved() {
        callerWithRole(ProjectRole.OWNER);
        ProjectMemberEntity onlyOwner = member("owner@example.com", ProjectRole.OWNER);
        UUID ownerId = onlyOwner.getUser().getId();
        when(memberRepository.findByUser_IdAndProject_Id(ownerId, projectId)).thenReturn(Optional.of(onlyOwner));
        when(memberRepository.countByProject_IdAndRoleIgnoreCase(projectId, "OWNER")).thenReturn(1L);

        assertThatThrownBy(() -> service.updateRole(CALLER, projectId, ownerId, ProjectRole.ADMIN))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least one owner");
        assertThatThrownBy(() -> service.remove(CALLER, projectId, ownerId))
                .isInstanceOf(IllegalArgumentException.class);
        verify(memberRepository, never()).delete(any());
        assertThat(onlyOwner.getRole()).isEqualTo(ProjectRole.OWNER);
    }

    @Test
    void ownerCanDemoteAnOwnerWhenAnotherOwnerRemains() {
        callerWithRole(ProjectRole.OWNER);
        ProjectMemberEntity other = member("other@example.com", ProjectRole.OWNER);
        UUID otherId = other.getUser().getId();
        when(memberRepository.findByUser_IdAndProject_Id(otherId, projectId)).thenReturn(Optional.of(other));
        when(memberRepository.countByProject_IdAndRoleIgnoreCase(projectId, "OWNER")).thenReturn(2L);

        service.updateRole(CALLER, projectId, otherId, ProjectRole.ADMIN);

        assertThat(other.getRole()).isEqualTo(ProjectRole.ADMIN);
    }

    @Test
    void adminCanChangeAndRemoveNonOwners() {
        callerWithRole(ProjectRole.ADMIN);
        ProjectMemberEntity viewer = member("viewer@example.com", ProjectRole.VIEWER);
        UUID viewerId = viewer.getUser().getId();
        when(memberRepository.findByUser_IdAndProject_Id(viewerId, projectId)).thenReturn(Optional.of(viewer));

        service.updateRole(CALLER, projectId, viewerId, ProjectRole.MEMBER);
        assertThat(viewer.getRole()).isEqualTo(ProjectRole.MEMBER);

        service.remove(CALLER, projectId, viewerId);
        verify(memberRepository).delete(viewer);
    }

    @Test
    void missingTargetMemberIsNotFound() {
        callerWithRole(ProjectRole.OWNER);
        UUID unknown = UUID.randomUUID();
        when(memberRepository.findByUser_IdAndProject_Id(unknown, projectId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.remove(CALLER, projectId, unknown)).isInstanceOf(NotFoundException.class);
    }
}
