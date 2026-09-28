package com.yourcompany.ingestion.telemetry.controller;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.yourcompany.ingestion.common.response.ApiResponse;
import com.yourcompany.ingestion.telemetry.dto.TelemetryIngestRequest;
import com.yourcompany.ingestion.telemetry.dto.TelemetryResponse;
import com.yourcompany.ingestion.telemetry.model.TelemetryRecord;
import com.yourcompany.ingestion.telemetry.service.TelemetryService;

@RestController
@RequestMapping("/api/v1/telemetry")
public class TelemetryController {

    private final TelemetryService telemetryService;

    public TelemetryController(TelemetryService telemetryService) {
        this.telemetryService = telemetryService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<TelemetryResponse>> ingest(@Valid @RequestBody TelemetryIngestRequest request) {
        TelemetryRecord record = telemetryService.ingest(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Telemetry ingested", toResponse(record)));
    }

    @GetMapping
    public ApiResponse<List<TelemetryResponse>> list(@RequestParam(required = false) UUID deviceId) {
        List<TelemetryRecord> records = deviceId == null ? telemetryService.findAll() : telemetryService.findByDevice(deviceId);
        return ApiResponse.success("Telemetry fetched", records.stream().map(this::toResponse).toList());
    }

    private TelemetryResponse toResponse(TelemetryRecord record) {
        return new TelemetryResponse(record.id(), record.deviceId(), record.capturedAt(), record.payload());
    }
}