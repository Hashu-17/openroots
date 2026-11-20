package com.openroots.device.controller;

import com.openroots.common.response.ApiResponse;
import com.openroots.device.dto.DeviceCredentialRequest;
import com.openroots.device.dto.DeviceCredentialResponse;
import com.openroots.device.service.DeviceCredentialService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/projects/{projectId}/devices/{deviceId}/credentials")
public class DeviceCredentialController {

    private final DeviceCredentialService credentialService;

    public DeviceCredentialController(DeviceCredentialService credentialService) {
        this.credentialService = credentialService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<DeviceCredentialResponse>> create(
            @PathVariable UUID projectId,
            @PathVariable UUID deviceId,
            @Valid @RequestBody DeviceCredentialRequest request,
            Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                "Device credential created",
                credentialService.create(authentication.getName(), projectId, deviceId, request.name())));
    }

    @PostMapping("/{credentialId}/rotate")
    public ApiResponse<DeviceCredentialResponse> rotate(
            @PathVariable UUID projectId,
            @PathVariable UUID deviceId,
            @PathVariable UUID credentialId,
            Authentication authentication) {
        return ApiResponse.success("Device credential rotated",
                credentialService.rotate(authentication.getName(), projectId, deviceId, credentialId));
    }

    @DeleteMapping("/{credentialId}")
    public ApiResponse<Void> revoke(
            @PathVariable UUID projectId,
            @PathVariable UUID deviceId,
            @PathVariable UUID credentialId,
            Authentication authentication) {
        credentialService.revoke(authentication.getName(), projectId, deviceId, credentialId);
        return ApiResponse.success("Device credential revoked", null);
    }
}
