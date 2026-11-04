# Architecture

These diagrams describe the current implementation on `spring-mqtt-ingestion`, based on the Compose configuration and source code. They do not include deferred command dispatch, a Go ingestion service, mobile clients, or implemented user authentication.

The root-level `architecture/` folder contains earlier exploratory diagrams, including proposed flows that are not implemented on this branch. Use the views below for the current architecture.

## C4 views

1. [System context (C1)](c4-system-context.md) — people, devices, and the OpenRoots system boundary.
2. [Containers (C2)](c4-containers.md) — the frontend, Spring backend, MQTT broker, and TimescaleDB.
3. [Backend components (C3)](c4-backend-components.md) — API adapters, application services, MQTT ingestion, and persistence.
4. [Telemetry ingestion (dynamic view)](c4-telemetry-ingestion.md) — the runtime path from an MQTT publish to storage.
5. [Local Compose deployment](c4-local-deployment.md) — how the containers are deployed for local development.

The views follow C4 levels and element categories, rendered with standard Mermaid flowcharts and a sequence diagram for predictable layout. Mermaid's dedicated C4 renderer is experimental and offers limited layout control.

## Current implementation notes

- The REST contract is [OpenAPI](../openapi.yaml); generated interfaces and models are implemented by the Spring controllers.
- The MQTT broker is Eclipse Mosquitto with dynamic security. The Spring backend subscribes to `openroots/v1/devices/+/telemetry`.
- Telemetry is stored in TimescaleDB/PostgreSQL.
- `SecurityConfig` currently permits all HTTP requests. The JWT and project-membership rules in [authentication.md](authentication.md) are target design, not active enforcement.
- The [telemetry MQTT contract](telemetry-contract.md) contains design requirements that are stricter than some current runtime checks. Treat that document's stated status and scope as part of the contract; do not assume every listed validation is already enforced.

## Keeping diagrams current

Update the relevant C4 views when a container, dependency, boundary, or important responsibility changes. Keep diagrams at the level useful to their audience; a code-level class diagram is optional and should not duplicate generated Javadoc or TypeDoc output.
