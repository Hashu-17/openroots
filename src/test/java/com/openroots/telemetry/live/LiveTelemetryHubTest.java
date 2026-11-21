package com.openroots.telemetry.live;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;

class LiveTelemetryHubTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final LiveTelemetryHub hub = new LiveTelemetryHub(objectMapper);

    private WebSocketSession openSession(String id) {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn(id);
        when(session.isOpen()).thenReturn(true);
        return session;
    }

    @Test
    void sendsEachCommittedReadingOnlyToThatDevicesSubscribers() throws Exception {
        UUID deviceId = UUID.randomUUID();
        UUID otherDevice = UUID.randomUUID();
        UUID sensorId = UUID.randomUUID();
        WebSocketSession subscriber = openSession("a");
        WebSocketSession bystander = openSession("b");
        hub.subscribe(deviceId, subscriber);
        hub.subscribe(otherDevice, bystander);

        Instant recordedAt = Instant.parse("2026-11-21T10:15:30.125Z");
        hub.onTelemetryCommitted(new TelemetryCommitted(deviceId, recordedAt,
                List.of(new TelemetryCommitted.StoredReading(sensorId, "temperature", 21.5))));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<WebSocketMessage<?>> sent = ArgumentCaptor.forClass(WebSocketMessage.class);
        verify(subscriber).sendMessage(sent.capture());
        LiveTelemetryEvent event = objectMapper.readValue(
                ((TextMessage) sent.getValue()).getPayload(), LiveTelemetryEvent.class);
        assertThat(event).isEqualTo(new LiveTelemetryEvent("telemetry.reading", deviceId.toString(),
                sensorId.toString(), "temperature", 21.5, "2026-11-21T10:15:30.125Z"));
        verify(bystander, never()).sendMessage(any());
    }

    @Test
    void refusesSubscribersBeyondCapacity() {
        UUID deviceId = UUID.randomUUID();
        for (int index = 0; index < LiveTelemetryHub.MAX_SUBSCRIBERS; index++) {
            assertThat(hub.subscribe(deviceId, openSession("s" + index))).isNotNull();
        }
        assertThat(hub.subscribe(deviceId, openSession("overflow"))).isNull();

        hub.unsubscribe(deviceId, openSession("s0"));
        assertThat(hub.subscriberCount()).isEqualTo(LiveTelemetryHub.MAX_SUBSCRIBERS - 1);
    }
}
