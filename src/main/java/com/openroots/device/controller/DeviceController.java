package com.openroots.device.controller;

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

import com.openroots.common.response.ApiResponse;
import com.openroots.device.dto.DeviceCreateRequest;
import com.openroots.device.dto.DeviceRegistrationResponse;
import com.openroots.device.dto.DeviceResponse;
import com.openroots.device.dto.MqttDeviceCredentials;
import com.openroots.device.dto.SensorResponse;
import com.openroots.device.model.DeviceEntity;
import com.openroots.device.service.DeviceService;
import com.openroots.device.service.MosquittoDeviceProvisioner;
import jakarta.transaction.Transactional;

@RestController
@RequestMapping("/api/v1/projects/{projectId}/devices")
public class DeviceController {

    private final DeviceService deviceService;
    private final MosquittoDeviceProvisioner mqttProvisioner;

    public DeviceController(DeviceService deviceService, MosquittoDeviceProvisioner mqttProvisioner) {
        this.deviceService = deviceService;
        this.mqttProvisioner = mqttProvisioner;
    }

    @PostMapping
    @Transactional
    public ResponseEntity<ApiResponse<DeviceRegistrationResponse>> registerDevice(
            @PathVariable UUID projectId,
            @Valid @RequestBody DeviceCreateRequest request) {
        DeviceEntity device = deviceService.register(projectId, request);
        MqttDeviceCredentials credentials = mqttProvisioner.isEnabled()
                ? mqttProvisioner.provision(device.getId())
                : null;
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Device registered", new DeviceRegistrationResponse(
                        toResponse(device), credentials)));
    }

    @GetMapping
    public ApiResponse<List<DeviceResponse>> listDevices(@PathVariable UUID projectId) {
        return ApiResponse.success("Devices fetched", deviceService.findByProject(projectId).stream()
                .map(this::toResponse)
                .toList());
    }

    @GetMapping("/{deviceId}")
    public ApiResponse<DeviceResponse> getDevice(
            @PathVariable UUID projectId,
            @PathVariable UUID deviceId) {
        return ApiResponse.success("Device fetched", toResponse(deviceService.findById(projectId, deviceId)));
    }

    @DeleteMapping("/{deviceId}")
    @Transactional
    public ApiResponse<Void> deleteDevice(
            @PathVariable UUID projectId,
            @PathVariable UUID deviceId) {
        DeviceEntity device = deviceService.findById(projectId, deviceId);
        if (mqttProvisioner.isEnabled()) {
            mqttProvisioner.revoke(device.getId());
        }
        deviceService.delete(projectId, device.getId());
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
