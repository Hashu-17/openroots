package com.openroots.device.controller;

import com.openroots.auth.CurrentUser;
import com.openroots.device.dto.DeviceCredentialResponse;
import com.openroots.device.dto.DeviceCredentialSummary;
import com.openroots.device.service.DeviceCredentialService;
import com.openroots.openapi.api.CredentialsApi;
import com.openroots.openapi.model.DeviceCredentialCreateRequest;
import com.openroots.openapi.model.DeviceCredentialListApiResponse;
import com.openroots.openapi.model.DeviceCredentialSecret;
import com.openroots.openapi.model.DeviceCredentialSecretApiResponse;
import com.openroots.openapi.model.VoidApiResponse;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DeviceCredentialController implements CredentialsApi {

    private final DeviceCredentialService credentialService;

    public DeviceCredentialController(DeviceCredentialService credentialService) {
        this.credentialService = credentialService;
    }

    @Override
    public ResponseEntity<DeviceCredentialListApiResponse> listDeviceCredentials(UUID projectId, UUID deviceId) {
        List<com.openroots.openapi.model.DeviceCredentialSummary> credentials = credentialService
                .list(CurrentUser.email(), projectId, deviceId).stream()
                .map(this::toSummary)
                .toList();
        return ResponseEntity.ok(new DeviceCredentialListApiResponse(true, "Device credentials fetched", credentials, now()));
    }

    @Override
    public ResponseEntity<DeviceCredentialSecretApiResponse> createDeviceCredential(
            UUID projectId, UUID deviceId, DeviceCredentialCreateRequest request) {
        Instant expiresAt = request.getExpiresAt() == null ? null : request.getExpiresAt().toInstant();
        DeviceCredentialResponse created = credentialService.create(
                CurrentUser.email(), projectId, deviceId, request.getName(), expiresAt);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new DeviceCredentialSecretApiResponse(true, "Device credential created", toSecret(created), now()));
    }

    @Override
    public ResponseEntity<DeviceCredentialSecretApiResponse> rotateDeviceCredential(
            UUID projectId, UUID deviceId, UUID credentialId) {
        DeviceCredentialResponse rotated = credentialService.rotate(
                CurrentUser.email(), projectId, deviceId, credentialId);
        return ResponseEntity.ok(
                new DeviceCredentialSecretApiResponse(true, "Device credential rotated", toSecret(rotated), now()));
    }

    @Override
    public ResponseEntity<VoidApiResponse> revokeDeviceCredential(UUID projectId, UUID deviceId, UUID credentialId) {
        credentialService.revoke(CurrentUser.email(), projectId, deviceId, credentialId);
        return ResponseEntity.ok(new VoidApiResponse(true, "Device credential revoked", null, now()));
    }

    private com.openroots.openapi.model.DeviceCredentialSummary toSummary(DeviceCredentialSummary summary) {
        return new com.openroots.openapi.model.DeviceCredentialSummary(
                summary.id(),
                summary.name(),
                summary.createdAt().atOffset(ZoneOffset.UTC),
                summary.active())
                .expiresAt(summary.expiresAt() == null ? null : summary.expiresAt().atOffset(ZoneOffset.UTC))
                .revokedAt(summary.revokedAt() == null ? null : summary.revokedAt().atOffset(ZoneOffset.UTC));
    }

    private DeviceCredentialSecret toSecret(DeviceCredentialResponse response) {
        return new DeviceCredentialSecret(response.id(), response.name(), response.secret())
                .expiresAt(response.expiresAt() == null ? null : response.expiresAt().atOffset(ZoneOffset.UTC));
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }
}
