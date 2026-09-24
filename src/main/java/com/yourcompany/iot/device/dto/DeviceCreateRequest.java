package com.yourcompany.iot.device.dto;

import jakarta.validation.constraints.NotBlank;

public record DeviceCreateRequest(
        @NotBlank(message = "name is required") String name,
        @NotBlank(message = "type is required") String type) {
}