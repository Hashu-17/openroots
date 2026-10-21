# Phase 4 Implementation Plan

The first device-registration slice uses a development-only seeded owner and
currently permits requests so the persistence and API workflow can be tested.
Phase 4 must replace that seed identity with the authenticated JWT principal
before enabling project-level authorization.


## Goal

Secure human users, projects, devices, and telemetry without changing the public behavior of unrelated modules.

## Tasks

1. Add `User` persistence entity and repository matching the Flyway schema.
2. Add registration and login request/response DTOs.
3. Add password hashing with a Spring `PasswordEncoder`.
4. Add JWT dependency and token provider.
5. Add JWT authentication filter.
6. Add authentication controller and service.
7. Replace `permitAll()` with explicit public and protected routes.
8. Add project membership entities and repositories.
9. Add authorization checks for project-owned devices and telemetry.
10. Add hashed device credentials with revocation and rotation.
11. Add tests for registration, login, authentication, and authorization failures.

## Completion criteria

- Invalid credentials return `401`.
- Missing credentials cannot access protected endpoints.
- Authenticated users cannot access another project's devices.
- Viewers cannot perform write operations.
- Device secrets are never returned after creation.
- Tests cover both successful access and denied access.