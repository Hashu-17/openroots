package com.openroots.auth.dto;

public record AuthResponse(String accessToken, String tokenType, long expiresInSeconds) {
}
