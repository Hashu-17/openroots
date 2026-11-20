# Level 3: Spring backend components

The Spring backend serves the REST API: generated API interfaces route HTTP requests to controllers, which use application services and repositories to access the database. It does not consume MQTT on this branch; the Go ingestion service does, and reads the device registry from Spring's internal endpoint. A provisioner keeps each device's broker login in step with its credentials.

```mermaid
flowchart LR
    contract["OpenAPI contract<br/>docs/openapi.yaml"]
    ingestion["Go ingestion service"]
    broker{{"MQTT broker<br/>Eclipse Mosquitto"}}
    database[("TimescaleDB<br/>PostgreSQL")]

    subgraph backend["Spring Boot backend · components"]
        direction LR
        generated["Generated API interfaces<br/>and models"]
        rest["REST controllers<br/>Auth · Project · Device · Telemetry"]
        registry["Ingestion registry endpoint<br/>IngestionRegistryController"]
        services["Application services<br/>Auth · Project · Device · Telemetry"]
        repositories["Persistence repositories<br/>JPA · JDBC"]
        provisioner["Broker provisioner<br/>Mosquitto dynamic security"]
        expiry["Credential expiry job"]
    end

    contract -->|"Generates at Maven build"| generated
    generated -->|"Implemented by"| rest
    rest -->|"Calls"| services
    ingestion -->|"Device registry · shared key"| registry
    registry -->|"Reads devices and sensors"| repositories
    services -->|"Queries and writes"| repositories
    repositories -->|"JPA / JDBC"| database
    services -->|"Set, disable or delete a device login"| provisioner
    expiry -->|"Disables expired logins"| provisioner
    provisioner -->|"Control topic"| broker

    classDef external fill:#999999,color:#fff,stroke:#666,stroke-width:2px;
    classDef component fill:#85bbf0,color:#111,stroke:#5d82a8,stroke-width:2px;
    classDef data fill:#438dd5,color:#fff,stroke:#2e6295,stroke-width:2px;
    class contract,ingestion,broker external;
    class generated,rest,registry,services,repositories,provisioner,expiry component;
    class database data;
```

The source names are intentionally grouped where multiple related controller or repository classes share one responsibility. The OpenAPI file remains the API contract; generated code under `target/` is build output.
