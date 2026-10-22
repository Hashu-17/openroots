# Level 1: System Context

The system boundary covers the OpenRoots platform. People use the web interface to inspect projects, register devices, and view telemetry. Registered devices publish telemetry through MQTT.

```mermaid
flowchart LR
    operator(["Person :<br/>Project operator"])
    device["External System: <br/>Registered IoT device"]

    subgraph platform["Software system: OpenRoots IoT Platform"]
        openroots("Manages projects and devices, ingests telemetry, and presents device data")
    end

    operator -->|"Uses web UI and REST API · HTTP/JSON"| openroots
    device -->|"Publishes timestamped readings · MQTT"| openroots

    classDef person fill:#08427b,color:#fff,stroke:#052e56,stroke-width:2px;
    classDef external fill:#999999,color:#fff,stroke:#666,stroke-width:2px;
    classDef system fill:#1168bd,color:#fff,stroke:#0b4884,stroke-width:2px;
    class operator person;
    class device external;
    class openroots system;
```

Authentication is not currently enforced by the Spring security configuration; see [authentication status](../security/authentication.md).
