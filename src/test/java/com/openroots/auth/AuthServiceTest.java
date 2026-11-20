package com.openroots.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.openroots.auth.dto.LoginRequest;
import com.openroots.auth.dto.RegisterRequest;
import com.openroots.user.model.UserEntity;
import com.openroots.user.repository.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private TokenProvider tokenProvider;

    @InjectMocks
    private AuthService authService;

    @Test
    void registrationStoresOnlyPasswordHash() {
        when(userRepository.findByEmail("person@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("correct horse battery staple")).thenReturn("hashed-password");

        authService.register(new RegisterRequest(" Person@Example.com ", "correct horse battery staple"));

        org.mockito.Mockito.verify(userRepository).save(any(UserEntity.class));
        org.mockito.Mockito.verify(passwordEncoder).encode("correct horse battery staple");
    }

    @Test
    void loginUsesSameFailureForUnknownEmailAndWrongPassword() {
        when(userRepository.findByEmail("person@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest("person@example.com", "wrong")))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("Invalid email or password");
    }

    @Test
    void loginReturnsBearerTokenForValidPassword() {
        UserEntity user = new UserEntity("person@example.com", "hashed-password");
        when(userRepository.findByEmail("person@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password", "hashed-password")).thenReturn(true);
        when(tokenProvider.createToken("person@example.com")).thenReturn("token");
        when(tokenProvider.getExpirationSeconds()).thenReturn(900L);

        assertThat(authService.login(new LoginRequest("person@example.com", "password")).accessToken())
                .isEqualTo("token");
    }
}
