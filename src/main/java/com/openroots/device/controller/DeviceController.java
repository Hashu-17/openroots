package com.openroots.device.controller;

import com.openroots.device.model.DeviceEntity;
import com.openroots.device.service.DeviceService;
import com.openroots.openapi.api.DevicesApi;
import com.openroots.openapi.model.DeviceApiResponse;
import com.openroots.openapi.model.DeviceCreateRequest;
import com.openroots.openapi.model.DeviceListApiResponse;
import com.openroots.openapi.model.DeviceResponse;
import com.openroots.openapi.model.SensorResponse;
import com.openroots.openapi.model.VoidApiResponse;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DeviceController implements DevicesApi {

    private final DeviceService deviceService;

    public DeviceController(DeviceService deviceService) {
        this.deviceService = deviceService;
    }

    @Override
    public ResponseEntity<DeviceApiResponse> registerDevice(UUID projectId, DeviceCreateRequest request) {
        DeviceEntity device = deviceService.register(projectId, toServiceRequest(request));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new DeviceApiResponse(true, "Device registered", toResponse(device), now()));
    }

    @Override
    public ResponseEntity<DeviceListApiResponse> listProjectDevices(UUID projectId) {
        List<DeviceResponse> devices = deviceService.findByProject(projectId).stream()
                .map(this::toResponse)
                .toList();

        return ResponseEntity.ok(new DeviceListApiResponse(true, "Devices fetched", devices, now()));
    }

    @Override
    public ResponseEntity<DeviceApiResponse> getDevice(UUID projectId, UUID deviceId) {
        DeviceResponse device = toResponse(deviceService.findById(projectId, deviceId));
        return ResponseEntity.ok(new DeviceApiResponse(true, "Device fetched", device, now()));
    }

    @Override
    public ResponseEntity<VoidApiResponse> deleteDevice(UUID projectId, UUID deviceId) {
        deviceService.delete(projectId, deviceId);
        return ResponseEntity.ok(new VoidApiResponse(true, "Device deleted", null, now()));
    }

    private com.openroots.device.dto.DeviceCreateRequest toServiceRequest(DeviceCreateRequest request) {
        List<com.openroots.device.dto.SensorCreateRequest> sensors = request.getSensors().stream()
                .map(sensor -> new com.openroots.device.dto.SensorCreateRequest(
                        sensor.getChannelName(),
                        sensor.getUnit()))
                .toList();

        return new com.openroots.device.dto.DeviceCreateRequest(
                request.getName(),
                request.getType(),
                sensors);
    }

    private DeviceResponse toResponse(DeviceEntity device) {
        List<SensorResponse> sensors = device.getSensors().stream()
                .map(sensor -> new SensorResponse(sensor.getId(), sensor.getChannelName())
                        .unit(sensor.getUnit()))
                .toList();

        return new DeviceResponse(
                device.getId(),
                device.getProject().getId(),
                device.getName(),
                device.getType(),
                device.getStatus(),
                device.getRegisteredAt().atOffset(ZoneOffset.UTC),
                sensors);
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }
}
