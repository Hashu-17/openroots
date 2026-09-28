package com.yourcompany.ingestion.telemetry.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record TelemetryResponse(UUID id, UUID deviceId, Instant capturedAt, Map<String, Object> payload) {
}