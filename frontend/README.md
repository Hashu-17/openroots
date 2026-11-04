# OpenRoots Frontend

Web control surface for OpenRoots — an open-source IoT environmental telemetry and hardware registry platform. This single-page application (SPA) enables operators to manage workspaces and projects, provision and monitor physical devices and sensor channels, and inspect incoming telemetry streams.

---

## Tech Stack & Tooling

| Category | Technology | Version | Purpose |
| :--- | :--- | :--- | :--- |
| **Runtime & Framework** | [React](https://react.dev/) | `^19.2.0` | UI component tree with React 19 concurrency and hooks |
| **Language** | [TypeScript](https://www.typescriptlang.org/) | `~6.0.0` | Strict type safety across components, models, and API responses |
| **Bundler & Dev Server** | [Vite](https://vite.dev/) | `^8.3.0` | Fast ESM development server with HMR and Rollup production builds |
| **Styling** | [Tailwind CSS](https://tailwindcss.com/) | `^4.3.0` | Utility-first CSS via `@tailwindcss/vite` with theme design tokens |
| **UI Primitives** | [Radix UI](https://www.radix-ui.com/) | `Select`, `Tooltip` | Accessible, unstyled UI components for keyboard and ARIA support |
| **Iconography** | [Lucide React](https://lucide.dev/) | `^1.49.0` | Crisp SVG system icons |
| **Linter** | [Oxlint](https://oxc.rs/docs/guide/usage/linter.html) | `^1.81.0` | High-performance Rust-based linter |
| **Production Server** | [Nginx](https://nginx.org/) | `1.27-alpine` | Reverse proxy and static file server inside Docker |

---

## Directory & File Structure

```text
frontend/
├── Dockerfile                  # Multi-stage production container build (Node.js -> Nginx)
├── nginx.conf                  # Nginx runtime configuration and /api reverse proxy
├── index.html                  # HTML template entry point
├── package.json                # Dependencies, project metadata, and npm scripts
├── tsconfig.json               # Root TypeScript configuration
├── tsconfig.app.json           # Application TypeScript compilation rules
├── tsconfig.node.json          # Node/build tool TypeScript compilation rules
├── vite.config.ts              # Vite plugin setup and development API proxy rules
├── public/                     # Static public assets (favicons, SVG icons)
└── src/
    ├── main.tsx                # Client application bootstrap and DOM root mounting
    ├── index.css               # Global Tailwind CSS directives and theme variables
    ├── App.tsx                 # Top-level workspace layout and view orchestrator
    ├── DevicesView.tsx         # Hardware inventory list and device provisioning form
    ├── TelemetryPanel.tsx      # Real-time and historical telemetry data viewer
    └── assets/                 # Embedded SVGs and brand assets
```

---

## Getting Started

### Prerequisites

* **Node.js**: `>= 22.0.0`
* **npm**: `>= 10.0.0`
* **Backend API**: Running instance of `iot-backend` (Spring Boot) on port `8080` (or configured via environment/proxy).

### Installation

Clone the repository and install dependencies from the `frontend/` directory:

```bash
npm install
```

---

## Development Scripts

| Command | Description |
| :--- | :--- |
| `npm run dev` | Starts the Vite development server at `http://localhost:5173` with HMR. |
| `npm run build` | Runs TypeScript type checking (`tsc -b`) and generates production bundles in `dist/`. |
| `npm run lint` | Runs `oxlint` to analyze code quality and enforce React rules. |
| `npm run preview` | Serves the local production build from `dist/` for sanity checking. |

---

## API Integration & Proxy

The frontend communicates with the backend via REST endpoints under the `/api/v1/` prefix.

### Development Proxy
In development, [`vite.config.ts`](vite.config.ts) forwards `/api` requests to the local backend:
```ts
server: {
  proxy: {
    '/api': 'http://localhost:8080',
  },
}
```

### Production Proxy
In Docker deployments, [`nginx.conf`](nginx.conf) proxies requests directed at `/api/` to the backend container service:
```nginx
location /api/ {
    proxy_pass http://backend:8080;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
}
```

### Response Envelope Standard
All backend API responses adhere to the standard envelope:
```json
{
  "success": true,
  "message": "Operation successful",
  "data": { ... }
}
```
Client HTTP calls handle this contract through typed request wrappers and throw readable errors if `success === false`.

---

## Architectural Guidelines

To maintain scalability and avoid accumulating monolithic components:

1. **Component Separation**: Keep view-specific logic, forms, and presentation components in distinct files rather than grouping them into `App.tsx`.
2. **State Management**: Lift shared state (such as active project or authenticated context) only as high as necessary. Local form state should reside inside form components.
3. **Type Safety**: Avoid using `any`. Define strong TypeScript interfaces or type aliases for domain entities (`Project`, `Device`, `Sensor`, `TelemetryRecord`).
4. **Design Tokens**: Rely on the CSS variables declared in `src/index.css` (`--paper`, `--line`, `--teal`, `--coral`, `--gold`, etc.) and standard Tailwind utility classes to preserve cohesive brand typography and coloring.
