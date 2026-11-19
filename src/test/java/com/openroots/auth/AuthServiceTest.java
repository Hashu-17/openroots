package com.openroots.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.openroots.auth.dto.LoginRequest;
import com.openroots.auth.dto.RegisterRequest;
import com.openroots.user.model.UserEntity;
import com.openroots.user.repository.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
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
    void registrationStoresOnlyPasswordHashAndNormalizesEmail() {
        when(userRepository.findByEmail("person@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("correct horse battery staple")).thenReturn("hashed-password");

        authService.register(new RegisterRequest(" Person@Example.com ", "correct horse battery staple"));

        ArgumentCaptor<UserEntity> saved = forClass(UserEntity.class);
        verify(userRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getEmail()).isEqualTo("person@example.com");
        assertThat(saved.getValue().getPasswordHash()).isEqualTo("hashed-password");
    }

    @Test
    void registrationRejectsDuplicateEmail() {
        when(userRepository.findByEmail("person@example.com"))
                .thenReturn(Optional.of(new UserEntity("person@example.com", "hash")));

        assertThatThrownBy(() -> authService.register(new RegisterRequest("person@example.com", "password123")))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void registrationMapsUniqueConstraintRaceToBadRequest() {
        when(userRepository.findByEmail("person@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("password123")).thenReturn("hash");
        when(userRepository.saveAndFlush(any(UserEntity.class))).thenThrow(new DataIntegrityViolationException("dup"));

        assertThatThrownBy(() -> authService.register(new RegisterRequest("person@example.com", "password123")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void loginFailsIdenticallyForUnknownEmailAndWrongPassword() {
        UserEntity user = new UserEntity("known@example.com", "real-hash");
        when(userRepository.findByEmail("known@example.com")).thenReturn(Optional.of(user));
        when(userRepository.findByEmail("unknown@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.matches(any(), any())).thenReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginRequest("unknown@example.com", "wrong")))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("Invalid email or password");
        assertThatThrownBy(() -> authService.login(new LoginRequest("known@example.com", "wrong")))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("Invalid email or password");
    }

    @Test
    void loginStillHashesPasswordWhenEmailIsUnknown() {
        when(userRepository.findByEmail("unknown@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.matches(any(), any())).thenReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginRequest("unknown@example.com", "wrong")))
                .isInstanceOf(BadCredentialsException.class);

        // A password comparison must still happen so response time does not reveal the email (BR-003).
        verify(passwordEncoder).matches(any(), any());
    }

    @Test
    void loginNeverSucceedsForUnknownEmailEvenIfEncoderAcceptsDummyHash() {
        when(userRepository.findByEmail("unknown@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.matches(any(), any())).thenReturn(true);

        assertThatThrownBy(() -> authService.login(new LoginRequest("unknown@example.com", "anything")))
                .isInstanceOf(BadCredentialsException.class);
        verifyNoInteractions(tokenProvider);
    }

    @Test
    void loginReturnsBearerTokenForValidPassword() {
        UserEntity user = new UserEntity("person@example.com", "hashed-password");
        when(userRepository.findByEmail("person@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password", "hashed-password")).thenReturn(true);
        when(tokenProvider.createToken("person@example.com")).thenReturn("token");
        when(tokenProvider.getExpirationSeconds()).thenReturn(900L);

        var response = authService.login(new LoginRequest(" Person@Example.com", "password"));

        assertThat(response.accessToken()).isEqualTo("token");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresInSeconds()).isEqualTo(900L);
    }

    @Test
    void currentUserReturnsTheAccountBehindTheToken() {
        UserEntity user = new UserEntity("person@example.com", "hash");
        when(userRepository.findByEmail("person@example.com")).thenReturn(Optional.of(user));

        assertThat(authService.currentUser("person@example.com")).isSameAs(user);
    }

    @Test
    void currentUserForDeletedAccountIsUnauthenticated() {
        when(userRepository.findByEmail("gone@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.currentUser("gone@example.com"))
                .isInstanceOf(org.springframework.security.authentication.AuthenticationCredentialsNotFoundException.class);
    }
}
