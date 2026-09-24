flowchart TB

%% =====================================================
%% CLIENTS
%% =====================================================

subgraph CLIENTS["Clients"]
    DEVICES["IoT Devices
(ESP32, STM32, Raspberry Pi)"]
    DASHBOARD["Web Dashboard"]
    MOBILE["Flutter Mobile App"]
end

%% =====================================================
%% TRANSPORT
%% =====================================================

subgraph TRANSPORT["Transport Layer"]
    REST["REST Controllers"]
    MQTT_IN["MQTT Listener"]
    MQTT_OUT["MQTT Publisher"]
    WS["WebSocket Gateway"]
end

BROKER[(Mosquitto MQTT Broker)]

%% =====================================================
%% APPLICATION
%% =====================================================

subgraph APPLICATION["Application Layer"]

    AUTH["Authentication"]

    DEVICE["Device Module"]

    TELEMETRY["Telemetry Module"]

    COMMAND["Command Module"]

    ALERT["Alert Module"]

    USER["User Module"]

end

%% =====================================================
%% PERSISTENCE
%% =====================================================

subgraph PERSISTENCE["Persistence Layer"]

    DB[(PostgreSQL)]

end

%% =====================================================
%% DEVICE COMMUNICATION
%% =====================================================

DEVICES -->|HTTPS| REST
DEVICES -->|MQTT| BROKER

BROKER --> MQTT_IN

%% =====================================================
%% USER COMMUNICATION
%% =====================================================

DASHBOARD -->|HTTPS| REST
MOBILE -->|HTTPS| REST

WS --> DASHBOARD
WS --> MOBILE

%% =====================================================
%% BACKEND FLOW
%% =====================================================

REST --> AUTH
MQTT_IN --> AUTH

AUTH --> DEVICE

DEVICE --> TELEMETRY
DEVICE --> COMMAND
DEVICE --> ALERT
DEVICE --> USER

%% =====================================================
%% DATABASE
%% =====================================================

DEVICE --> DB
USER --> DB
TELEMETRY --> DB
COMMAND --> DB
ALERT --> DB

%% =====================================================
%% REAL-TIME
%% =====================================================

TELEMETRY --> WS
ALERT --> WS

%% =====================================================
%% DEVICE COMMANDS
%% =====================================================

COMMAND --> MQTT_OUT
MQTT_OUT --> BROKER
BROKER -->|MQTT| DEVICES