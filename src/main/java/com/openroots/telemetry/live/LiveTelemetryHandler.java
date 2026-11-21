package com.openroots.telemetry.live;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openroots.auth.TokenProvider;
import com.openroots.common.exception.NotFoundException;
import com.openroots.device.service.DeviceService;
import com.openroots.project.service.ProjectAuthorizationService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/**
 * Live telemetry for one device at {@code /api/v1/devices/{deviceId}/telemetry/live}.
 *
 * <p>Browsers cannot set an Authorization header on a WebSocket, and a token in the URL
 * would leak into logs, so the client authenticates in-band: its first message must be
 * {@code {"type":"auth","token":"<jwt>"}} within {@link #AUTH_TIMEOUT}. Any project member
 * may subscribe (same rule as reading telemetry); non-members get "not found" (BR-010).
 * The server answers {@code {"type":"ready"}}, then streams {@link LiveTelemetryEvent}s
 * and closes the socket when the access token expires.
 *
 * <p>Close codes: 4400 bad request, 4401 missing/invalid/expired token, 4404 device not
 * found or not visible, 4408 no auth message in time, 1013 subscriber capacity reached.
 */
@Component
public class LiveTelemetryHandler extends TextWebSocketHandler implements DisposableBean {

    static final Duration AUTH_TIMEOUT = Duration.ofSeconds(10);
    static final CloseStatus BAD_REQUEST = new CloseStatus(4400, "bad request");
    static final CloseStatus UNAUTHORIZED = new CloseStatus(4401, "unauthorized");
    static final CloseStatus NOT_FOUND = new CloseStatus(4404, "device not found");
    static final CloseStatus AUTH_TIMED_OUT = new CloseStatus(4408, "authentication timeout");
    static final CloseStatus TOKEN_EXPIRED = new CloseStatus(4401, "token expired");

    private static final Pattern PATH = Pattern.compile("^/api/v1/devices/([0-9a-fA-F-]{36})/telemetry/live$");

    private final TokenProvider tokenProvider;
    private final DeviceService deviceService;
    private final ProjectAuthorizationService authorizationService;
    private final LiveTelemetryHub hub;
    private final ObjectMapper objectMapper;
    private final ScheduledExecutorService timers = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "live-telemetry-timers");
        thread.setDaemon(true);
        return thread;
    });
    private final Map<String, Connection> connections = new ConcurrentHashMap<>();

    public LiveTelemetryHandler(
            TokenProvider tokenProvider,
            DeviceService deviceService,
            ProjectAuthorizationService authorizationService,
            LiveTelemetryHub hub,
            ObjectMapper objectMapper) {
        this.tokenProvider = tokenProvider;
        this.deviceService = deviceService;
        this.authorizationService = authorizationService;
        this.hub = hub;
        this.objectMapper = objectMapper;
    }

    private static final class Connection {
        private final UUID deviceId;
        private volatile boolean authenticated;
        private volatile ScheduledFuture<?> timer;

        private Connection(UUID deviceId) {
            this.deviceId = deviceId;
        }
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws IOException {
        UUID deviceId = deviceIdFrom(session.getUri());
        if (deviceId == null) {
            session.close(BAD_REQUEST);
            return;
        }
        Connection connection = new Connection(deviceId);
        connections.put(session.getId(), connection);
        connection.timer = timers.schedule(
                () -> closeQuietly(session, AUTH_TIMED_OUT), AUTH_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws IOException {
        Connection connection = connections.get(session.getId());
        if (connection == null || connection.authenticated) {
            return; // the stream is server-to-client only after authentication
        }
        connection.timer.cancel(false);

        String token = tokenFrom(message.getPayload());
        if (token == null) {
            session.close(BAD_REQUEST);
            return;
        }
        Claims claims;
        try {
            claims = tokenProvider.parse(token);
        } catch (JwtException | IllegalArgumentException invalid) {
            session.close(UNAUTHORIZED);
            return;
        }
        String email = claims.getSubject();
        if (email == null || email.isBlank()) {
            session.close(UNAUTHORIZED);
            return;
        }
        try {
            authorizationService.requireRead(email, deviceService.findProjectId(connection.deviceId));
        } catch (NotFoundException hidden) {
            session.close(NOT_FOUND);
            return;
        }

        WebSocketSession safe = hub.subscribe(connection.deviceId, session);
        if (safe == null) {
            session.close(CloseStatus.SERVICE_OVERLOAD);
            return;
        }
        connection.authenticated = true;
        long untilExpiry = claims.getExpiration() == null
                ? Long.MAX_VALUE
                : Math.max(0, claims.getExpiration().toInstant().toEpochMilli() - Instant.now().toEpochMilli());
        if (untilExpiry != Long.MAX_VALUE) {
            connection.timer = timers.schedule(
                    () -> closeQuietly(session, TOKEN_EXPIRED), untilExpiry, TimeUnit.MILLISECONDS);
        }
        safe.sendMessage(new TextMessage("{\"type\":\"ready\"}"));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Connection connection = connections.remove(session.getId());
        if (connection == null) {
            return;
        }
        if (connection.timer != null) {
            connection.timer.cancel(false);
        }
        hub.unsubscribe(connection.deviceId, session);
    }

    @Override
    public void destroy() {
        timers.shutdownNow();
    }

    static UUID deviceIdFrom(URI uri) {
        if (uri == null || uri.getRawQuery() != null) {
            return null;
        }
        Matcher matcher = PATH.matcher(uri.getPath());
        if (!matcher.matches()) {
            return null;
        }
        try {
            return UUID.fromString(matcher.group(1));
        } catch (IllegalArgumentException invalid) {
            return null;
        }
    }

    private String tokenFrom(String payload) {
        try {
            JsonNode body = objectMapper.readTree(payload);
            if (body == null || !"auth".equals(body.path("type").asText())) {
                return null;
            }
            String token = body.path("token").asText("");
            return token.isBlank() ? null : token;
        } catch (IOException malformed) {
            return null;
        }
    }

    private static void closeQuietly(WebSocketSession session, CloseStatus status) {
        try {
            if (session.isOpen()) {
                session.close(status);
            }
        } catch (IOException ignored) {
            // Already closed by the peer.
        }
    }
}
