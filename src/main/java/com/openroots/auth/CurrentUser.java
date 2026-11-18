package com.openroots.auth;

import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Identity of the authenticated human caller. The generated OpenAPI interfaces carry no
 * {@code Authentication} parameter, so controllers read it from the security context.
 */
public final class CurrentUser {

    private CurrentUser() {
    }

    /** Email (JWT subject) of the caller; protected routes guarantee one is present. */
    public static String email() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || authentication.getName() == null) {
            throw new AuthenticationCredentialsNotFoundException("No authenticated user");
        }
        return authentication.getName();
    }
}
