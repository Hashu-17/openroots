package com.openroots.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.openroots.user.model.PlatformRole;
import com.openroots.user.model.UserEntity;
import com.openroots.user.repository.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AdminBootstrapTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private AdminBootstrap bootstrap(String email, String password) {
        return new AdminBootstrap(userRepository, passwordEncoder, email, password);
    }

    @Test
    void createsAdminWithHashedPasswordWhenNoneExists() {
        when(userRepository.existsByPlatformRole(PlatformRole.ADMIN)).thenReturn(false);
        when(userRepository.findByEmail("admin@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("a-strong-password")).thenReturn("hashed");

        bootstrap(" Admin@Example.com ", "a-strong-password").run(null);

        ArgumentCaptor<UserEntity> saved = ArgumentCaptor.forClass(UserEntity.class);
        verify(userRepository).save(saved.capture());
        assertThat(saved.getValue().getEmail()).isEqualTo("admin@example.com");
        assertThat(saved.getValue().getPasswordHash()).isEqualTo("hashed");
        assertThat(saved.getValue().getPlatformRole()).isEqualTo(PlatformRole.ADMIN);
    }

    @Test
    void doesNothingOnceAnAdminExists() {
        when(userRepository.existsByPlatformRole(PlatformRole.ADMIN)).thenReturn(true);

        bootstrap("admin@example.com", "a-strong-password").run(null);

        verify(userRepository, never()).save(any());
    }

    @Test
    void skipsWhenNotConfigured() {
        when(userRepository.existsByPlatformRole(PlatformRole.ADMIN)).thenReturn(false);

        bootstrap("", "").run(null);
        bootstrap("admin@example.com", "").run(null);

        verify(userRepository, never()).save(any());
    }

    @Test
    void rejectsShortPassword() {
        when(userRepository.existsByPlatformRole(PlatformRole.ADMIN)).thenReturn(false);

        bootstrap("admin@example.com", "short").run(null);

        verify(userRepository, never()).save(any());
    }

    @Test
    void neverPromotesAnAlreadyRegisteredEmail() {
        UserEntity existing = new UserEntity("admin@example.com", "someone-elses-hash");
        when(userRepository.existsByPlatformRole(PlatformRole.ADMIN)).thenReturn(false);
        when(userRepository.findByEmail("admin@example.com")).thenReturn(Optional.of(existing));

        bootstrap("admin@example.com", "a-strong-password").run(null);

        verify(userRepository, never()).save(any());
        assertThat(existing.getPlatformRole()).isEqualTo(PlatformRole.USER);
    }
}
