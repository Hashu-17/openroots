# Dynamic View: Request Paths

This is a proposed runtime request-flow view, not a C4 level 1, 2, or 3 diagram.
It shows how a future React dashboard could use the Spring Boot business API for
project and device-management operations while using a separate Go telemetry API
for bounded historical telemetry queries and live-reading updates.

The Spring Boot API and React frontend exist on the current branch, and every
request carries the signed-in user's access token. The Go telemetry API and the
historical TimescaleDB query path are proposed and are not implemented in the
current Compose deployment. The dashed live-update path exists on this branch,
but Spring serves it over an authenticated WebSocket rather than Go. The diagram
also includes account settings as a future frontend request category.

Use the implemented C4 views in the [architecture index](README.md) for the
current system boundaries, containers, and components. Use this view when
discussing the proposed separation between business requests and telemetry
queries.

```mermaid
flowchart TB
    User[User in React dashboard]
    React[React dashboard]
    Spring[Spring Boot business API]
    Go[Go telemetry API]
    PG[(PostgreSQL)]
    TS[(TimescaleDB)]

    User --> React
    React -->|projects, device inventory, account settings| Spring
    Spring --> PG
    React -->|device ID and bounded time range| Go
    Go -->|historical query| TS
    TS -->|historical readings| Go
    Go --> React
    Go -.->|new committed readings| React
```