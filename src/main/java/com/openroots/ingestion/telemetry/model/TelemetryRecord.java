package com.openroots.ingestion.telemetry.model;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record TelemetryRecord(UUID id, UUID deviceId, Instant capturedAt, Map<String, Object> payload) {
}