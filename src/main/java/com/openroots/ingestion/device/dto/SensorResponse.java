package com.openroots.ingestion.device.dto;

import java.util.UUID;

public record SensorResponse(UUID id, String channelName, String unit) {
}