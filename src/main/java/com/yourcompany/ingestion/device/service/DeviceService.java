package com.yourcompany.ingestion.device.service;

import com.yourcompany.ingestion.common.exception.NotFoundException;
import com.yourcompany.ingestion.device.dto.DeviceCreateRequest;
import com.yourcompany.ingestion.device.dto.SensorCreateRequest;
import com.yourcompany.ingestion.device.model.DeviceEntity;
import com.yourcompany.ingestion.device.model.SensorEntity;
import com.yourcompany.ingestion.device.repository.DeviceRepository;
import com.yourcompany.ingestion.project.model.ProjectEntity;
import com.yourcompany.ingestion.project.repository.ProjectRepository;
import jakarta.transaction.Transactional;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class DeviceService {

    private final DeviceRepository deviceRepository;
    private final ProjectRepository projectRepository;

    public DeviceService(DeviceRepository deviceRepository, ProjectRepository projectRepository) {
        this.deviceRepository = deviceRepository;
        this.projectRepository = projectRepository;
    }

    @Transactional
    public DeviceEntity register(UUID projectId, DeviceCreateRequest request) {
        ProjectEntity project = projectRepository.findById(projectId)
                .orElseThrow(() -> new NotFoundException("Project not found: " + projectId));

        List<String> channelNames = request.sensors().stream()
                .map(sensor -> sensor.channelName().trim())
                .toList();
        Set<String> uniqueChannelNames = new HashSet<>(channelNames);
        if (uniqueChannelNames.size() != channelNames.size()) {
            throw new IllegalArgumentException("Sensor channel names must be unique within a device");
        }

        DeviceEntity device = new DeviceEntity(project, request.name().trim(), request.type().trim());
        for (SensorCreateRequest sensor : request.sensors()) {
            String unit = sensor.unit() == null || sensor.unit().isBlank() ? null : sensor.unit().trim();
            device.addSensor(new SensorEntity(sensor.channelName().trim(), unit));
        }

        return deviceRepository.save(device);
    }

    @Transactional
    public List<DeviceEntity> findByProject(UUID projectId) {
        requireProject(projectId);
        return deviceRepository.findAllByProject_IdOrderByRegisteredAtDesc(projectId);
    }

    @Transactional
    public DeviceEntity findById(UUID projectId, UUID deviceId) {
        requireProject(projectId);
        return deviceRepository.findByIdAndProject_Id(deviceId, projectId)
                .orElseThrow(() -> new NotFoundException("Device not found: " + deviceId));
    }

    public boolean exists(UUID deviceId) {
        return deviceRepository.existsById(deviceId);
    }

    private void requireProject(UUID projectId) {
        if (!projectRepository.existsById(projectId)) {
            throw new NotFoundException("Project not found: " + projectId);
        }
    }
}