# level 2: containers

The platform runs as four application containers in Docker Compose. The browser loads the React UI from Nginx; Nginx serves the static app and proxies `/api/` requests to Spring Boot. Devices publish MQTT messages to Mosquitto, which Spring Boot consumes. The backend persists application and telemetry data in TimescaleDB/PostgreSQL.

```mermaid
flowchart LR
    operator(["Person<br/>Project operator"])
    device["External software system<br/>Registered IoT device"]

    subgraph platform["OpenRoots IoT Platform · containers"]
        direction LR
        web["Web application<br/>React · TypeScript · Nginx"]
        broker{{"MQTT broker<br/>Eclipse Mosquitto"}}
        backend["Spring backend<br/>Java 21 · Spring Boot"]
        database[("Application database<br/>TimescaleDB · PostgreSQL")]
    end

    operator -->|"Uses · HTTP"| web
    web -->|"/api/v1 · HTTP/JSON<br/>Nginx proxy"| backend
    device -->|"Publishes telemetry<br/>MQTT"| broker
    broker -->|"Delivers subscribed messages<br/>MQTT"| backend
    backend -->|"Reads and writes · JDBC"| database

    classDef person fill:#08427b,color:#fff,stroke:#052e56,stroke-width:2px;
    classDef external fill:#999999,color:#fff,stroke:#666,stroke-width:2px;
    classDef container fill:#438dd5,color:#fff,stroke:#2e6295,stroke-width:2px;
    classDef data fill:#438dd5,color:#fff,stroke:#2e6295,stroke-width:2px;
    class operator person;
    class device external;
    class web,backend,broker container;
    class database data;
```

The Compose host publishes the frontend on port `5173`, the backend on `8080`, the broker on `1883`, and the database on `5432` by default. These host ports can be overridden with the corresponding environment variables in `.env`.
