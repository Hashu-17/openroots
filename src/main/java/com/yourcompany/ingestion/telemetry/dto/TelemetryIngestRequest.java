package com.yourcompany.ingestion.telemetry.dto;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record TelemetryIngestRequest(
        @NotNull(message = "deviceId is required") UUID deviceId,
        @NotNull(message = "capturedAt is required") Instant capturedAt,
        @NotNull(message = "payload is required") Map<String, Object> payload) {
}