package com.yourcompany.ingestion.device.dto;

import java.time.Instant;
import java.util.UUID;

public record DeviceResponse(UUID id, String name, String type, Instant registeredAt) {
}