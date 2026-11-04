# OpenRoots Frontend: In Depth guide

This guide explains the frontend as if you are learning the codebase from the beginning. It covers the files, functions, React concepts, asynchronous behavior, API calls, TypeScript types, CSS, and build configuration.

The frontend is a React 19 single page application written in TypeScript and bundled with Vite. It  an operations console for:

1. Listing projects.
2. Opening one project.
3. Listing and registering devices in that project.
4. Viewing device telemetry.
5. Deleting devices.

## Pre requisites

- components (parent vs child) and JSX basics
- states and props
- conditional rendering 
- hooks
- async vs sync functions 

Component level architecture was used for the react app separating view components from app.tsx under views/ , a separate api client under api/ , type definitions under types/

> DevicesView is in features/ and not views/ because it represents a reusable component that can be used in multiple views, rather than being a top-level view itself.

## 1. Start here: how the browser reaches the application

The browser starts from [`index.html`](../index.html). It contains:

```html
<div id="root"></div>
<script type="module" src="/src/main.tsx"></script>
```

`root` is an empty HTML element. React takes ownership of it. The module script loads [`src/main.tsx`](../src/main.tsx).

### `main.tsx`

```tsx
import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import './index.css'
import App from './App.tsx'
```

- `StrictMode` is a React development helper. It can deliberately re-run certain lifecycle behavior in development to expose unsafe patterns. It does not create visible UI.
- `createRoot` is React 18/19's API for mounting a React application.
- `./index.css` loads global CSS once.
- `App` is the top-level React component.

```tsx
createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <App />
  </StrictMode>,
)
```

- `document.getElementById('root')` finds the HTML mount point.
- `!` is the TypeScript non-null assertion. It tells TypeScript that the element is expected to exist. If the HTML did not contain `root`, the browser would still fail at runtime.
- `render` places `<App />` inside the root element.

## 2. Project configuration and build tools

### `package.json`

The project is an ES module package because `"type": "module"` is set.

Important scripts:

- `npm run dev`: starts Vite's development server.
- `npm run build`: runs `tsc -b` and then creates a production bundle with Vite.
- `npm run lint`: runs Oxlint.
- `npm run preview`: serves the already-built production bundle locally.

Important runtime dependencies:

- `react`, `react-dom`: UI rendering.
- `@radix-ui/react-select`: accessible project selector.
- `@radix-ui/react-tooltip`: accessible tooltips.
- `lucide-react`: icons.
- `tailwindcss`, `@tailwindcss/vite`: utility CSS.

Important development dependencies:

- `typescript`: type checking.
- `vite`: dev server and bundler.
- `@vitejs/plugin-react`: React support for Vite.
- `oxlint`: linting.

### `vite.config.ts`

```ts
export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    proxy: {
      '/api': 'http://localhost:8080',
    },
  },
})
```

- `defineConfig` gives Vite typed configuration.
- `react()` enables JSX/React transformation.
- `tailwindcss()` enables Tailwind v4 processing.
- The development server proxies every request beginning with `/api` to the backend at port `8080`.
- The frontend therefore calls `/api/v1/...`, not `http://localhost:8080/api/v1/...`. The proxy supplies the host during development.

### TypeScript configuration

[`tsconfig.app.json`](../tsconfig.app.json) applies to `src`:

- `target: es2023`: generated assumptions can use modern JavaScript.
- `lib: ["ES2023", "DOM"]`: includes modern JavaScript and browser APIs such as `fetch`, `window`, and `document`.
- `moduleResolution: bundler`: resolves imports the way a bundler does.
- `jsx: react-jsx`: uses the modern JSX transform; components do not need `import React`.
- `noEmit: true`: TypeScript checks but does not produce JavaScript. Vite does the bundling.
- `noUnusedLocals` and `noUnusedParameters`: unused code is a type-checking error.
- `verbatimModuleSyntax`: type-only imports must be marked with `import type`.

[`tsconfig.node.json`](../tsconfig.node.json) applies to `vite.config.ts` and provides Node types.

## 3. Global CSS: `src/index.css`

[`index.css`](../src/index.css) is imported by `main.tsx`, so it applies to the whole application.

