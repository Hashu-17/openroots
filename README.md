# OpenRoots

The platform can currently manage projects, registered devices, and telemetry. The current backend exposes a versioned REST API, with an MQTT path for device telemetry.

## Features
- Create and manage operational projects.
- Register physical devices under a project.
- Configure multiple sensor channels for each device.
- Ingest device telemetry through MQTT.
- Persist telemetry in PostgreSQL/TimescaleDB.
- View projects, devices, sensors, and telemetry from the React web interface.
- Access project, device, and telemetry resources through a versioned REST API.
- Validate device registrations and return structured API error responses.

## System Requirements
- Java 21 or newer.
- Maven 3.9 or newer.
- Node.js and npm for frontend development.
- Docker Engine or Docker Desktop.
- Docker Compose v2.
- PostgreSQL/TimescaleDB for application data.
- Eclipse Mosquitto for MQTT communication.
- Ports `5173`, `8080`, `5432`, and `1883` available locally.

## Deploying Locally
1. Clone the repository and open a terminal in the project root.
2. Copy `.env.example` to `.env`.
3. Replace the placeholder database and MQTT passwords in `.env`.
4. Start the complete local stack:

   ```bash
   docker compose up --build
   ```

5. Open the frontend at `http://localhost:5173`.
6. The backend is available at `http://localhost:8080`.
7. Check backend health at `http://localhost:8080/actuator/health`.
8. Stop the services with:

   ```bash
   docker compose down
   ```

The Compose stack starts the database, MQTT broker, Spring Boot backend, and frontend. Database and broker data are stored in Docker volumes. To remove those volumes and reset local data, run `docker compose down -v`.

## Documentation

- The in depth documentation - [Complete Documentation](docs/README.md)
