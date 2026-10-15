package com.openroots.device.dto;

import jakarta.validation.constraints.NotBlank;

public record SensorCreateRequest(
        @NotBlank(message = "channelName is required") String channelName,
        String unit) {
}