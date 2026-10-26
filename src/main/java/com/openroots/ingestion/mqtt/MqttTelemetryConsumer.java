package com.openroots.ingestion.mqtt;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openroots.telemetry.dto.TelemetryIngestRequest;
import com.openroots.telemetry.service.TelemetryService;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.integration.core.GenericHandler;
import org.springframework.messaging.MessageHeaders;
import org.springframework.stereotype.Component;

@Component
public class MqttTelemetryConsumer implements GenericHandler<Object> {

    private final ObjectMapper objectMapper;
    private final TelemetryService telemetryService;

    public MqttTelemetryConsumer(ObjectMapper objectMapper, TelemetryService telemetryService) {
        this.objectMapper = objectMapper;
        this.telemetryService = telemetryService;
    }

    @Override
    public Object handle(Object payload, MessageHeaders headers) {
        try {
            String topic = String.valueOf(headers.get("mqtt_receivedTopic"));
            UUID deviceId = deviceIdFromTopic(topic);
            JsonNode body = objectMapper.readTree(payload instanceof byte[] bytes
                    ? new String(bytes, StandardCharsets.UTF_8)
                    : String.valueOf(payload));

            UUID bodyDeviceId = body.hasNonNull("deviceId")
                    ? UUID.fromString(body.get("deviceId").asText())
                    : deviceId;
            if (!deviceId.equals(bodyDeviceId)) {
                throw new IllegalArgumentException("MQTT deviceId does not match topic");
            }

            TelemetryIngestRequest request = objectMapper.treeToValue(body, TelemetryIngestRequest.class);
            telemetryService.ingest(new TelemetryIngestRequest(deviceId, request.capturedAt(), request.payload()));
            return null;
        } catch (Exception exception) {
            throw new IllegalArgumentException("Unable to ingest MQTT telemetry", exception);
        }
    }

    private UUID deviceIdFromTopic(String topic) {
        String[] segments = topic.split("/");
        if (segments.length != 5
                || !"openroots".equals(segments[0])
                || !"v1".equals(segments[1])
                || !"devices".equals(segments[2])
                || !"telemetry".equals(segments[4])) {
            throw new IllegalArgumentException("Unexpected MQTT telemetry topic: " + topic);
        }
        return UUID.fromString(segments[3]);
    }
}
