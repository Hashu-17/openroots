# level 2: containers

The platform runs as five application containers in Docker Compose. The browser loads the React UI from Nginx; Nginx serves the static app and proxies `/api/` requests to Spring Boot. Devices publish MQTT messages to Mosquitto; the Go ingestion service consumes them, validates them against the device registry it reads from Spring, and writes readings to TimescaleDB. It also streams new readings to open browsers over an authenticated WebSocket. Spring persists application data, serves telemetry reads, and keeps each device's MQTT login in step with its credentials.

```mermaid
flowchart LR
    operator(["Person<br/>Project operator"])
    device["External software system<br/>Registered IoT device"]

    subgraph platform["OpenRoots IoT Platform · containers"]
        direction LR
        web["Web application<br/>React · TypeScript · Nginx"]
        broker{{"MQTT broker<br/>Eclipse Mosquitto"}}
        backend["Spring backend<br/>Java 21 · Spring Boot"]
        ingestion["Ingestion service<br/>Go"]
        database[("Application database<br/>TimescaleDB · PostgreSQL")]
    end

    operator -->|"Uses · HTTP"| web
    web -->|"/api/v1 · HTTP/JSON<br/>Nginx proxy"| backend
    device -->|"Publishes telemetry<br/>MQTT"| broker
    broker -->|"Delivers subscribed messages<br/>MQTT"| ingestion
    web -->|"Live WebSocket<br/>Nginx proxy"| ingestion
    backend -->|"Manages device logins<br/>MQTT dynamic security"| broker
    ingestion -->|"Device registry · HTTP"| backend
    ingestion -->|"Writes telemetry · pgx"| database
    backend -->|"Reads and writes · JDBC"| database

    classDef person fill:#08427b,color:#fff,stroke:#052e56,stroke-width:2px;
    classDef external fill:#999999,color:#fff,stroke:#666,stroke-width:2px;
    classDef container fill:#438dd5,color:#fff,stroke:#2e6295,stroke-width:2px;
    classDef data fill:#438dd5,color:#fff,stroke:#2e6295,stroke-width:2px;
    class operator person;
    class device external;
    class web,backend,ingestion,broker container;
    class database data;
```

The Compose host publishes the frontend on port `5173`, the backend on `8080`, the ingestion service on `127.0.0.1:8081`, the broker on `1883`, and the database on `5432` by default. These host ports can be overridden with the corresponding environment variables in `.env`.
