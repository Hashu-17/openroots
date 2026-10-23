# Frontend developer guide

The frontend is a React and TypeScript single-page application. Its current navigation is state-based: a user moves between the project index and a selected project's workspace. It does not use a routing library.

## Source map

```text
src/
├── App.tsx                         Application state and top-level coordination
├── main.tsx                        Browser entry point; mounts the React application
├── api/
│   └── client.ts                   HTTP helper and named REST endpoint functions
├── types/
│   └── api.ts                      Shared TypeScript shapes for projects, devices, and telemetry
├── views/
│   ├── ProjectsIndex.tsx           Project list screen
│   └── ProjectWorkspace.tsx         Selected-project header, tabs, and screen composition
├── features/
│   └── devices/
│       ├── DevicesView.tsx         Device inventory and telemetry selection UI
│       └── DeviceRegistrationForm.tsx Registration form state, validation, and submit feedback
├── TelemetryPanel.tsx              Telemetry record presentation
├── index.css                       Global styles
└── assets/                         Static images and starter assets
```

`types/api.ts` contains **TypeScript types**, not API request functions. For example, `Device` describes the device data passed between API and UI code. The HTTP calls and response-envelope handling live in `api/client.ts`.

The folder distinction is by responsibility: `views/` contains screen-level compositions; `features/devices/` groups UI and behavior specific to device management. `DevicesView` is a feature component used inside the project workspace, not a separate top-level screen.

## Data flow

- `App` loads projects and coordinates selected project, device, and telemetry data through `api/client.ts`.
- `ProjectWorkspace` renders project-level navigation and delegates the Devices tab to the device feature.
- `DeviceRegistrationForm` owns its draft fields and validation; after registration it asks `App` to refresh the device list.
- Device inventory refreshes every five seconds while the Devices tab is open.
- The backend contract is maintained in [`../docs/openapi.yaml`](../docs/openapi.yaml).

For the component relationships, see the [frontend C4 component view](../docs/architecture/c4-frontend-components.md).

## Local commands

Run these commands from this directory:

```sh
npm install
npm run dev
```

The development server uses the Vite configuration in this folder. The frontend expects the backend API at `/api/v1`; run the backend or the repository's Compose setup for live data. `npm run build` performs TypeScript project checking and creates the production bundle.
