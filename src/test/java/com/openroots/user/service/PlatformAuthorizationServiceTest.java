package com.openroots.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.openroots.user.model.PlatformRole;
import com.openroots.user.model.UserEntity;
import com.openroots.user.repository.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;

@ExtendWith(MockitoExtension.class)
class PlatformAuthorizationServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private PlatformAuthorizationService service;

    @Test
    void adminPasses() {
        UserEntity admin = new UserEntity("admin@example.com", "hash", PlatformRole.ADMIN);
        when(userRepository.findByEmail("admin@example.com")).thenReturn(Optional.of(admin));

        assertThat(service.requireAdmin("admin@example.com")).isSameAs(admin);
    }

    @Test
    void regularUserIsForbidden() {
        when(userRepository.findByEmail("user@example.com"))
                .thenReturn(Optional.of(new UserEntity("user@example.com", "hash")));

        assertThatThrownBy(() -> service.requireAdmin("user@example.com"))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void deletedAccountIsUnauthenticated() {
        when(userRepository.findByEmail("gone@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.requireAdmin("gone@example.com"))
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
    }
}
