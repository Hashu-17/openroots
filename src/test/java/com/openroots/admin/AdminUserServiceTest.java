package com.openroots.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.openroots.common.exception.NotFoundException;
import com.openroots.user.model.PlatformRole;
import com.openroots.user.model.UserEntity;
import com.openroots.user.repository.UserRepository;
import com.openroots.user.service.PlatformAuthorizationService;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

@ExtendWith(MockitoExtension.class)
class AdminUserServiceTest {

    private static final String ADMIN = "admin@example.com";

    @Mock
    private UserRepository userRepository;

    @Mock
    private PlatformAuthorizationService platformAuthorizationService;

    @InjectMocks
    private AdminUserService service;

    @Test
    void nonAdminCannotListOrChangeRoles() {
        when(platformAuthorizationService.requireAdmin("user@example.com")).thenThrow(new AccessDeniedException("no"));

        assertThatThrownBy(() -> service.listUsers("user@example.com")).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.changePlatformRole("user@example.com", UUID.randomUUID(), PlatformRole.ADMIN))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(userRepository);
    }

    @Test
    void adminListsUsers() {
        UserEntity user = new UserEntity("a@example.com", "hash");
        when(userRepository.findAllByOrderByEmailAsc()).thenReturn(List.of(user));

        assertThat(service.listUsers(ADMIN)).containsExactly(user);
    }

    @Test
    void adminPromotesAUser() {
        UUID id = UUID.randomUUID();
        UserEntity user = new UserEntity("a@example.com", "hash");
        when(userRepository.findById(id)).thenReturn(Optional.of(user));

        service.changePlatformRole(ADMIN, id, PlatformRole.ADMIN);

        assertThat(user.getPlatformRole()).isEqualTo(PlatformRole.ADMIN);
    }

    @Test
    void lastAdminCannotBeDemoted() {
        UUID id = UUID.randomUUID();
        UserEntity onlyAdmin = new UserEntity(ADMIN, "hash", PlatformRole.ADMIN);
        when(userRepository.findById(id)).thenReturn(Optional.of(onlyAdmin));
        when(userRepository.countByPlatformRole(PlatformRole.ADMIN)).thenReturn(1L);

        assertThatThrownBy(() -> service.changePlatformRole(ADMIN, id, PlatformRole.USER))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least one administrator");
        assertThat(onlyAdmin.getPlatformRole()).isEqualTo(PlatformRole.ADMIN);
    }

    @Test
    void adminCanBeDemotedWhenAnotherRemains() {
        UUID id = UUID.randomUUID();
        UserEntity other = new UserEntity("other@example.com", "hash", PlatformRole.ADMIN);
        when(userRepository.findById(id)).thenReturn(Optional.of(other));
        when(userRepository.countByPlatformRole(PlatformRole.ADMIN)).thenReturn(2L);

        service.changePlatformRole(ADMIN, id, PlatformRole.USER);

        assertThat(other.getPlatformRole()).isEqualTo(PlatformRole.USER);
    }

    @Test
    void unknownUserIsNotFound() {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.changePlatformRole(ADMIN, id, PlatformRole.ADMIN))
                .isInstanceOf(NotFoundException.class);
    }
}
