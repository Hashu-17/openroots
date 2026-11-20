package com.openroots.telemetry.controller;

import com.openroots.auth.CurrentUser;
import com.openroots.openapi.api.TelemetryApi;
import com.openroots.openapi.model.TelemetryApiResponse;
import com.openroots.openapi.model.TelemetryIngestRequest;
import com.openroots.openapi.model.TelemetryListApiResponse;
import com.openroots.openapi.model.TelemetryResponse;
import com.openroots.telemetry.model.TelemetryRecord;
import com.openroots.telemetry.service.TelemetryService;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TelemetryController implements TelemetryApi {

    private final TelemetryService telemetryService;

    public TelemetryController(TelemetryService telemetryService) {
        this.telemetryService = telemetryService;
    }

    @Override
    public ResponseEntity<TelemetryApiResponse> ingestTelemetry(TelemetryIngestRequest request) {
        TelemetryRecord record = telemetryService.ingest(CurrentUser.email(), toServiceRequest(request));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new TelemetryApiResponse(true, "Telemetry ingested", toResponse(record), now()));
    }

    @Override
    public ResponseEntity<TelemetryListApiResponse> listTelemetry(UUID deviceId) {
        List<TelemetryRecord> records = deviceId == null
                ? telemetryService.findAll(CurrentUser.email())
                : telemetryService.findByDevice(CurrentUser.email(), deviceId);
        List<TelemetryResponse> responses = records.stream()
                .map(this::toResponse)
                .toList();

        return ResponseEntity.ok(new TelemetryListApiResponse(true, "Telemetry fetched", responses, now()));
    }

    private com.openroots.telemetry.dto.TelemetryIngestRequest toServiceRequest(TelemetryIngestRequest request) {
        Map<String, Object> payload = new LinkedHashMap<>(request.getPayload());
        return new com.openroots.telemetry.dto.TelemetryIngestRequest(
                request.getDeviceId(),
                request.getCapturedAt().toInstant(),
                payload);
    }

    private TelemetryResponse toResponse(TelemetryRecord record) {
        Map<String, BigDecimal> payload = new LinkedHashMap<>();
        record.payload().forEach((channel, value) -> {
            if (!(value instanceof Number number)) {
                throw new IllegalStateException("Stored telemetry value is not numeric: " + channel);
            }
            payload.put(channel, new BigDecimal(number.toString()));
        });

        return new TelemetryResponse(
                record.id(),
                record.deviceId(),
                record.capturedAt().atOffset(ZoneOffset.UTC),
                payload);
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }
}
