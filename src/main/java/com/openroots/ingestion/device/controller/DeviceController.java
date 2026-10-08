package com.openroots.ingestion.device.controller;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.Authentication;

import com.openroots.ingestion.common.response.ApiResponse;
import com.openroots.ingestion.device.dto.DeviceCreateRequest;
import com.openroots.ingestion.device.dto.DeviceResponse;
import com.openroots.ingestion.device.dto.SensorResponse;
import com.openroots.ingestion.device.model.DeviceEntity;
import com.openroots.ingestion.device.service.DeviceService;

@RestController
@RequestMapping("/api/v1/projects/{projectId}/devices")

public class DeviceController {

    private final DeviceService deviceService;

    public DeviceController(DeviceService deviceService) {
        this.deviceService = deviceService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<DeviceResponse>> registerDevice(
            @PathVariable UUID projectId,
            @Valid @RequestBody DeviceCreateRequest request,
            Authentication authentication) {
        DeviceEntity device = deviceService.register(authentication.getName(), projectId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Device registered", toResponse(device)));
    }

    @GetMapping
    public ApiResponse<List<DeviceResponse>> listDevices(
            @PathVariable UUID projectId,
            Authentication authentication) {
        return ApiResponse.success("Devices fetched", deviceService.findByProject(authentication.getName(), projectId).stream()
                .map(this::toResponse)
                .toList());
    }

    @GetMapping("/{deviceId}")
    public ApiResponse<DeviceResponse> getDevice(
            @PathVariable UUID projectId,
            @PathVariable UUID deviceId,
            Authentication authentication) {
        return ApiResponse.success("Device fetched",
                toResponse(deviceService.findById(authentication.getName(), projectId, deviceId)));
    }

    @DeleteMapping("/{deviceId}")
    public ApiResponse<Void> deleteDevice(
            @PathVariable UUID projectId,
            @PathVariable UUID deviceId,
            Authentication authentication) {
        deviceService.delete(authentication.getName(), projectId, deviceId);
        return ApiResponse.success("Device deleted", null);
    }

    private DeviceResponse toResponse(DeviceEntity device) {
        return new DeviceResponse(
                device.getId(),
                device.getProject().getId(),
                device.getName(),
                device.getType(),
                device.getStatus(),
                device.getRegisteredAt(),
                device.getSensors().stream()
                        .map(sensor -> new SensorResponse(sensor.getId(), sensor.getChannelName(), sensor.getUnit()))
                        .toList());
    }
}