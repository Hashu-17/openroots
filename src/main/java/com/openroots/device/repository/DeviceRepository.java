package com.openroots.device.repository;

import com.openroots.device.model.DeviceEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface DeviceRepository extends JpaRepository<DeviceEntity, UUID> {

    @EntityGraph(attributePaths = {"project", "sensors"})
    @Query("select distinct device from DeviceEntity device")
    List<DeviceEntity> findAllWithSensors();

    @EntityGraph(attributePaths = "sensors")
    List<DeviceEntity> findAllByProject_IdOrderByRegisteredAtDesc(UUID projectId);

    @EntityGraph(attributePaths = "sensors")
    Optional<DeviceEntity> findByIdAndProject_Id(UUID deviceId, UUID projectId);
}
