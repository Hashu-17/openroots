package com.openroots.device.dto;

public record DeviceRegistrationResponse(
        DeviceResponse device,
        MqttDeviceCredentials mqttCredentials) {
}
