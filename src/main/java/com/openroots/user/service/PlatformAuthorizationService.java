package com.openroots.user.service;

import com.openroots.user.model.PlatformRole;
import com.openroots.user.model.UserEntity;
import com.openroots.user.repository.UserRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Platform-level authorization. The role is read from the database on every check rather
 * than from the token, so a demotion takes effect immediately.
 */
@Service
public class PlatformAuthorizationService {

    private final UserRepository userRepository;

    public PlatformAuthorizationService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /** Returns the caller if they are a platform ADMIN; otherwise 403 (or 401 if the account is gone). */
    public UserEntity requireAdmin(String email) {
        UserEntity user = userRepository.findByEmail(email)
                .orElseThrow(() -> new AuthenticationCredentialsNotFoundException("Account no longer exists"));
        if (user.getPlatformRole() != PlatformRole.ADMIN) {
            throw new AccessDeniedException("Only platform administrators may perform this operation");
        }
        return user;
    }
}
