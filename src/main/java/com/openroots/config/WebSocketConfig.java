package com.openroots.config;

import com.openroots.telemetry.live.LiveTelemetryHandler;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/** Registers the live telemetry WebSocket. Authentication happens in-band; see {@link LiveTelemetryHandler}. */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final LiveTelemetryHandler liveTelemetryHandler;
    private final List<String> allowedOrigins;

    public WebSocketConfig(
            LiveTelemetryHandler liveTelemetryHandler,
            @Value("${openroots.live.allowed-origins:http://localhost:5173,http://127.0.0.1:5173}")
            List<String> allowedOrigins) {
        this.liveTelemetryHandler = liveTelemetryHandler;
        this.allowedOrigins = allowedOrigins;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(liveTelemetryHandler, "/api/v1/devices/*/telemetry/live")
                .setAllowedOrigins(allowedOrigins.toArray(String[]::new));
    }
}
