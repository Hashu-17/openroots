package com.openroots.ingestion.device.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record DeviceCreateRequest(
        @NotBlank(message = "name is required") String name,
        @NotBlank(message = "type is required") String type,
        @NotEmpty(message = "at least one sensor is required") List<@Valid SensorCreateRequest> sensors) {
}