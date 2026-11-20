package com.openroots.device.service;

import com.openroots.common.exception.NotFoundException;
import com.openroots.device.dto.DeviceCreateRequest;
import com.openroots.device.dto.SensorCreateRequest;
import com.openroots.device.model.DeviceEntity;
import com.openroots.device.model.SensorEntity;
import com.openroots.device.repository.DeviceRepository;
import com.openroots.project.model.ProjectEntity;
import com.openroots.project.repository.ProjectRepository;
import com.openroots.project.service.ProjectAuthorizationService;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class DeviceService {

    private final DeviceRepository deviceRepository;
    private final ProjectRepository projectRepository;
    private final ProjectAuthorizationService authorizationService;

    public DeviceService(
            DeviceRepository deviceRepository,
            ProjectRepository projectRepository,
            ProjectAuthorizationService authorizationService) {
        this.deviceRepository = deviceRepository;
        this.projectRepository = projectRepository;
        this.authorizationService = authorizationService;
    }

    // --- Authorized entry points used by the REST layer (caller identified by email) ---

    @Transactional
    public DeviceEntity register(String email, UUID projectId, DeviceCreateRequest request) {
        authorizationService.requireManage(email, projectId);
        return register(projectId, request);
    }

    @Transactional
    public List<DeviceEntity> findByProject(String email, UUID projectId) {
        authorizationService.requireRead(email, projectId);
        return findByProject(projectId);
    }

    @Transactional
    public DeviceEntity findById(String email, UUID projectId, UUID deviceId) {
        authorizationService.requireRead(email, projectId);
        return findById(projectId, deviceId);
    }

    /** Device lookup for operations that modify the device or its credentials (OWNER/ADMIN). */
    @Transactional
    public DeviceEntity findByIdForManage(String email, UUID projectId, UUID deviceId) {
        authorizationService.requireManage(email, projectId);
        return findById(projectId, deviceId);
    }

    @Transactional
    public void delete(String email, UUID projectId, UUID deviceId) {
        authorizationService.requireManage(email, projectId);
        delete(projectId, deviceId);
    }

    /** Project that owns the device; used to authorize device-scoped requests such as telemetry. */
    public UUID findProjectId(UUID deviceId) {
        return deviceRepository.findById(deviceId)
                .map(device -> device.getProject().getId())
                .orElseThrow(() -> new NotFoundException("Device not found: " + deviceId));
    }

    // --- Unauthorized variants: trusted internal callers only (e.g. the MQTT consumer) ---

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

    @Transactional
    public void delete(UUID projectId, UUID deviceId) {
        deviceRepository.delete(findById(projectId, deviceId));
    }

    public boolean exists(UUID deviceId) {
        return deviceRepository.existsById(deviceId);
    }

    @Transactional
    public void markOnline(UUID deviceId) {
        DeviceEntity device = deviceRepository.findById(deviceId)
                .orElseThrow(() -> new NotFoundException("Device not found: " + deviceId));
        device.markOnline(Instant.now());
    }

    private void requireProject(UUID projectId) {
        if (!projectRepository.existsById(projectId)) {
            throw new NotFoundException("Project not found: " + projectId);
        }
    }
}