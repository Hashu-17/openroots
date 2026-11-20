package com.openroots.device.service;

import com.openroots.device.dto.DeviceCredentialResponse;
import com.openroots.device.model.DeviceCredentialEntity;
import com.openroots.device.model.DeviceEntity;
import com.openroots.device.repository.DeviceCredentialRepository;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DeviceCredentialService {

    private final DeviceService deviceService;
    private final DeviceCredentialRepository credentialRepository;
    private final PasswordEncoder passwordEncoder;
    private final SecureRandom secureRandom = new SecureRandom();

    public DeviceCredentialService(
            DeviceService deviceService,
            DeviceCredentialRepository credentialRepository,
            PasswordEncoder passwordEncoder) {
        this.deviceService = deviceService;
        this.credentialRepository = credentialRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public DeviceCredentialResponse create(String email, UUID projectId, UUID deviceId, String name) {
        DeviceEntity device = deviceService.findByIdForManage(email, projectId, deviceId);
        String secret = generateSecret();
        DeviceCredentialEntity credential = credentialRepository.save(
                new DeviceCredentialEntity(device, name.trim(), passwordEncoder.encode(secret), null));
        return new DeviceCredentialResponse(credential.getId(), name.trim(), secret, null);
    }

    @Transactional
    public DeviceCredentialResponse rotate(String email, UUID projectId, UUID deviceId, UUID credentialId) {
        revoke(email, projectId, deviceId, credentialId);
        return create(email, projectId, deviceId, "rotated");
    }

    @Transactional
    public void revoke(String email, UUID projectId, UUID deviceId, UUID credentialId) {
        deviceService.findByIdForManage(email, projectId, deviceId);
        DeviceCredentialEntity credential = credentialRepository.findByIdAndDevice_Id(credentialId, deviceId)
                .filter(item -> item.isActive(Instant.now()))
                .orElseThrow(() -> new IllegalArgumentException("Credential is not active"));
        credential.revoke();
    }

    private String generateSecret() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
