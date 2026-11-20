package com.openroots.device.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DeviceCredentialRequest(@NotBlank @Size(max = 100) String name) {
}
