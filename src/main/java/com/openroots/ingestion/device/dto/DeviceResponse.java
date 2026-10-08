package com.openroots.ingestion.device.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record DeviceResponse(
	UUID id,
	UUID projectId,
	String name,
	String type,
	String status,
	Instant registeredAt,
	List<SensorResponse> sensors) {
}