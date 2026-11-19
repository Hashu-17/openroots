package com.openroots.project.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.openroots.common.exception.NotFoundException;
import com.openroots.project.model.ProjectEntity;
import com.openroots.project.model.ProjectMemberEntity;
import com.openroots.project.model.ProjectRole;
import com.openroots.project.repository.ProjectMemberRepository;
import com.openroots.user.model.UserEntity;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

@ExtendWith(MockitoExtension.class)
class ProjectAuthorizationServiceTest {

    private static final String EMAIL = "person@example.com";

    @Mock
    private ProjectMemberRepository memberRepository;

    @InjectMocks
    private ProjectAuthorizationService authorizationService;

    private final UUID projectId = UUID.randomUUID();

    private void givenRole(ProjectRole role) {
        when(memberRepository.findByUser_EmailAndProject_Id(EMAIL, projectId)).thenReturn(Optional.of(
                new ProjectMemberEntity(new UserEntity(EMAIL, "hash"), new ProjectEntity("Project"), role)));
    }

    @Test
    void nonMemberSeesNotFoundForEveryOperation() {
        when(memberRepository.findByUser_EmailAndProject_Id(EMAIL, projectId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authorizationService.requireRead(EMAIL, projectId))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> authorizationService.requireDeviceUse(EMAIL, projectId))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> authorizationService.requireManage(EMAIL, projectId))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void viewerCanOnlyRead() {
        givenRole(ProjectRole.VIEWER);

        assertThatCode(() -> authorizationService.requireRead(EMAIL, projectId)).doesNotThrowAnyException();
        assertThatThrownBy(() -> authorizationService.requireDeviceUse(EMAIL, projectId))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> authorizationService.requireManage(EMAIL, projectId))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void memberCanReadAndUseDevicesButNotManage() {
        givenRole(ProjectRole.MEMBER);

        assertThatCode(() -> authorizationService.requireRead(EMAIL, projectId)).doesNotThrowAnyException();
        assertThatCode(() -> authorizationService.requireDeviceUse(EMAIL, projectId)).doesNotThrowAnyException();
        assertThatThrownBy(() -> authorizationService.requireManage(EMAIL, projectId))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void adminAndOwnerCanDoEverything() {
        for (ProjectRole role : new ProjectRole[] {ProjectRole.ADMIN, ProjectRole.OWNER}) {
            givenRole(role);
            assertThatCode(() -> authorizationService.requireRead(EMAIL, projectId)).doesNotThrowAnyException();
            assertThatCode(() -> authorizationService.requireDeviceUse(EMAIL, projectId)).doesNotThrowAnyException();
            assertThatCode(() -> authorizationService.requireManage(EMAIL, projectId)).doesNotThrowAnyException();
        }
    }
}
