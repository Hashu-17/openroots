package com.openroots.device.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Carries the plaintext secret. Only returned when a credential is created or rotated;
 * the secret is stored hashed and can never be read back (BR-012).
 */
public record DeviceCredentialResponse(UUID id, String name, String secret, Instant expiresAt) {
}
