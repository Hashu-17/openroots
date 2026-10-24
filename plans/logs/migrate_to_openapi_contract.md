# OpenAPI contract migration log

This log records AI-authored changes made during the move to contract-first OpenAPI. Each implementation checkpoint is recorded before its source changes. The user reviews and commits each checkpoint before work continues.

## Checkpoint 1 — describe the current backend contract

**Status:** Complete  
**Date:** 2026-11-08

**Change made:** Updated `docs/openapi.yaml` to describe the routes and behavior currently implemented by `ProjectController`, `DeviceController`, and `TelemetryController`. Added the `ApiResponse` success/error envelope schemas, removed the unimplemented authentication endpoints and bearer-auth requirements, corrected device registration as a `POST`, and added the implemented device deletion operation. Documented the existing request/response DTO fields and observed 400/404/201/200 statuses. No Java implementation files changed in this checkpoint.

**Files to change:**

- `docs/openapi.yaml`
- `plans/logs/migrate_to_openapi_contract.md` (this log)

**Review:** Compared the listed routes, DTO fields, response envelope, exception handlers, and security behavior against the current Java controllers and configuration. No test suite was run in this checkpoint.

## Checkpoint 2 — configure Spring API generation

**Status:** Complete  
**Date:** 2026-11-08

**Change made:** Added OpenAPI Generator Maven plugin version `7.26.0`, configured to read `docs/openapi.yaml` and generate Spring API interfaces and models under `target/generated-sources/openapi` in packages separate from handwritten code. Disabled generated test, documentation, and supporting files. Updated `.gitignore` so this migration log can be committed while keeping other `plans/` content ignored. No Java source files changed in this checkpoint.

**Files to change:**

- `pom.xml`
- `.gitignore`
- `plans/logs/migrate_to_openapi_contract.md` (this log)

**Review:** Maven XML parsed successfully, the log is no longer ignored by Git, and `git diff --check` reported no whitespace errors. Code generation and tests were not run; generation is checkpoint 3.
