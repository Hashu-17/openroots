package com.openroots.ingestion.device.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.openroots.ingestion.device.dto.DeviceCreateRequest;
import com.openroots.ingestion.device.dto.SensorCreateRequest;
import com.openroots.ingestion.device.model.DeviceEntity;
import com.openroots.ingestion.device.repository.DeviceRepository;
import com.openroots.ingestion.project.model.ProjectEntity;
import com.openroots.ingestion.project.repository.ProjectRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DeviceServiceTest {

    @Mock
    private DeviceRepository deviceRepository;

    @Mock
    private ProjectRepository projectRepository;

    @InjectMocks
    private DeviceService deviceService;

    @Test
    void registersDeviceWithNestedSensors() {
        UUID projectId = UUID.randomUUID();
        ProjectEntity project = new ProjectEntity("Test project");
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
        when(deviceRepository.save(any(DeviceEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        DeviceEntity device = deviceService.register(projectId, new DeviceCreateRequest(
                "Garden device",
                "weather-station",
                List.of(
                        new SensorCreateRequest("temperature", "C"),
                        new SensorCreateRequest("rain", "mm"),
                        new SensorCreateRequest("uv", "index"))));

        assertThat(device.getName()).isEqualTo("Garden device");
        assertThat(device.getSensors())
                .extracting(sensor -> sensor.getChannelName())
                .containsExactly("temperature", "rain", "uv");
    }

    @Test
    void rejectsDuplicateSensorChannels() {
        UUID projectId = UUID.randomUUID();
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(new ProjectEntity("Test project")));

        assertThatThrownBy(() -> deviceService.register(projectId, new DeviceCreateRequest(
                "Garden device",
                "weather-station",
                List.of(
                        new SensorCreateRequest("temperature", "C"),
                        new SensorCreateRequest(" temperature ", "C")))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Sensor channel names must be unique within a device");
    }
}