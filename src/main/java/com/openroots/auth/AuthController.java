package com.openroots.auth;

import com.openroots.openapi.api.AuthApi;
import com.openroots.openapi.model.AuthApiResponse;
import com.openroots.openapi.model.LoginRequest;
import com.openroots.openapi.model.RegisterRequest;
import com.openroots.openapi.model.VoidApiResponse;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AuthController implements AuthApi {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @Override
    public ResponseEntity<VoidApiResponse> register(RegisterRequest request) {
        authService.register(new com.openroots.auth.dto.RegisterRequest(request.getEmail(), request.getPassword()));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new VoidApiResponse(true, "Account registered", null, now()));
    }

    @Override
    public ResponseEntity<AuthApiResponse> login(LoginRequest request) {
        com.openroots.auth.dto.AuthResponse token = authService.login(
                new com.openroots.auth.dto.LoginRequest(request.getEmail(), request.getPassword()));
        com.openroots.openapi.model.AuthResponse data = new com.openroots.openapi.model.AuthResponse(
                token.accessToken(), token.tokenType(), token.expiresInSeconds());
        return ResponseEntity.ok(new AuthApiResponse(true, "Authentication successful", data, now()));
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }
}