### Tailwind import

```css
@import "tailwindcss";
```

This loads Tailwind v4's generated base styles and utility classes. Most JSX styling in the project is written as utility classes such as `flex`, `border`, `px-5`, and `text-sm`.

### CSS variables

The `:root` block defines reusable design tokens:

- `--paper`: page background.
- `--white`: panel background.
- `--ink`: primary text.
- `--muted`: secondary text.
- `--line`: borders.
- `--mist`: muted panel elements.
- `--coral`: destructive/error color.
- `--teal`: success/active color.
- `--gold`: accent color.
- `--sans`: default UI font stack.
- `--display`: display/heading font stack.
- `--mono`: monospace font stack.

The application mostly uses equivalent literal Tailwind colors such as `bg-[#101416]`, but the variables establish the intended design system and are used by the root document defaults.

### Root document defaults

```css
font: 16px/1.45 var(--sans);
color: var(--ink);
background: var(--paper);
font-synthesis: none;
text-rendering: optimizeLegibility;
-webkit-font-smoothing: antialiased;
```

- Sets the base font size and line height.
- Sets default text and page colors.
- Prevents the browser from synthesizing missing font styles.
- Requests improved text rendering and antialiasing.

### Global selectors

```css
* { box-sizing: border-box; }
```

Every element includes padding and borders inside its declared width and height. This makes layout sizing more predictable.

```css
body { margin: 0; min-width: 320px; background: var(--paper); }
```

Removes the browser's default body margin, prevents the layout from becoming narrower than 320 pixels, and sets the page background.

```css
button, input, select { font: inherit; }
```

Form controls inherit the application's font instead of using browser-specific defaults.

```css
::selection { background: rgba(65, 199, 161, .28); color: #f1f3ed; }
```

Defines the colors shown when the user selects text.

## 4. Shared TypeScript data model

[`src/types/api.ts`](../src/types/api.ts) contains types only. It does not make network requests.

### `Project`

```ts
type Project = {
  id: string
  name: string
  createdAt: string
}
```

Represents a project returned by the backend. `createdAt` is an ISO date-time string, not a JavaScript `Date`.

### `Sensor`

```ts
type Sensor = {
  id: string
  channelName: string
  unit: string | null
}
```

Represents a saved measurement channel. `unit` can be `null` when the channel has no unit.

### `Device`

A device contains:

- its generated ID
- its owning project ID
- name and type
- status
- registration timestamp
- saved sensors

### `SensorDraft`

```ts
type SensorDraft = {
  channelName: string
  unit: string
}
```

This is deliberately different from `Sensor`. A draft has no backend ID and uses an empty string while the user is editing. The backend request later converts an empty unit to `null`.

### `RegisterDeviceRequest`

This is the JSON body sent when registering a device. Each sensor has a channel name and nullable unit.

### `TelemetryRecord`

A telemetry record has:

- `id`
- `deviceId`
- `capturedAt`
- `payload`

`payload` is a dictionary. `Object.entries(payload)` converts that dictionary into channel/value pairs for display.

## 5. API layer: `src/api/client.ts`

The API layer keeps URLs and response-envelope handling out of the components.

### `ApiResponse<T>`

```ts
type ApiResponse<T> = {
  success: boolean
  message: string
  data: T
}
```

`T` is a generic. For example:

- `ApiResponse<Project[]>` means `data` is a project array.
- `ApiResponse<Device>` means `data` is one device.

### `request<T>()`

```ts
async function request<T>(url: string, options?: RequestInit): Promise<T>
```

This is the shared asynchronous HTTP function.

1. `async` means the function always returns a `Promise`.
2. `url` is the relative backend URL.
3. `options` can contain a method and request body.
4. `fetch` starts the browser network request.
5. `await fetch(...)` pauses this function until the response arrives.
6. The `Content-Type` header tells the backend that request bodies are JSON.
7. `response.text()` reads the raw response body as text.
8. The code reads text before parsing so empty responses and invalid JSON can be handled explicitly.
9. `JSON.parse` converts the response text into an object.
10. Invalid JSON throws a new descriptive error.
11. `!response.ok` catches HTTP status failures.
12. `!body?.success` catches backend responses that are HTTP-successful but application-unsuccessful.
13. If `data` is an array, it is treated as validation details and appended to the error message.
14. Successful requests return `body.data`, so components receive useful data rather than the envelope.

