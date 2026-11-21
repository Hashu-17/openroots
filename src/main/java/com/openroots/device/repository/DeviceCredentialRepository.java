package com.openroots.device.repository;

import com.openroots.device.model.DeviceCredentialEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeviceCredentialRepository extends JpaRepository<DeviceCredentialEntity, UUID> {

    List<DeviceCredentialEntity> findAllByDevice_IdOrderByCreatedAtDesc(UUID deviceId);

    java.util.Optional<DeviceCredentialEntity> findByIdAndDevice_Id(UUID id, UUID deviceId);

    /** Unrevoked credentials whose expiry falls in (after, upTo]. */
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = "device")
    List<DeviceCredentialEntity> findAllByRevokedAtIsNullAndExpiresAtGreaterThanAndExpiresAtLessThanEqual(
            java.time.Instant after, java.time.Instant upTo);
}
