package com.openroots.telemetry.live;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openroots.auth.TokenProvider;
import com.openroots.common.exception.NotFoundException;
import com.openroots.device.service.DeviceService;
import com.openroots.project.service.ProjectAuthorizationService;
import java.net.URI;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

class LiveTelemetryHandlerTest {

    private static final String SECRET = "b3BlbnJvb3RzLWxpdmUtdGVzdC1zZWNyZXQtdGhpcnR5LXR3by1ieXRlcy1sb25n";
    private static final String EMAIL = "member@example.com";

    private final TokenProvider tokenProvider = new TokenProvider(SECRET, Duration.ofMinutes(15));
    private final DeviceService deviceService = mock(DeviceService.class);
    private final ProjectAuthorizationService authorizationService = mock(ProjectAuthorizationService.class);
    private final LiveTelemetryHub hub = new LiveTelemetryHub(new ObjectMapper());
    private LiveTelemetryHandler handler;

    private final UUID deviceId = UUID.randomUUID();
    private final UUID projectId = UUID.randomUUID();
    private WebSocketSession session;

    @BeforeEach
    void setUp() throws Exception {
        handler = new LiveTelemetryHandler(tokenProvider, deviceService, authorizationService, hub, new ObjectMapper());
        session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn("session-1");
        when(session.isOpen()).thenReturn(true);
        when(session.getUri()).thenReturn(URI.create("ws://localhost/api/v1/devices/" + deviceId + "/telemetry/live"));
        when(deviceService.findProjectId(deviceId)).thenReturn(projectId);
        handler.afterConnectionEstablished(session);
    }

    @AfterEach
    void tearDown() {
        handler.destroy();
    }

    private TextMessage auth(String token) {
        return new TextMessage("{\"type\":\"auth\",\"token\":\"" + token + "\"}");
    }

    @Test
    void memberIsSubscribedAndToldTheStreamIsReady() throws Exception {
        handler.handleTextMessage(session, auth(tokenProvider.createToken(EMAIL)));

        verify(authorizationService).requireRead(EMAIL, projectId);
        verify(session).sendMessage(new TextMessage("{\"type\":\"ready\"}"));
        verify(session, never()).close(any());
        assertThat(hub.subscriberCount()).isEqualTo(1);

        handler.afterConnectionClosed(session, CloseStatus.NORMAL);
        assertThat(hub.subscriberCount()).isZero();
    }

    @Test
    void invalidTokenIsRejected() throws Exception {
        handler.handleTextMessage(session, auth("not-a-jwt"));

        verify(session).close(LiveTelemetryHandler.UNAUTHORIZED);
        assertThat(hub.subscriberCount()).isZero();
    }

    @Test
    void tokenSignedWithAnotherKeyIsRejected() throws Exception {
        TokenProvider other = new TokenProvider(
                "YW5vdGhlci1zZWNyZXQtdGhhdC1pcy1hdC1sZWFzdC10aGlydHktdHdvLWJ5dGVz", Duration.ofMinutes(15));

        handler.handleTextMessage(session, auth(other.createToken(EMAIL)));

        verify(session).close(LiveTelemetryHandler.UNAUTHORIZED);
    }

    @Test
    void nonMemberSeesNotFound() throws Exception {
        when(authorizationService.requireRead(EMAIL, projectId))
                .thenThrow(new NotFoundException("Project not found: " + projectId));

        handler.handleTextMessage(session, auth(tokenProvider.createToken(EMAIL)));

        verify(session).close(LiveTelemetryHandler.NOT_FOUND);
        assertThat(hub.subscriberCount()).isZero();
    }

    @Test
    void firstMessageMustBeAnAuthMessage() throws Exception {
        handler.handleTextMessage(session, new TextMessage("{\"type\":\"hello\"}"));

        verify(session).close(LiveTelemetryHandler.BAD_REQUEST);
        verify(authorizationService, never()).requireRead(any(), eq(projectId));
    }

    @Test
    void pathMustContainADeviceUuidAndNoQuery() {
        assertThat(LiveTelemetryHandler.deviceIdFrom(
                URI.create("ws://h/api/v1/devices/" + deviceId + "/telemetry/live"))).isEqualTo(deviceId);
        assertThat(LiveTelemetryHandler.deviceIdFrom(
                URI.create("ws://h/api/v1/devices/" + deviceId + "/telemetry/live?token=x"))).isNull();
        assertThat(LiveTelemetryHandler.deviceIdFrom(URI.create("ws://h/api/v1/devices/abc/telemetry/live"))).isNull();
    }
}
