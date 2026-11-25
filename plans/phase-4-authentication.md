# Phase 4 Implementation Plan

Phase 4 replaced the development-only `permitAll()` configuration with JWT
authentication and project-level authorization. The `dev` profile still seeds
`dev@openroots.local` with an `OWNER` membership in "OpenRoots Development".

## Goal

Secure human users, projects, devices, and telemetry without changing the public behavior of unrelated modules.

## Tasks

- [x] 1. Add `User` persistence entity and repository matching the Flyway schema.
- [x] 2. Add registration and login request/response DTOs (contract-first in `docs/openapi.yaml`).
- [x] 3. Add password hashing with a Spring `PasswordEncoder` (BCrypt).
- [x] 4. Add JWT dependency and token provider.
- [x] 5. Add JWT authentication filter.
- [x] 6. Add authentication controller and service.
- [x] 7. Replace `permitAll()` with explicit public and protected routes (401/403 JSON handlers).
- [x] 8. Add project membership entities and repositories, plus project creation and member management.
- [x] 9. Add authorization checks for project-owned devices and telemetry.
- [x] 10. Add hashed device credentials with revocation and rotation (migration `V4__device_credentials.sql`).
- [x] 11. Add tests for registration, login, authentication, and authorization failures.

## Completion criteria

- [x] Invalid credentials return `401` (`SecurityWebTest`, `AuthServiceTest`).
- [x] Missing credentials cannot access protected endpoints (`SecurityWebTest`).
- [x] Authenticated users cannot access another project's devices (`DeviceServiceTest`, `TelemetryServiceAuthorizationTest`).
- [x] Viewers cannot perform write operations (`ProjectAuthorizationServiceTest`, `SecurityWebTest`).
- [x] Device secrets are never returned after creation (`DeviceCredentialServiceTest`, `SecurityWebTest`).
- [x] Tests cover both successful access and denied access.
- [ ] End-to-end run against a real database (register, login, create project, register device, cross-user denial). Unit and MockMvc tests pass; this still needs a manual run with the Compose stack.

## Deferred

- Devices authenticating with their credentials over HTTPS or MQTT (credentials can be issued and revoked, but nothing verifies them yet).
- Refresh tokens, token revocation, password reset.
- Authentication for the WebSocket push.
- Frontend login, project creation and member management screens.

See `docs/security/authentication.md` for the role matrix, status codes and configuration.
