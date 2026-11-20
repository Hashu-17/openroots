# Dynamic View: MQTT Telemetry Ingestion

This view follows one telemetry publication through the current Spring MQTT integration, including the live push to browsers that are watching the device. The broker subscription is configured in `MqttConfig`; `MqttTelemetryConsumer` derives the device UUID from the topic, parses the payload, and passes the reading to `TelemetryService` for storage.

```mermaid
sequenceDiagram
    autonumber
    participant Device as IoT device
    participant Broker as Mosquitto broker
    participant Adapter as Spring MQTT inbound flow
    participant Consumer as MqttTelemetryConsumer
    participant Service as TelemetryService
    participant Store as TimescaleDB / PostgreSQL
    participant Hub as LiveTelemetryHub
    participant Browser as Signed-in browser

    Device->>Broker: Publish JSON to openroots/v1/devices/{deviceId}/telemetry
    Broker->>Adapter: Deliver matching subscribed message
    Adapter->>Consumer: Pass topic and payload
    Consumer->>Consumer: Derive device ID from topic and parse JSON
    Consumer->>Service: Submit telemetry request
    Service->>Store: Resolve sensor and upsert reading
    Store-->>Service: Persisted result
    Service-->>Consumer: Ingestion complete
    Service-)Hub: Readings committed (published after the transaction commits)
    Hub-)Browser: telemetry.reading over the authenticated WebSocket
```

The current MQTT request uses `deviceId`, `capturedAt`, and a channel-to-value `payload`. The broker topic also carries a device ID; the consumer rejects a body `deviceId` that disagrees with the topic. See the implementation in `MqttTelemetryConsumer` and the [telemetry contract](../contracts/mqtt-telemetry.md) for the distinction between current behavior and stricter design requirements.
