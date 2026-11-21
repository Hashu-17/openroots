package com.openroots.device.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.openroots.common.exception.NotFoundException;
import com.openroots.device.dto.DeviceCreateRequest;
import com.openroots.device.dto.SensorCreateRequest;
import com.openroots.device.model.DeviceEntity;
import com.openroots.device.repository.DeviceRepository;
import com.openroots.project.model.ProjectEntity;
import com.openroots.project.repository.ProjectRepository;
import com.openroots.project.service.ProjectAuthorizationService;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

@ExtendWith(MockitoExtension.class)
class DeviceServiceTest {

    @Mock
    private DeviceRepository deviceRepository;

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private ProjectAuthorizationService authorizationService;

    @Mock
    private MosquittoDeviceProvisioner brokerProvisioner;

    @InjectMocks
    private DeviceService deviceService;

    @Test
    void deletingADeviceRemovesItsBrokerLoginFirst() {
        UUID projectId = UUID.randomUUID();
        DeviceEntity device = new DeviceEntity(new com.openroots.project.model.ProjectEntity("p"), "d", "t");
        UUID deviceId = UUID.randomUUID();
        when(projectRepository.existsById(projectId)).thenReturn(true);
        when(deviceRepository.findByIdAndProject_Id(deviceId, projectId)).thenReturn(Optional.of(device));

        deviceService.delete("owner@example.com", projectId, deviceId);

        org.mockito.InOrder order = org.mockito.Mockito.inOrder(brokerProvisioner, deviceRepository);
        order.verify(brokerProvisioner).delete(deviceId);
        order.verify(deviceRepository).delete(device);
    }

    @Test
    void aDeviceStaysWhenItsBrokerLoginCannotBeRemoved() {
        UUID projectId = UUID.randomUUID();
        DeviceEntity device = new DeviceEntity(new com.openroots.project.model.ProjectEntity("p"), "d", "t");
        UUID deviceId = UUID.randomUUID();
        when(projectRepository.existsById(projectId)).thenReturn(true);
        when(deviceRepository.findByIdAndProject_Id(deviceId, projectId)).thenReturn(Optional.of(device));
        org.mockito.Mockito.doThrow(new MqttProvisioningException("broker down")).when(brokerProvisioner).delete(deviceId);

        assertThatThrownBy(() -> deviceService.delete("owner@example.com", projectId, deviceId))
                .isInstanceOf(MqttProvisioningException.class);
        verify(deviceRepository, never()).delete(any());
    }

    @Test
    void nonMemberCannotRegisterListReadOrDeleteDevicesInAnotherProject() {
        UUID projectId = UUID.randomUUID();
        UUID deviceId = UUID.randomUUID();
        String stranger = "stranger@example.com";
        NotFoundException hidden = new NotFoundException("Project not found: " + projectId);
        when(authorizationService.requireManage(stranger, projectId)).thenThrow(hidden);
        when(authorizationService.requireRead(stranger, projectId)).thenThrow(hidden);

        assertThatThrownBy(() -> deviceService.register(stranger, projectId, new DeviceCreateRequest(
                "d", "t", List.of(new SensorCreateRequest("c", null)))))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> deviceService.findByProject(stranger, projectId))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> deviceService.findById(stranger, projectId, deviceId))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> deviceService.delete(stranger, projectId, deviceId))
                .isInstanceOf(NotFoundException.class);
        verifyNoInteractions(deviceRepository);
    }

    @Test
    void viewerAndMemberCanReadButNotWriteDevices() {
        UUID projectId = UUID.randomUUID();
        String viewer = "viewer@example.com";
        when(authorizationService.requireManage(viewer, projectId)).thenThrow(new AccessDeniedException("no"));

        assertThatThrownBy(() -> deviceService.register(viewer, projectId, new DeviceCreateRequest(
                "d", "t", List.of(new SensorCreateRequest("c", null)))))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> deviceService.delete(viewer, projectId, UUID.randomUUID()))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> deviceService.findByIdForManage(viewer, projectId, UUID.randomUUID()))
                .isInstanceOf(AccessDeniedException.class);
        verify(projectRepository, never()).findById(any());
        verify(deviceRepository, never()).save(any());
        verify(deviceRepository, never()).delete(any());
    }

    @Test
    void deviceFromAnotherProjectIsNotFoundEvenForAMember() {
        UUID projectId = UUID.randomUUID();
        UUID foreignDeviceId = UUID.randomUUID();
        when(projectRepository.existsById(projectId)).thenReturn(true);
        when(deviceRepository.findByIdAndProject_Id(foreignDeviceId, projectId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> deviceService.findById("member@example.com", projectId, foreignDeviceId))
                .isInstanceOf(NotFoundException.class);
    }

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