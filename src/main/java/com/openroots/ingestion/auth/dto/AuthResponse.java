package com.openroots.ingestion.auth.dto;

public record AuthResponse(String accessToken, String tokenType, long expiresInSeconds) {
}
