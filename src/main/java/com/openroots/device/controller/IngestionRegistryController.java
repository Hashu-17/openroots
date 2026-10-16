package com.openroots.device.controller;

import com.openroots.device.dto.DeviceRegistrySnapshot;
import com.openroots.device.model.DeviceEntity;
import com.openroots.device.repository.DeviceRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/internal/v1/ingestion")
public class IngestionRegistryController {

    private static final String REGISTRY_KEY_HEADER = "X-OpenRoots-Registry-Key";

    private final DeviceRepository deviceRepository;
    private final byte[] registryKey;

    public IngestionRegistryController(
            DeviceRepository deviceRepository,
            @Value("${openroots.ingestion.registry-api-key:}") String registryKey) {
        this.deviceRepository = deviceRepository;
        this.registryKey = registryKey.getBytes(StandardCharsets.UTF_8);
    }

    @GetMapping("/device-registry")
    public DeviceRegistrySnapshot getDeviceRegistry(
            @RequestHeader(name = REGISTRY_KEY_HEADER, required = false) String suppliedKey) {
        if (!hasValidKey(suppliedKey)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid registry credentials");
        }

        List<DeviceRegistrySnapshot.DeviceSchema> devices = deviceRepository.findAllWithSensors().stream()
                .map(this::toDeviceSchema)
                .toList();
        return new DeviceRegistrySnapshot(devices);
    }

    private DeviceRegistrySnapshot.DeviceSchema toDeviceSchema(DeviceEntity device) {
        return new DeviceRegistrySnapshot.DeviceSchema(
                device.getId(),
                device.getSensors().stream()
                        .map(sensor -> new DeviceRegistrySnapshot.SensorSchema(
                                sensor.getId(), sensor.getChannelName()))
                        .toList());
    }

    private boolean hasValidKey(String suppliedKey) {
        if (registryKey.length == 0 || suppliedKey == null) {
            return false;
        }
        return MessageDigest.isEqual(registryKey, suppliedKey.getBytes(StandardCharsets.UTF_8));
    }
}
