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
import com.openroots.user.model.UserEntity;
import com.openroots.user.repository.UserRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProjectServiceTest {

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private ProjectMemberRepository memberRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ProjectAuthorizationService authorizationService;

    @InjectMocks
    private ProjectService projectService;

    @Test
    void creatorBecomesOwnerOfNewProject() {
        UserEntity user = new UserEntity("owner@example.com", "hash");
        when(userRepository.findByEmail("owner@example.com")).thenReturn(Optional.of(user));
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
