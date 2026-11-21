package com.openroots.device.service;

import com.openroots.device.dto.DeviceCredentialResponse;
import com.openroots.device.dto.DeviceCredentialSummary;
import com.openroots.device.model.DeviceCredentialEntity;
import com.openroots.device.model.DeviceEntity;
import com.openroots.device.repository.DeviceCredentialRepository;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Device credentials belong to one device and are separate from human credentials (BR-011).
 * Secrets are random, returned once, and stored only as hashes (BR-012). Only project
 * OWNER/ADMIN may manage them. Revoked or expired credentials are inactive (BR-013) and
 * rotation replaces the secret without changing the device identity (BR-014).
 */
@Service
public class DeviceCredentialService {

    private final DeviceService deviceService;
    private final DeviceCredentialRepository credentialRepository;
    private final PasswordEncoder passwordEncoder;
    private final MosquittoDeviceProvisioner brokerProvisioner;
    private final SecureRandom secureRandom = new SecureRandom();

    public DeviceCredentialService(
            DeviceService deviceService,
            DeviceCredentialRepository credentialRepository,
            PasswordEncoder passwordEncoder,
            MosquittoDeviceProvisioner brokerProvisioner) {
        this.deviceService = deviceService;
        this.credentialRepository = credentialRepository;
        this.passwordEncoder = passwordEncoder;
        this.brokerProvisioner = brokerProvisioner;
    }

    @Transactional
    public List<DeviceCredentialSummary> list(String email, UUID projectId, UUID deviceId) {
        deviceService.findByIdForManage(email, projectId, deviceId);
        Instant now = Instant.now();
        return credentialRepository.findAllByDevice_IdOrderByCreatedAtDesc(deviceId).stream()
                .map(credential -> new DeviceCredentialSummary(
                        credential.getId(),
                        credential.getName(),
                        credential.getCreatedAt(),
                        credential.getExpiresAt(),
                        credential.getRevokedAt(),
                        credential.isActive(now)))
                .toList();
    }

    @Transactional
    public DeviceCredentialResponse create(
            String email, UUID projectId, UUID deviceId, String name, Instant expiresAt) {
        DeviceEntity device = deviceService.findByIdForManage(email, projectId, deviceId);
        if (expiresAt != null && !expiresAt.isAfter(Instant.now())) {
            throw new IllegalArgumentException("Credential expiry must be in the future");
        }
        return issue(device, name.trim(), expiresAt);
    }

    /** Revokes the credential and issues a replacement with the same name and expiry. */
    @Transactional
    public DeviceCredentialResponse rotate(String email, UUID projectId, UUID deviceId, UUID credentialId) {
        DeviceEntity device = deviceService.findByIdForManage(email, projectId, deviceId);
        DeviceCredentialEntity current = activeCredential(deviceId, credentialId);
        current.revoke();
        return issue(device, current.getName(), current.getExpiresAt());
    }

    /**
     * Revokes the credential and disables the device's broker login. The broker holds only the
     * most recently issued password, so the device cannot connect again until a new credential
     * is issued. Other credentials that are still active are not restored on the broker.
     */
    @Transactional
    public void revoke(String email, UUID projectId, UUID deviceId, UUID credentialId) {
        deviceService.findByIdForManage(email, projectId, deviceId);
        activeCredential(deviceId, credentialId).revoke();
        brokerProvisioner.disable(deviceId);
    }

    private DeviceCredentialEntity activeCredential(UUID deviceId, UUID credentialId) {
        return credentialRepository.findByIdAndDevice_Id(credentialId, deviceId)
                .filter(credential -> credential.isActive(Instant.now()))
                .orElseThrow(() -> new IllegalArgumentException("Credential is not active"));
    }

    private DeviceCredentialResponse issue(DeviceEntity device, String name, Instant expiresAt) {
        String secret = generateSecret();
        DeviceCredentialEntity credential = credentialRepository.save(
                new DeviceCredentialEntity(device, name, passwordEncoder.encode(secret), expiresAt));
        brokerProvisioner.setPassword(device.getId(), secret);
        return new DeviceCredentialResponse(credential.getId(), name, secret, expiresAt);
    }

    private String generateSecret() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
