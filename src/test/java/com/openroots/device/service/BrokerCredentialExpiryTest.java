package com.openroots.device.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.openroots.device.model.DeviceCredentialEntity;
import com.openroots.device.model.DeviceEntity;
import com.openroots.device.repository.DeviceCredentialRepository;
import com.openroots.project.model.ProjectEntity;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class BrokerCredentialExpiryTest {

    private final DeviceCredentialRepository repository = mock(DeviceCredentialRepository.class);
    private final DeviceCredentialService credentialService = mock(DeviceCredentialService.class);
    private final MosquittoDeviceProvisioner provisioner = mock(MosquittoDeviceProvisioner.class);
    private final BrokerCredentialExpiry job = new BrokerCredentialExpiry(repository, credentialService, provisioner);
    private final UUID deviceId = UUID.randomUUID();
    private DeviceCredentialEntity expired;

    @BeforeEach
    void setUp() {
        DeviceEntity device = new DeviceEntity(new ProjectEntity("p"), "d", "t");
        ReflectionTestUtils.setField(device, "id", deviceId);
        expired = new DeviceCredentialEntity(device, "gateway", "hash", Instant.now().minusSeconds(5));
        ReflectionTestUtils.setField(expired, "id", UUID.randomUUID());
        when(provisioner.isEnabled()).thenReturn(true);
        when(repository.findAllByRevokedAtIsNullAndExpiresAtGreaterThanAndExpiresAtLessThanEqual(any(), any()))
                .thenReturn(List.of(expired));
    }

    @Test
    void disablesTheLoginWhenTheBrokersCredentialExpires() {
        when(credentialService.isBrokerCredential(deviceId, expired.getId())).thenReturn(true);

        job.disableExpiredLogins();

        verify(provisioner).disable(deviceId);
    }

    @Test
    void ignoresAnOlderCredentialThatIsNoLongerOnTheBroker() {
        when(credentialService.isBrokerCredential(deviceId, expired.getId())).thenReturn(false);

        job.disableExpiredLogins();

        verify(provisioner, never()).disable(any());
    }

    @Test
    void firstRunChecksEveryPastExpiryAndAFailedRunIsRetried() {
        when(credentialService.isBrokerCredential(deviceId, expired.getId())).thenReturn(true);
        doThrow(new MqttProvisioningException("broker down")).when(provisioner).disable(deviceId);

        job.disableExpiredLogins();
        job.disableExpiredLogins();

        // Both runs start from the epoch: the first because it is the first, the second because the first failed.
        verify(repository, org.mockito.Mockito.times(2))
                .findAllByRevokedAtIsNullAndExpiresAtGreaterThanAndExpiresAtLessThanEqual(eq(Instant.EPOCH), any());
    }

    @Test
    void doesNothingWhenBrokerSyncIsOff() {
        when(provisioner.isEnabled()).thenReturn(false);

        job.disableExpiredLogins();

        verifyNoInteractions(repository);
    }
}