The `catch` blocks in components receive these thrown errors and convert them into visible UI messages.

### Named API functions

- `listProjects`: `GET /api/v1/projects`.
- `listProjectDevices`: `GET /api/v1/projects/{projectId}/devices`.
- `registerDevice`: `POST /api/v1/projects/{projectId}/devices`.
- `deleteDevice`: `DELETE /api/v1/projects/{projectId}/devices/{deviceId}`.
- `listTelemetry`: `GET /api/v1/telemetry?deviceId=...`.

`encodeURIComponent` protects the telemetry query parameter when constructing the URL.

## 6. The top-level component: `App.tsx`

[`App.tsx`](../src/App.tsx) is the application's state owner and coordinator.

### `View`

```ts
type View = 'projects' | 'project'
```

This is a TypeScript union. `view` cannot accidentally become an arbitrary string.

### State variables

- `projects`: all projects loaded from the backend.
- `devices`: devices belonging to the selected project.
- `selectedProjectId`: the currently active project ID.
- `view`: whether the project index or workspace is visible.
- `projectsLoading`: initial project request status.
- `devicesLoading`: initial device request status.
- `projectsError`: project-loading error message.
- `devicesError`: device-loading or delete error message.
- `devicesNotice`: successful deletion message.
- `telemetryDevice`: selected device whose telemetry is open.
- `telemetry`: fetched records for that device.
- `telemetryLoading`: telemetry request status.
- `telemetryError`: telemetry error message.
- `loadedDevicesProjectId`: a `useRef` value used to distinguish first device load from refreshes.

### `selectedProject`

```ts
const selectedProject = projects.find(
  (project) => project.id === selectedProjectId,
)
```

`find` returns the matching project or `undefined`. This is why the workspace accepts `project?: Project` and displays a fallback name while data is unavailable.

### Initial project-loading `useEffect`

This effect runs once because its dependency array is empty.

```ts
let active = true
```

The flag prevents a late network response from updating state after unmount. The cleanup function sets it to `false`.

`loadProjects`:

1. Awaits `api.listProjects()`.
2. Stops if the component was unmounted.
3. Sorts projects newest-first using `Date.parse`.
4. Stores the projects.
5. Selects the first project only if no project is already selected.
6. Catches and displays an error.
7. Clears the loading state in `finally`, whether the request succeeded or failed.

`void loadProjects()` intentionally starts the async function without making the effect itself async. React effects should return either nothing or a cleanup function, not a Promise.

### `reloadDevices`

This is wrapped in `useCallback` so its function identity remains stable until `selectedProjectId` changes.

Behavior:

1. If no project is selected, it clears devices and returns.
2. It checks whether this project has loaded before.
3. Initial loading shows the spinner.
4. Refreshes clear the previous error but do not necessarily show the initial spinner.
5. It calls `api.listProjectDevices`.
6. It stores the returned devices.
7. Errors become visible messages.
8. On the first load, it records the project ID and stops the spinner.

### Device-loading `useEffect`

Devices load when all of these are true:

- the current view is `project`
- a project ID exists
- `reloadDevices` is available

This means opening a project or switching projects triggers a load.

### `openProject`

This function:

1. Changes the selected project ID.
2. Switches to the workspace.
3. Clears old device errors.
4. Clears old notices.

It is passed to both project cards and the project selector.

### `showTelemetry`

This async function:

1. Immediately records which device is selected.
2. Clears old telemetry and errors.
3. Shows the loading state.
4. Awaits `api.listTelemetry`.
5. Stores the returned records.
6. Displays an error if the request fails.
7. Always clears the loading state.

### `deleteDevice`

This async function:

1. Finds the full device object from the current list.
2. Shows a browser confirmation dialog.
3. Stops if the device no longer exists or the user cancels.
4. Clears old feedback.
5. Awaits the delete API call.
6. Reloads the inventory.
7. Shows a success notice containing the deleted device name.
8. Shows an error if deletion fails.

