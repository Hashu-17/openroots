package com.openroots.ingestion.device.dto;

import java.time.Instant;
import java.util.UUID;

public record DeviceCredentialResponse(UUID id, String name, String secret, Instant expiresAt) {
}
