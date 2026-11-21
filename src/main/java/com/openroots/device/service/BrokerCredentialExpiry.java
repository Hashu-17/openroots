package com.openroots.device.service;

import com.openroots.device.model.DeviceCredentialEntity;
import com.openroots.device.repository.DeviceCredentialRepository;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Enforces credential expiry on the broker (BR-013). Mosquitto has no notion of expiry, so when
 * the credential whose secret the broker holds expires, this job disables the device's login.
 *
 * <p>The first run after startup checks every past expiry, so expiries that happened while the
 * backend was down are still applied. Disabling is idempotent. A run that fails is retried from
 * the same point on the next run.
 */
@Component
public class BrokerCredentialExpiry {

    private static final Logger log = LoggerFactory.getLogger(BrokerCredentialExpiry.class);

    private final DeviceCredentialRepository credentialRepository;
    private final DeviceCredentialService credentialService;
    private final MosquittoDeviceProvisioner brokerProvisioner;
    private Instant checkedUpTo = Instant.EPOCH;

    public BrokerCredentialExpiry(
            DeviceCredentialRepository credentialRepository,
            DeviceCredentialService credentialService,
            MosquittoDeviceProvisioner brokerProvisioner) {
        this.credentialRepository = credentialRepository;
        this.credentialService = credentialService;
        this.brokerProvisioner = brokerProvisioner;
    }

    @Scheduled(fixedDelayString = "${openroots.mqtt.expiry-check-interval:PT1M}")
    public synchronized void disableExpiredLogins() {
        if (!brokerProvisioner.isEnabled()) {
            return;
        }
        Instant now = Instant.now();
        boolean allApplied = true;
        for (DeviceCredentialEntity credential : credentialRepository
                .findAllByRevokedAtIsNullAndExpiresAtGreaterThanAndExpiresAtLessThanEqual(checkedUpTo, now)) {
            UUID deviceId = credential.getDevice().getId();
            if (!credentialService.isBrokerCredential(deviceId, credential.getId())) {
                continue; // an older credential; the broker already holds a newer secret
            }
            try {
                brokerProvisioner.disable(deviceId);
            } catch (MqttProvisioningException exception) {
                allApplied = false;
                log.warn("Could not disable the broker login of device {} after its credential expired; will retry",
                        deviceId, exception);
            }
        }
        if (allApplied) {
            checkedUpTo = now;
        }
    }
}