### Render tree

`App` renders:

1. A Radix tooltip provider.
2. A page wrapper with dark background and light text.
3. A header containing the OpenRoots brand button.
4. A status indicator saying “Development workspace”.
5. Either `ProjectsIndex` or `ProjectWorkspace`.
6. A footer describing the API and persistence state.

The header brand button returns to the project index but does not clear the selected project or loaded data.

## 7. Project index: `ProjectsIndex.tsx`

This component is presentational. It receives data and callbacks through props.

### `formatDate`

Converts an ISO date string into a localized date such as `Nov 10, 2026`.

### Render states

The component renders exactly one main content state:

1. Error banner, if `error` is nonempty.
2. Loading spinner, if `loading` is true.
3. Empty state, if loading is finished and `projects.length === 0`.
4. Project grid, otherwise.

Each project card is a button. Clicking it calls `onOpenProject(project.id)`.

The New project button is intentionally disabled. Radix Tooltip explains that project creation belongs to a future backend slice.

## 8. Project workspace: `ProjectWorkspace.tsx`

This component creates the selected-project shell.

### Props

The props include project data, device data, telemetry data, and callbacks. The important architecture rule is:

- `App` owns shared data and network actions.
- `ProjectWorkspace` owns screen-level tab state.
- child components receive callbacks instead of directly owning shared application data.

### `ProjectTab`

The union allows only:

- `overview`
- `devices`
- `members`
- `settings`

### `Unavailable`

This small reusable component displays a disabled-feature message with an icon.

### Local `tab` state

The workspace starts on `overview`.

```ts
useEffect(() => {
  setTab('overview')
}, [projectId])
```

When the user changes projects, the active tab resets. This prevents a user from switching projects while remaining on a stale Devices view.

### Five-second refresh effect

When `tab === 'devices'` and `projectId` exists:

1. `setInterval` schedules `onReloadDevices()` every five seconds.
2. `void` intentionally ignores the returned Promise.
3. The cleanup function clears the interval.
4. Changing projects, tabs, or the callback identity recreates the effect safely.

### Project selector

Radix Select provides the accessible dropdown. Selecting another project calls `onProjectChange`, which points to `App.openProject`.

### Tabs

Overview and Devices are enabled. Members and Settings are disabled with a “Soon” label.

### Overview

The overview calculates:

- device count: `devices.length`
- sensor count: `devices.reduce((total, device) => total + device.sensors.length, 0)`
- members: currently unavailable

## 9. Device inventory: `DevicesView.tsx`

This component displays the saved devices and embeds the registration form.

### Render states

The inventory shows:

1. Error feedback.
2. Success notice.
3. Loading spinner.
4. Empty inventory message.
5. Device articles.

Each device article displays:

- device name
- status badge
- device type
- UUID
- sensor channel badges
- registration date
- delete button

Clicking the device name or the telemetry-selection button calls `onShowTelemetry`.

The delete button calls `onDelete(device.id)` and has an accessible `aria-label`.

The `select-all` class on the UUID makes it easy to copy the device identifier.

## 10. Registration form: `DeviceRegistrationForm.tsx`

This component owns temporary form state because the draft only matters while the form is open.

### Starter sensors

The form begins with:

- temperature / C
- rain / mm
- uv / index

The form copies this array after submission so future state changes do not mutate the starter data.

### `updateSensor`

Receives:

- array index
- field name
- new string value

It maps over the sensors and returns a new object only for the selected row. This is immutable React state updating.

### `submit`

This is the main form async function.

1. `event.preventDefault()` stops a full browser navigation.
2. Existing errors and notices are cleared.
3. Sensor names are trimmed and blank names are removed for preliminary checks.
4. Required device/type/sensor values are checked.
5. Every original sensor row is checked for a blank channel name.
6. A `Set` checks duplicate channel names.
7. `submitting` disables the button and changes its label.
8. The API request is sent with trimmed values.
9. Empty units become `null`.
10. `onRegistered()` asks the parent to reload devices.
11. On success, the form resets to its starter values.
12. On failure, the thrown error becomes visible.
13. `finally` always re-enables the form.

