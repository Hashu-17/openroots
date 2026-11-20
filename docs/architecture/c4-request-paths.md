# Dynamic View: Request Paths

This is a proposed runtime request-flow view, not a C4 level 1, 2, or 3 diagram.
It shows how a future React dashboard could use the Spring Boot business API for
project and device-management operations while using a separate Go telemetry API
for bounded historical telemetry queries and live-reading updates.

The Spring Boot API and React frontend exist on the current branch, and every
request carries the signed-in user's access token. The Go telemetry API is
implemented on this branch: its live-update path (dashed) is an authenticated
WebSocket that the frontend uses, and its bounded history endpoint is available
but the frontend still reads history from the Spring REST API. Go checks the same
access token and project membership as Spring. The diagram also includes account
settings as a future frontend request category.

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