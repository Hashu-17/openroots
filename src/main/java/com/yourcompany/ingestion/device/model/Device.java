package com.yourcompany.ingestion.device.model;

import java.time.Instant;
import java.util.UUID;

public record Device(UUID id, String name, String type, Instant registeredAt) {
}