package com.openroots.device.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.openroots.device.dto.DeviceCredentialResponse;
import com.openroots.device.dto.DeviceCredentialSummary;
import com.openroots.device.model.DeviceCredentialEntity;
import com.openroots.device.model.DeviceEntity;
import com.openroots.device.repository.DeviceCredentialRepository;
import com.openroots.project.model.ProjectEntity;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class DeviceCredentialServiceTest {

    private static final String EMAIL = "admin@example.com";

    @Mock
    private DeviceService deviceService;

    @Mock
    private DeviceCredentialRepository credentialRepository;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);
    private final UUID projectId = UUID.randomUUID();
    private final UUID deviceId = UUID.randomUUID();
    private final DeviceEntity device = new DeviceEntity(new ProjectEntity("Project"), "device", "sensor");

    private DeviceCredentialService service;

    @BeforeEach
    void setUp() {
        service = new DeviceCredentialService(deviceService, credentialRepository, passwordEncoder);
        org.mockito.Mockito.lenient().when(deviceService.findByIdForManage(EMAIL, projectId, deviceId)).thenReturn(device);
        org.mockito.Mockito.lenient().when(credentialRepository.save(any(DeviceCredentialEntity.class)))
                .thenAnswer(call -> {
                    DeviceCredentialEntity saved = call.getArgument(0);
                    ReflectionTestUtils.setField(saved, "id", UUID.randomUUID());
                    ReflectionTestUtils.setField(saved, "createdAt", Instant.now());
                    return saved;
                });
    }

    private DeviceCredentialEntity stored(String name, Instant expiresAt) {
        DeviceCredentialEntity credential = new DeviceCredentialEntity(device, name, "hash", expiresAt);
        ReflectionTestUtils.setField(credential, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(credential, "createdAt", Instant.now());
        return credential;
    }

    @Test
    void createReturnsSecretOnceAndStoresOnlyItsHash() {
        DeviceCredentialResponse response = service.create(EMAIL, projectId, deviceId, "  gateway key ", null);

        assertThat(response.secret()).hasSizeGreaterThanOrEqualTo(43);
        assertThat(response.name()).isEqualTo("gateway key");

        var saved = org.mockito.ArgumentCaptor.forClass(DeviceCredentialEntity.class);
        org.mockito.Mockito.verify(credentialRepository).save(saved.capture());
        String stored = saved.getValue().getSecretHash();
        assertThat(stored).isNotEqualTo(response.secret());
        assertThat(passwordEncoder.matches(response.secret(), stored)).isTrue();
    }

    @Test
    void createRejectsExpiryInThePast() {
        assertThatThrownBy(() -> service.create(
                EMAIL, projectId, deviceId, "key", Instant.now().minus(1, ChronoUnit.MINUTES)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void nonManagerCannotCreateRotateRevokeOrList() {
        when(deviceService.findByIdForManage("member@example.com", projectId, deviceId))
                .thenThrow(new AccessDeniedException("no"));

        UUID credentialId = UUID.randomUUID();
        assertThatThrownBy(() -> service.create("member@example.com", projectId, deviceId, "k", null))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.rotate("member@example.com", projectId, deviceId, credentialId))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.revoke("member@example.com", projectId, deviceId, credentialId))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.list("member@example.com", projectId, deviceId))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void rotateRevokesOldCredentialAndKeepsNameAndExpiry() {
        Instant expiry = Instant.now().plus(30, ChronoUnit.DAYS);
        DeviceCredentialEntity current = stored("gateway key", expiry);
        when(credentialRepository.findByIdAndDevice_Id(current.getId(), deviceId)).thenReturn(Optional.of(current));

        DeviceCredentialResponse rotated = service.rotate(EMAIL, projectId, deviceId, current.getId());

        assertThat(current.getRevokedAt()).isNotNull();
        assertThat(current.isActive(Instant.now())).isFalse();
        assertThat(rotated.id()).isNotEqualTo(current.getId());
        assertThat(rotated.name()).isEqualTo("gateway key");
        assertThat(rotated.expiresAt()).isEqualTo(expiry);
        assertThat(rotated.secret()).isNotBlank();
    }

    @Test
    void revokedOrExpiredCredentialCannotBeRevokedOrRotatedAgain() {
        DeviceCredentialEntity revoked = stored("old", null);
        revoked.revoke();
        DeviceCredentialEntity expired = stored("expired", Instant.now().minus(1, ChronoUnit.DAYS));
        when(credentialRepository.findByIdAndDevice_Id(revoked.getId(), deviceId)).thenReturn(Optional.of(revoked));
        when(credentialRepository.findByIdAndDevice_Id(expired.getId(), deviceId)).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> service.revoke(EMAIL, projectId, deviceId, revoked.getId()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.rotate(EMAIL, projectId, deviceId, expired.getId()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void credentialOfAnotherDeviceIsNotFound() {
        UUID foreign = UUID.randomUUID();
        when(credentialRepository.findByIdAndDevice_Id(foreign, deviceId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.revoke(EMAIL, projectId, deviceId, foreign))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void listExposesMetadataOnly() {
        DeviceCredentialEntity active = stored("active", null);
        DeviceCredentialEntity revoked = stored("revoked", null);
        revoked.revoke();
        when(credentialRepository.findAllByDevice_IdOrderByCreatedAtDesc(deviceId))
                .thenReturn(List.of(active, revoked));

        List<DeviceCredentialSummary> summaries = service.list(EMAIL, projectId, deviceId);

        assertThat(summaries).extracting(DeviceCredentialSummary::active).containsExactly(true, false);
        // The summary type has no secret/hash component at all.
        assertThat(DeviceCredentialSummary.class.getRecordComponents())
                .extracting(java.lang.reflect.RecordComponent::getName)
                .doesNotContain("secret", "secretHash");
    }
}
