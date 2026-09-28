package com.yourcompany.ingestion.device.service;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.stereotype.Service;

import com.yourcompany.ingestion.common.exception.NotFoundException;
import com.yourcompany.ingestion.device.dto.DeviceCreateRequest;
import com.yourcompany.ingestion.device.model.Device;

@Service
public class DeviceService {

    private final ConcurrentMap<UUID, Device> devices = new ConcurrentHashMap<>();

    public Device register(DeviceCreateRequest request) {
        Device device = new Device(UUID.randomUUID(), request.name().trim(), request.type().trim(), Instant.now());
        devices.put(device.id(), device);
        return device;
    }

    public List<Device> findAll() {
        return devices.values().stream()
                .sorted(Comparator.comparing(Device::registeredAt).reversed())
                .toList();
    }

    public Device findById(UUID deviceId) {
        return Optional.ofNullable(devices.get(deviceId))
                .orElseThrow(() -> new NotFoundException("Device not found: " + deviceId));
    }

    public boolean exists(UUID deviceId) {
        return devices.containsKey(deviceId);
    }
}