package com.openroots.telemetry.service;

import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import com.openroots.common.exception.NotFoundException;
import com.openroots.device.service.DeviceService;
import com.openroots.project.service.ProjectAuthorizationService;
import com.openroots.telemetry.dto.TelemetryIngestRequest;
import com.openroots.telemetry.model.TelemetryRecord;
import com.openroots.telemetry.repository.TelemetryRepository;
import com.openroots.telemetry.repository.TelemetryRepository.TelemetryPoint;

@Service
public class TelemetryService {

    private final DeviceService deviceService;
    private final TelemetryRepository telemetryRepository;

    private final ProjectAuthorizationService authorizationService;

    public TelemetryService(
            DeviceService deviceService,
            TelemetryRepository telemetryRepository,
            ProjectAuthorizationService authorizationService) {
        this.deviceService = deviceService;
        this.telemetryRepository = telemetryRepository;
        this.authorizationService = authorizationService;
    }

    /** Human-user ingest: the caller must be able to use devices in the device's project. */
    @Transactional
    public TelemetryRecord ingest(String email, TelemetryIngestRequest request) {
        authorizationService.requireDeviceUse(email, deviceService.findProjectId(request.deviceId()));
        return ingest(request);
    }

    /** All telemetry for projects the caller is a member of. */
    public List<TelemetryRecord> findAll(String email) {
        return toRecords(telemetryRepository.findAllForUser(email));
    }

    public List<TelemetryRecord> findByDevice(String email, UUID deviceId) {
        authorizationService.requireRead(email, deviceService.findProjectId(deviceId));
        return findByDevice(deviceId);
    }

    /**
     * Unauthorized ingest for trusted internal callers only (the MQTT consumer).
     * REST callers must use {@link #ingest(String, TelemetryIngestRequest)}.
     */
    @Transactional
    public TelemetryRecord ingest(TelemetryIngestRequest request) {
        if (!deviceService.exists(request.deviceId())) {
            throw new NotFoundException("Device not found: " + request.deviceId());
        }

        Instant capturedAt = request.capturedAt() == null ? Instant.now() : request.capturedAt();
        TelemetryRecord record = new TelemetryRecord(
                recordId(request.deviceId(), capturedAt),
                request.deviceId(),
                capturedAt,
                Map.copyOf(request.payload()));

        for (Map.Entry<String, Object> entry : request.payload().entrySet()) {
            if (!(entry.getValue() instanceof Number number)) {
                throw new IllegalArgumentException("Telemetry value must be numeric: " + entry.getKey());
            }
            if (!telemetryRepository.save(request.deviceId(), entry.getKey(), number.doubleValue(), capturedAt)) {
                throw new NotFoundException("Sensor not found for device: " + entry.getKey());
            }
        }
        deviceService.markOnline(request.deviceId());

        return record;
    }

    List<TelemetryRecord> findByDevice(UUID deviceId) {
        if (!deviceService.exists(deviceId)) {
            throw new NotFoundException("Device not found: " + deviceId);
        }

        return toRecords(telemetryRepository.findByDevice(deviceId));
    }

    private List<TelemetryRecord> toRecords(List<TelemetryPoint> points) {
        Map<String, TelemetryRecordBuilder> grouped = new LinkedHashMap<>();
        for (TelemetryPoint point : points) {
            String key = point.deviceId() + "|" + point.capturedAt();
            grouped.computeIfAbsent(key, ignored -> new TelemetryRecordBuilder(
                    point.deviceId(), point.capturedAt()))
                    .payload.put(point.channelName(), point.value());
        }
        return grouped.values().stream().map(TelemetryRecordBuilder::build).toList();
    }

    private UUID recordId(UUID deviceId, Instant capturedAt) {
        return UUID.nameUUIDFromBytes((deviceId + "|" + capturedAt).getBytes(StandardCharsets.UTF_8));
    }

    private static final class TelemetryRecordBuilder {
        private final UUID deviceId;
        private final Instant capturedAt;
        private final Map<String, Object> payload = new LinkedHashMap<>();

        private TelemetryRecordBuilder(UUID deviceId, Instant capturedAt) {
            this.deviceId = deviceId;
            this.capturedAt = capturedAt;
        }

        private TelemetryRecord build() {
            return new TelemetryRecord(
                    UUID.nameUUIDFromBytes((deviceId + "|" + capturedAt).getBytes(StandardCharsets.UTF_8)),
                    deviceId,
                    capturedAt,
                    Map.copyOf(payload));
        }
    }
}