The inputs are controlled inputs: their displayed values come from React state, and every `onChange` updates that state.

Removing a sensor filters it from the array. The final sensor cannot be removed because the button is disabled when only one remains.

## 11. Telemetry presentation: `TelemetryPanel.tsx`

This is a presentation-only component.

### `formatTimestamp`

Formats a timestamp with month, day, hour, minute, and second.

### Render states

The panel renders:

1. An error banner, if present.
2. A loading spinner while fetching.
3. A no-data message when there are no records.
4. A record list otherwise.

For each record:

- the capture time is rendered in a `<time>` element
- `dateTime` preserves the machine-readable timestamp
- `Object.entries(record.payload)` creates one visual badge per channel
- values are converted to strings for display

The close button calls the callback owned by `App`, which clears `telemetryDevice`.

## 12. Async and React concepts used everywhere

### `async` and `await`

An `async` function returns a Promise. `await` pauses that function until a Promise resolves or rejects. It does not freeze the browser UI.

### `try / catch / finally`

- `try`: normal request path.
- `catch`: converts failures into user-facing errors.
- `finally`: cleanup that must happen on both success and failure.

### `useState`

Stores values that affect rendering. Calling a setter schedules a re-render.

### `useEffect`

Runs side effects such as network calls, timers, and cleanup.

### `useCallback`

Preserves a callback identity between renders until its dependencies change. This matters because effects depend on `reloadDevices`.

### `useRef`

Stores a mutable value without causing a render. `loadedDevicesProjectId` tracks loading history.

### Props and callbacks

Parents pass data down and callbacks down. Children notify parents by calling those callbacks. This creates one-directional data flow.

## 13. API contract relationship

The frontend contract is documented in [`docs/openapi.yaml`](../../docs/openapi.yaml). It confirms:

- project, device, and telemetry response shapes
- `{ success, message, data, timestamp }` response envelopes
- validation error arrays
- device registration requirements
- nullable sensor units
- UUID path/query parameters

The frontend intentionally implements only a subset of the available backend contract. For example, the backend exposes project and device detail endpoints, but the frontend currently uses list operations rather than separate detail requests.

## 14. Complete user journey

```text
index.html
  -> main.tsx
  -> App
  -> GET /api/v1/projects
  -> ProjectsIndex
  -> click project
  -> ProjectWorkspace
  -> GET /api/v1/projects/{id}/devices
  -> DevicesView
  -> DeviceRegistrationForm
  -> POST /api/v1/projects/{id}/devices
  -> refresh device list
  -> click device
  -> GET /api/v1/telemetry?deviceId={id}
  -> TelemetryPanel
```

## 15. Current limitations and likely next work

- No URL routing or browser back-button navigation.
- No authentication or authorization.
- No project creation UI.
- No member management.
- No project settings.
- No frontend test suite is currently described in the project guide.
- Telemetry is displayed as raw key/value data rather than charts or summaries.
- Five-second polling continues only while the Devices tab is open.
- The frontend assumes the backend response envelope and field names exactly match the TypeScript types.

## Files to read in order

1. [`README.md`](../README.md)
2. [`src/main.tsx`](../src/main.tsx)
3. [`src/App.tsx`](../src/App.tsx)
4. [`src/api/client.ts`](../src/api/client.ts)
5. [`src/types/api.ts`](../src/types/api.ts)
6. [`src/views/ProjectsIndex.tsx`](../src/views/ProjectsIndex.tsx)
7. [`src/views/ProjectWorkspace.tsx`](../src/views/ProjectWorkspace.tsx)
8. [`src/features/devices/DevicesView.tsx`](../src/features/devices/DevicesView.tsx)
9. [`src/features/devices/DeviceRegistrationForm.tsx`](../src/features/devices/DeviceRegistrationForm.tsx)
10. [`src/TelemetryPanel.tsx`](../src/TelemetryPanel.tsx)
11. [`src/index.css`](../src/index.css)
12. [`vite.config.ts`](../vite.config.ts)
13. [`../../docs/openapi.yaml`](../../docs/openapi.yaml)
