package com.openroots.ingestion.project.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.openroots.ingestion.project.model.ProjectMemberEntity;
import com.openroots.ingestion.project.repository.ProjectMemberRepository;
import com.openroots.ingestion.user.model.UserEntity;
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

    @Mock
    private ProjectMemberRepository memberRepository;

    @InjectMocks
    private ProjectAuthorizationService authorizationService;

    @Test
    void viewerCannotManageProject() {
        UUID projectId = UUID.randomUUID();
        ProjectMemberEntity member = new ProjectMemberEntity(
                new UserEntity("viewer@example.com", "hash"),
                new com.openroots.ingestion.project.model.ProjectEntity("Project"),
                "VIEWER");
        when(memberRepository.findByUser_EmailAndProject_Id("viewer@example.com", projectId))
                .thenReturn(Optional.of(member));

        assertThatThrownBy(() -> authorizationService.requireManage("viewer@example.com", projectId))
                .isInstanceOf(AccessDeniedException.class);
    }
}
