package com.openroots.project.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.openroots.common.exception.NotFoundException;
import com.openroots.project.model.ProjectEntity;
import com.openroots.project.model.ProjectMemberEntity;
import com.openroots.project.model.ProjectRole;
import com.openroots.project.repository.ProjectMemberRepository;
import com.openroots.project.repository.ProjectRepository;
import com.openroots.user.model.PlatformRole;
import com.openroots.user.model.UserEntity;
import com.openroots.user.service.PlatformAuthorizationService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

@ExtendWith(MockitoExtension.class)
class ProjectServiceTest {

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private ProjectMemberRepository memberRepository;

    @Mock
    private ProjectAuthorizationService authorizationService;

    @Mock
    private PlatformAuthorizationService platformAuthorizationService;

    @InjectMocks
    private ProjectService projectService;

    @Test
    void nonAdminCannotCreateProjects() {
        when(platformAuthorizationService.requireAdmin("user@example.com"))
                .thenThrow(new AccessDeniedException("Only platform administrators may perform this operation"));

        assertThatThrownBy(() -> projectService.create("user@example.com", "Greenhouse"))
                .isInstanceOf(AccessDeniedException.class);
        org.mockito.Mockito.verifyNoInteractions(projectRepository, memberRepository);
    }

    @Test
    void adminCreatorBecomesOwnerOfNewProject() {
        UserEntity user = new UserEntity("owner@example.com", "hash", PlatformRole.ADMIN);
        when(platformAuthorizationService.requireAdmin("owner@example.com")).thenReturn(user);
        when(projectRepository.save(any(ProjectEntity.class))).thenAnswer(call -> call.getArgument(0));

        ProjectEntity project = projectService.create("owner@example.com", "  Greenhouse ");

        assertThat(project.getName()).isEqualTo("Greenhouse");
        ArgumentCaptor<ProjectMemberEntity> member = ArgumentCaptor.forClass(ProjectMemberEntity.class);
        verify(memberRepository).save(member.capture());
        assertThat(member.getValue().getRole()).isEqualTo(ProjectRole.OWNER);
        assertThat(member.getValue().getUser()).isSameAs(user);
        assertThat(member.getValue().getProject()).isSameAs(project);
    }

    @Test
    void listReturnsOnlyProjectsTheUserBelongsTo() {
        ProjectEntity mine = new ProjectEntity("Mine");
        UserEntity user = new UserEntity("me@example.com", "hash");
        when(memberRepository.findAllByUser_EmailOrderByProject_CreatedAtDesc("me@example.com"))
                .thenReturn(List.of(new ProjectMemberEntity(user, mine, ProjectRole.VIEWER)));

        assertThat(projectService.findAllForUser("me@example.com")).containsExactly(mine);
    }

    @Test
    void nonMemberCannotReadProjectAndCannotTellItExists() {
        UUID projectId = UUID.randomUUID();
        when(authorizationService.requireRead("stranger@example.com", projectId))
                .thenThrow(new NotFoundException("Project not found: " + projectId));

        assertThatThrownBy(() -> projectService.findByIdForUser("stranger@example.com", projectId))
                .isInstanceOf(NotFoundException.class);
        org.mockito.Mockito.verifyNoInteractions(projectRepository);
    }
}
