package com.openroots.device.dto;

import java.time.Instant;
import java.util.UUID;

/** Credential metadata. Never includes the secret or its hash. */
public record DeviceCredentialSummary(
        UUID id,
        String name,
        Instant createdAt,
        Instant expiresAt,
        Instant revokedAt,
        boolean active) {
}
