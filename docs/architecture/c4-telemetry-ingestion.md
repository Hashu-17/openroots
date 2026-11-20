# Dynamic View: MQTT Telemetry Ingestion

This view follows one telemetry publication through the Go ingestion service (`ingestion/`). Spring does not subscribe to MQTT on this branch. Go keeps an in-memory copy of the registered devices and sensors, refreshed from the backend's internal registry endpoint, so it never calls Spring per message.

```mermaid
sequenceDiagram
    autonumber
    participant Device as IoT device
    participant Broker as Mosquitto broker
    participant Consumer as Go MQTT consumer
    participant Registry as Go device registry (cached)
    participant Spring as Spring backend
    participant Workers as Go storage workers
    participant Store as TimescaleDB / PostgreSQL
    participant Hub as Go live hub
    participant Browser as Signed-in browser

    loop every few seconds
        Registry->>Spring: GET /internal/v1/ingestion/device-registry (shared key)
        Spring-->>Registry: Devices, projects and sensor channels
    end
    Device->>Broker: Publish JSON to openroots/v1/devices/{deviceId}/telemetry
    Broker->>Consumer: Deliver message (shared subscription, QoS 1)
    Consumer->>Registry: Resolve topic device ID and allowed channels
    Consumer->>Consumer: Validate envelope, timestamp and numeric values
    Consumer->>Workers: Queue validated readings (bounded queue)
    Workers->>Store: Upsert readings and mark device online in one transaction
    Store-->>Workers: Committed
    Workers-)Hub: Publish the committed readings
    Hub-)Browser: telemetry.reading over the authenticated WebSocket
    Workers-->>Broker: Acknowledge the MQTT message
```

The message body is `{"capturedAt": ..., "payload": {...}}`. The topic identifies the device, so a body `deviceId` is rejected as an unexpected field. See the [telemetry contract](../contracts/mqtt-telemetry.md) for every validation rule and the storage mapping, and [`ingestion/README.md`](../../ingestion/README.md) for configuration.
