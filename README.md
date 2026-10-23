# OpenRoots

OpenRoots is a platform for managing projects, registered devices, and telemetry. The current backend exposes a versioned REST API, with an MQTT path for device telemetry.

## Start here

- [Frontend developer guide](frontend/README.md) — source layout, responsibilities, and local commands.
- [Architecture index](docs/architecture/README.md) — current C4 views and implementation notes.
- [OpenAPI contract](docs/openapi.yaml) — the REST API contract used by the backend generator.
- [Product requirements](docs/product/requirements.md) — current product requirements.

## Main parts

- `frontend/` — React and TypeScript browser application.
- `src/main/java/` — Spring Boot REST API and application services.
- `ingestion/` — Go MQTT ingestion service, where present on the checked-out branch.
- `docs/` — API contract, product/architecture documentation, and diagrams.

Use the documentation for the checked-out branch as the source of truth; implementation details can differ between branches.
