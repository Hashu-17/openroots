# Ingress Flow (dynamic view)

This shows how external clients enter the platform through REST and MQTT, pass through authentication and application modules, and reach PostgreSQL.

The view spans C4 abstractions rather than representing one C4 level:

- **C2 containers:** REST controllers, MQTT listener, and PostgreSQL.
- **C3 components/modules:** authentication, device, telemetry, command, alert, and user modules.


For the implemented MQTT path, see  [MQTT telemetry ingestion (dynamic view)](c4-telemetry-ingestion.md).

```mermaid
flowchart LR
    subgraph CLIENTS["Clients"]
        DASHBOARD["Web Dashboard"]
        MOBILE["Flutter Mobile App"]
        DEVICES["IoT Devices"]
    end
    subgraph TRANSPORT["Transport Layer"]
        REST["REST Controllers"]
        MQTT_IN["MQTT Listener"]
    end
    subgraph SUBMODULES["Application Modules"]
        TELEMETRY["Telemetry Module"]
        COMMAND["Command Module"]
        ALERT["Alert Module"]
        USER["User Module"]
    end
    DASHBOARD -- HTTPS --> REST
    MOBILE -- HTTPS --> REST
    DEVICES -- MQTT --> MQTT_IN
    DEVICES -. HTTPS (no MQTT) .-> REST
    REST --> AUTH["Authentication"]
    MQTT_IN --> AUTH
    AUTH --> DEVICE["Device Module"]
    DEVICE --> TELEMETRY & COMMAND & ALERT & USER & DB[("PostgreSQL")]
    TELEMETRY --> DB
    COMMAND --> DB
    ALERT --> DB
    USER --> DB

    DASHBOARD:::clients
    MOBILE:::clients
    DEVICES:::clients
    REST:::transport
    MQTT_IN:::transport
    AUTH:::application
    DEVICE:::application
    TELEMETRY:::application
    COMMAND:::application
    ALERT:::application
    USER:::application
    DB:::storage
    classDef clients fill:#E6F1FB,stroke:#185FA5,color:#0C447C
    classDef transport fill:#E1F5EE,stroke:#0F6E56,color:#085041
    classDef application fill:#EEEDFE,stroke:#534AB7,color:#3C3489
    classDef storage fill:#FAEEDA,stroke:#854F0B,color:#633806
```
