# Level 3: Spring backend components

The Spring backend has three input paths: generated REST API interfaces route HTTP requests to controllers, the MQTT inbound adapter forwards broker messages to the telemetry consumer, and a WebSocket handler streams stored readings to authorized browsers. Application services and repositories access the database, and a provisioner keeps each device's broker login in step with its credentials.

```mermaid
flowchart LR
    contract["OpenAPI contract<br/>docs/openapi.yaml"]
    broker{{"MQTT broker<br/>Eclipse Mosquitto"}}
    database[("TimescaleDB<br/>PostgreSQL")]
    browser(["Signed-in browser"])

    subgraph backend["Spring Boot backend · components"]
        direction LR
        generated["Generated API interfaces<br/>and models"]
        rest["REST controllers<br/>Project · Device · Telemetry"]
        mqtt["MQTT inbound adapter<br/>Spring Integration"]
        consumer["Telemetry consumer<br/>MqttTelemetryConsumer"]
        services["Application services<br/>Project · Device · Telemetry"]
        repositories["Persistence repositories<br/>JPA · JDBC"]
        live["Live telemetry<br/>WebSocket handler and hub"]
        provisioner["Broker provisioner<br/>Mosquitto dynamic security"]
        expiry["Credential expiry job"]
    end

    contract -->|"Generates at Maven build"| generated
    generated -->|"Implemented by"| rest
    rest -->|"Calls"| services
    broker -->|"Subscribed MQTT messages"| mqtt
    mqtt -->|"Dispatches payload and topic"| consumer
    consumer -->|"Submits readings"| services
    services -->|"Queries and writes"| repositories
    repositories -->|"JPA / JDBC"| database
    services -->|"Readings, after commit"| live
    live -->|"Authenticated WebSocket"| browser
    services -->|"Set, disable or delete a device login"| provisioner
    expiry -->|"Disables expired logins"| provisioner
    provisioner -->|"Control topic"| broker

    classDef external fill:#999999,color:#fff,stroke:#666,stroke-width:2px;
    classDef component fill:#85bbf0,color:#111,stroke:#5d82a8,stroke-width:2px;
    classDef data fill:#438dd5,color:#fff,stroke:#2e6295,stroke-width:2px;
    class contract,broker,browser external;
    class generated,rest,mqtt,consumer,services,repositories,live,provisioner,expiry component;
    class database data;
```

The source names are intentionally grouped where multiple related controller or repository classes share one responsibility. The OpenAPI file remains the API contract; generated code under `target/` is build output.
