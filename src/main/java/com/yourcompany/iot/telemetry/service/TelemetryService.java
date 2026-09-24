package com.yourcompany.iot.telemetry.service;

import com.yourcompany.iot.common.exception.NotFoundException;
import com.yourcompany.iot.device.service.DeviceService;
import com.yourcompany.iot.telemetry.dto.TelemetryIngestRequest;
import com.yourcompany.iot.telemetry.model.TelemetryRecord;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ConcurrentMap;
import org.springframework.stereotype.Service;

@Service
public class TelemetryService {

    private final DeviceService deviceService;
    private final ConcurrentMap<UUID, List<TelemetryRecord>> telemetryByDevice = new ConcurrentHashMap<>();

    public TelemetryService(DeviceService deviceService) {
        this.deviceService = deviceService;
    }

    public TelemetryRecord ingest(TelemetryIngestRequest request) {
        if (!deviceService.exists(request.deviceId())) {
            throw new NotFoundException("Device not found: " + request.deviceId());
        }

        TelemetryRecord record = new TelemetryRecord(
                UUID.randomUUID(),
                request.deviceId(),
                request.capturedAt() == null ? Instant.now() : request.capturedAt(),
                Map.copyOf(request.payload()));

        telemetryByDevice.computeIfAbsent(request.deviceId(), key -> new CopyOnWriteArrayList<>())
                .add(record);

        return record;
    }

    public List<TelemetryRecord> findAll() {
        return telemetryByDevice.values().stream()
                .flatMap(List::stream)
                .sorted(Comparator.comparing(TelemetryRecord::capturedAt).reversed())
                .toList();
    }

    public List<TelemetryRecord> findByDevice(UUID deviceId) {
        if (!deviceService.exists(deviceId)) {
            throw new NotFoundException("Device not found: " + deviceId);
        }

        return telemetryByDevice.getOrDefault(deviceId, List.of()).stream()
                .sorted(Comparator.comparing(TelemetryRecord::capturedAt).reversed())
                .toList();
    }
}