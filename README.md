# OpenRoots

Our platform can currently manage projects, registered devices, and telemetry ingestion and display. 

## Features
- Create and manage  projects.
- Register physical devices under a project.
- Configure multiple sensor channels for each device.
- View projects, devices, sensors, and telemetry from the web interface.

## System Requirements
- Java 21 or newer.
- Go (version in `ingestion/go.mod`) for ingestion-service development.
- Maven 3.9 or newer.
- Node.js and npm for frontend development.
- Docker Engine or Docker Desktop.
- Docker Compose v2.
- PostgreSQL/TimescaleDB for application data.
- Eclipse Mosquitto for MQTT communication.
- Ports `5173`, `8080`, `8081`, `5432`, and `1883` available locally.

## Deploying Locally
1. Clone the repository and open a terminal in the project root.
2. Copy `.env.example` to `.env`.
3. Replace the placeholder database, JWT, MQTT and ingestion registry secrets in `.env`.
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

The Compose stack starts the database, MQTT broker, Spring Boot backend, Go ingestion service, and frontend. On this branch MQTT telemetry is consumed by the Go service in [`ingestion/`](ingestion/README.md); everything else matches `spring-mqtt-ingestion`. Database and broker data are stored in Docker volumes. To remove those volumes and reset local data, run `docker compose down -v`.

## Documentation

- The in depth documentation - [Complete Documentation](docs/README.md)
- [Testing and CI](docs/testing/README.md) — where unit, integration and end-to-end tests live, and what runs on a pull request.
- [MQTT smoke and fleet load tests](docs/testing/mqtt-smoke-and-load.md) — run a GitHub Actions end-to-end smoke test or simulate a fleet publishing telemetry.
