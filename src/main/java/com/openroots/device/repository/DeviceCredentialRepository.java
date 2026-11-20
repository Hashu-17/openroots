package com.openroots.device.repository;

import com.openroots.device.model.DeviceCredentialEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeviceCredentialRepository extends JpaRepository<DeviceCredentialEntity, UUID> {

    List<DeviceCredentialEntity> findAllByDevice_Id(UUID deviceId);

    java.util.Optional<DeviceCredentialEntity> findByIdAndDevice_Id(UUID id, UUID deviceId);
}
