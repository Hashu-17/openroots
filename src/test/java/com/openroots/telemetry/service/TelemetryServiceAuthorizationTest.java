package com.openroots.telemetry.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.openroots.common.exception.NotFoundException;
import com.openroots.device.service.DeviceService;
import com.openroots.project.service.ProjectAuthorizationService;
import com.openroots.telemetry.dto.TelemetryIngestRequest;
import com.openroots.telemetry.repository.TelemetryRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;

@ExtendWith(MockitoExtension.class)
class TelemetryServiceAuthorizationTest {

    private static final String EMAIL = "person@example.com";

    @Mock
    private DeviceService deviceService;

    @Mock
    private TelemetryRepository telemetryRepository;

    @Mock
    private ProjectAuthorizationService authorizationService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private TelemetryService telemetryService;

    private final UUID deviceId = UUID.randomUUID();
    private final UUID projectId = UUID.randomUUID();

    @Test
    void viewerCannotIngestTelemetry() {
        when(deviceService.findProjectId(deviceId)).thenReturn(projectId);
        when(authorizationService.requireDeviceUse(EMAIL, projectId)).thenThrow(new AccessDeniedException("no"));

        assertThatThrownBy(() -> telemetryService.ingest(
                EMAIL, new TelemetryIngestRequest(deviceId, Instant.now(), Map.of("temperature", 21.5))))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(telemetryRepository);
    }

    @Test
    void nonMemberCannotIngestOrReadAnotherProjectsTelemetry() {
        when(deviceService.findProjectId(deviceId)).thenReturn(projectId);
        NotFoundException hidden = new NotFoundException("Project not found: " + projectId);
        when(authorizationService.requireDeviceUse(EMAIL, projectId)).thenThrow(hidden);
        when(authorizationService.requireRead(EMAIL, projectId)).thenThrow(hidden);

        assertThatThrownBy(() -> telemetryService.ingest(
                EMAIL, new TelemetryIngestRequest(deviceId, Instant.now(), Map.of("temperature", 1.0))))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> telemetryService.findByDevice(EMAIL, deviceId))
                .isInstanceOf(NotFoundException.class);
        verifyNoInteractions(telemetryRepository);
    }

    @Test
    void listingAllTelemetryIsScopedToTheCallersProjects() {
        when(telemetryRepository.findAllForUser(EMAIL)).thenReturn(List.of());

        telemetryService.findAll(EMAIL);

        verify(telemetryRepository).findAllForUser(EMAIL);
    }
}
