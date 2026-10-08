package com.openroots.ingestion.device.repository;

import com.openroots.ingestion.device.model.DeviceEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeviceRepository extends JpaRepository<DeviceEntity, UUID> {

    @EntityGraph(attributePaths = "sensors")
    List<DeviceEntity> findAllByProject_IdOrderByRegisteredAtDesc(UUID projectId);

    @EntityGraph(attributePaths = "sensors")
    Optional<DeviceEntity> findByIdAndProject_Id(UUID deviceId, UUID projectId);
}