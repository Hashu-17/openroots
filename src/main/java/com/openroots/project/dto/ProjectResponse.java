package com.openroots.project.dto;

import java.time.Instant;
import java.util.UUID;

public record ProjectResponse(UUID id, String name, Instant createdAt) {
}