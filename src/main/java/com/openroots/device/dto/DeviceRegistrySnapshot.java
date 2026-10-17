package com.openroots.device.dto;

import java.util.List;
import java.util.UUID;

public record DeviceRegistrySnapshot(List<DeviceSchema> devices) {

    public record DeviceSchema(
            UUID deviceId,
            UUID projectId,
            String projectName,
            String name,
            List<SensorSchema> sensors) {
    }

    public record SensorSchema(UUID sensorId, String channelName, String unit) {
    }
}
