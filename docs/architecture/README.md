# Architecture

These diagrams describe the current implementation on `go-mqtt-ingestion`, based on the Compose configuration and source code. Spring Boot owns users, projects, devices and the REST API; the Go ingestion service consumes MQTT telemetry and writes it to TimescaleDB. They do not include deferred command dispatch or mobile clients.

The root-level `architecture/` folder contains earlier exploratory diagrams, including proposed flows that are not implemented on this branch. Use the views below for the current architecture.





## C4 views ( Diagrams )
The views follow the [C4 Model](https://c4model.com/) levels and element categories, rendered with standard Mermaid flowcharts and a sequence diagrams. Mermaid's dedicated C4 renderer is experimental and offers limited layout control.

1. [System context (C1)](c4-system-context.md) — Start here.
2. [Containers (C2)](c4-containers.md) —  frontend, backend, Go ingestion service, MQTT broker, and DB.
3. [Backend components (C3)](c4-backend-components.md) — API adapters, application services, the ingestion device registry, and persistence.
4. [Frontend components (C3)](c4-frontend-components.md) — React views, device registration, application state, and the API client.
5. [ Ingress Flow (dynamic view)](ingress-flow.md) — proposed client, REST, MQTT, authentication, and application-module paths spanning C2 and C3 abstractions.
6. [Request Paths (dynamic view)](c4-request-paths.md) — proposed dashboard request paths involving the Spring business API and future Go telemetry API.
7. [Telemetry Ingestion (dynamic view)](c4-telemetry-ingestion.md) — the runtime path from an MQTT publish through the Go service to storage.
8. [Local Deployment](c4-local-deployment.md) — how the containers are deployed for local development.
9. [Domain data model](data-model.md) — relational entities, relationships, constraints, and implementation status.

>Update the relevant C4 views when a container, dependency, boundary, or important responsibility changes. Keep diagrams at the level useful to their audience; a code-level class diagram is optional and should not duplicate generated Javadoc or TypeDoc output.






## Architectural decisions

| Area | Direction | Scope |
|---|---|---|
| Business API | Spring Boot | Human accounts, projects, device registration, sensor configuration, membership, and business rules |
| Business data | PostgreSQL | Users, projects, devices, sensor definitions, permissions, subscriptions, and other transactional records |
| MQTT broker | Eclipse Mosquitto | Accept device connections and MQTT publications; enforce device credentials and topic access |
| Telemetry service | Go, at repository root `ingestion/` | Consume MQTT messages, validate device and payload, write telemetry, serve history, and publish committed live updates |
| Telemetry storage | TimescaleDB | Time-series readings and historical queries |
| Frontend | Existing React dashboard | Continue business workflows; add telemetry history and live views through the telemetry service |
| Stream processing | None initially | Do not add Kafka, Flink, or a separate transformation platform until a measured requirement justifies it |
| Deployment | Docker Compose initially | Run the broker, Go service, Spring service, frontend, and database services with explicit configuration and network boundaries |

PostgreSQL and TimescaleDB describe the data technologies and ownership boundaries. Whether the two logical stores run in one PostgreSQL server or separate instances is an operational decision still to be made; the Go service owns telemetry writes, and Spring owns business writes.

## Notes

- The REST contract is [OpenAPI](../openapi.yaml); generated interfaces and models are implemented by the Spring controllers.
- The MQTT broker is Eclipse Mosquitto with dynamic security. The Spring backend subscribes to `openroots/v1/devices/+/telemetry`.
- Telemetry is stored in TimescaleDB/PostgreSQL.
- `SecurityConfig` currently permits all HTTP requests. The JWT and project-membership rules in [authentication design](../security/authentication.md) are target design, not active enforcement.
- The [telemetry MQTT contract](../contracts/mqtt-telemetry.md) contains design requirements that are stricter than some current runtime checks. Treat that document's stated status and scope as part of the contract; do not assume every listed validation is already enforced.


