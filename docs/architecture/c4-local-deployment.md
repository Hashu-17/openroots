# Deployment view: Local Docker Compose

The local development deployment runs the platform containers on one Docker host. The browser and an optional MQTT test client run on the workstation outside the Compose network and connect through published host ports.

```mermaid
flowchart LR
    subgraph host["Developer workstation"]
        browser(["Browser"])
        mqttClient["MQTT test client"]
        subgraph compose["Docker Compose network"]
            direction LR
            frontend["Frontend container<br/>Nginx · port 80"]
            broker{{"Mosquitto container<br/>MQTT · port 1883"}}
            backend["Backend container<br/>Spring Boot · port 8080"]
            database[("Database container<br/>TimescaleDB · port 5432")]
        end
    end
    browser -->|"Host port 5173"| frontend
    frontend -->|"/api proxy and live WebSocket · backend:8080"| backend
    mqttClient -->|"Host loopback · 127.0.0.1:1883"| broker
    broker -->|"MQTT subscription"| backend
    backend -->|"JDBC"| database
    backend -->|"Device logins · dynamic security"| broker

    classDef client fill:#08427b,color:#fff,stroke:#052e56,stroke-width:2px;
    classDef external fill:#999999,color:#fff,stroke:#666,stroke-width:2px;
    classDef container fill:#438dd5,color:#fff,stroke:#2e6295,stroke-width:2px;
    classDef data fill:#438dd5,color:#fff,stroke:#2e6295,stroke-width:2px;
    class browser,mqttClient client;
    class frontend,broker,backend container;
    class database data;
```

Published port values are defaults. Compose allows overrides through `.env`; the MQTT host binding defaults to `127.0.0.1`, so remote devices cannot reach it until `MQTT_BIND_ADDRESS` is changed.
