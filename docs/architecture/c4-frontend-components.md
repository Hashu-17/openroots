# Level 3: Frontend components 

This view describes the React frontend currently implemented on `spring-mqtt-ingestion`. It focuses on the main UI and API boundaries rather than individual presentational elements.

```mermaid
flowchart LR
    operator[Operator]

    subgraph frontend[OpenRoots Control frontend · React application]
        app[App<br/>application state and orchestration]

        subgraph project_views[Project views]
            projects[ProjectsIndex<br/>project list]
            workspace[ProjectWorkspace<br/>project navigation and tabs]
        end

        subgraph device_feature[Device feature]
            devices[DevicesView<br/>inventory and telemetry selection]
            registration[DeviceRegistrationForm<br/>registration form state and validation]
        end

        telemetry[TelemetryPanel<br/>telemetry presentation]
        client[API client<br/>HTTP requests and response envelope]

        app -->|renders and handles navigation| projects
        app -->|supplies project and device state| workspace
        workspace -->|renders device feature| devices
        devices -->|contains| registration
        workspace -->|renders selected device data| telemetry
        app -->|project, device list, and telemetry requests| client
        registration -->|device registration request| client
    end

    operator -->|uses| projects
    operator -->|uses| workspace
    client -->|REST /api/v1| backend[Spring Boot REST API]
```

## Responsibilities

- **App** owns the selected project, loaded project/device data, telemetry selection, and calls that coordinate views.
- **ProjectsIndex** presents projects and reports a user's project selection to `App`.
- **ProjectWorkspace** presents project navigation and tabs, and coordinates the device and telemetry views.
- **DevicesView** presents the inventory and telemetry selection controls. Device names use explicit buttons to open telemetry.
- **DeviceRegistrationForm** owns registration input, client-side validation, submission state, and user feedback.
- **API client** centralizes the JSON response-envelope handling and the frontend's `/api/v1` endpoints.
- **TelemetryPanel** presents telemetry records passed from the application state.

The device inventory continues to refresh every five seconds while its tab is open. This diagram describes frontend responsibilities; the authoritative HTTP contract remains [OpenAPI](../openapi.yaml).
