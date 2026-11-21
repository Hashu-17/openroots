package com.openroots.telemetry.live;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;

/**
 * Fans committed readings out to authorized WebSocket subscribers, per device.
 * A subscriber that cannot keep up is disconnected so it never slows ingestion.
 */
@Component
public class LiveTelemetryHub {

    static final int MAX_SUBSCRIBERS = 256;
    private static final int SEND_TIME_LIMIT_MS = 5_000;
    private static final int BUFFER_SIZE_LIMIT_BYTES = 64 * 1024;

    private static final Logger log = LoggerFactory.getLogger(LiveTelemetryHub.class);

    private final ObjectMapper objectMapper;
    private final Map<UUID, Set<WebSocketSession>> subscribers = new ConcurrentHashMap<>();
    private final Map<String, WebSocketSession> decorated = new ConcurrentHashMap<>();
    private final AtomicInteger count = new AtomicInteger();

    public LiveTelemetryHub(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** Returns the session wrapper to send through, or null when the hub is full. */
    public WebSocketSession subscribe(UUID deviceId, WebSocketSession session) {
        if (count.incrementAndGet() > MAX_SUBSCRIBERS) {
            count.decrementAndGet();
            return null;
        }
        WebSocketSession safe = new ConcurrentWebSocketSessionDecorator(
                session, SEND_TIME_LIMIT_MS, BUFFER_SIZE_LIMIT_BYTES,
                ConcurrentWebSocketSessionDecorator.OverflowStrategy.TERMINATE);
        decorated.put(session.getId(), safe);
        subscribers.computeIfAbsent(deviceId, ignored -> ConcurrentHashMap.newKeySet()).add(safe);
        return safe;
    }

    public void unsubscribe(UUID deviceId, WebSocketSession session) {
        WebSocketSession safe = decorated.remove(session.getId());
        if (safe == null) {
            return;
        }
        count.decrementAndGet();
        subscribers.computeIfPresent(deviceId, (ignored, sessions) -> {
            sessions.remove(safe);
            return sessions.isEmpty() ? null : sessions;
        });
    }

    int subscriberCount() {
        return count.get();
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onTelemetryCommitted(TelemetryCommitted committed) {
        Set<WebSocketSession> sessions = subscribers.get(committed.deviceId());
        if (sessions == null || sessions.isEmpty()) {
            return;
        }
        for (TelemetryCommitted.StoredReading reading : committed.readings()) {
            TextMessage message;
            try {
                message = new TextMessage(objectMapper.writeValueAsString(new LiveTelemetryEvent(
                        LiveTelemetryEvent.TYPE,
                        committed.deviceId().toString(),
                        reading.sensorId().toString(),
                        reading.channelName(),
                        reading.value(),
                        committed.recordedAt().toString())));
            } catch (JsonProcessingException exception) {
                log.warn("Could not serialize live telemetry event", exception);
                return;
            }
            for (WebSocketSession session : sessions) {
                send(session, message);
            }
        }
    }

    private void send(WebSocketSession session, TextMessage message) {
        if (!session.isOpen()) {
            return;
        }
        try {
            session.sendMessage(message);
        } catch (IOException | RuntimeException exception) {
            try {
                session.close(CloseStatus.SESSION_NOT_RELIABLE);
            } catch (IOException ignored) {
                // The connection is already gone.
            }
        }
    }
}
