package com.yourcompany.iot.device.controller;

import com.yourcompany.iot.common.response.ApiResponse;
import com.yourcompany.iot.device.dto.DeviceCreateRequest;
import com.yourcompany.iot.device.dto.DeviceResponse;
import com.yourcompany.iot.device.model.Device;
import com.yourcompany.iot.device.service.DeviceService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/devices")
public class DeviceController {

    private final DeviceService deviceService;

    public DeviceController(DeviceService deviceService) {
        this.deviceService = deviceService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<DeviceResponse>> registerDevice(@Valid @RequestBody DeviceCreateRequest request) {
        Device device = deviceService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Device registered", toResponse(device)));
    }

    @GetMapping
    public ApiResponse<List<DeviceResponse>> listDevices() {
        return ApiResponse.success("Devices fetched", deviceService.findAll().stream().map(this::toResponse).toList());
    }

    @GetMapping("/{deviceId}")
    public ApiResponse<DeviceResponse> getDevice(@PathVariable UUID deviceId) {
        return ApiResponse.success("Device fetched", toResponse(deviceService.findById(deviceId)));
    }

    private DeviceResponse toResponse(Device device) {
        return new DeviceResponse(device.id(), device.name(), device.type(), device.registeredAt());
    }
}