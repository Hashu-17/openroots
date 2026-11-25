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

**Change made:** Added OpenAPI Generator Maven plugin version `7.26.0`, configured to read `docs/openapi.yaml` and generate Spring API interfaces and models under `target/generated-sources/openapi` in packages separate from handwritten code. Disabled generated test, documentation, and supporting files. Restored `.gitignore` to ignore all of `plans/`; this migration log is intentionally local and untracked. No Java source files changed in this checkpoint.

**Files to change:**

- `pom.xml`
- `.gitignore`
- `plans/logs/migrate_to_openapi_contract.md` (this log)

**Review:** Maven XML parsed successfully and `git diff --check` reported no whitespace errors. Code generation and tests were not run; generation is checkpoint 3. The migration log is intentionally excluded from commits per the user's preference.

## Checkpoint 3 — generate and compile API types

**Status:** Generation succeeded; full compile blocked by existing MQTT code  
**Date:** 2026-11-08

**Change made:** Ran `mvn clean compile`. OpenAPI Generator produced the `ProjectsApi`, `DevicesApi`, and `TelemetryApi` interfaces and their models under ignored `target/generated-sources/openapi/`. Maven reached Java compilation, where it failed on four existing MQTT integration errors in `MqttTelemetryConsumer.java` and `MqttConfig.java`; no generated source errors were reported. No Java source files were edited in this checkpoint, and the test suite was not run.

**Files changed:**

- Generated sources under `target/generated-sources/openapi/` (ignored build output)
- `plans/logs/migrate_to_openapi_contract.md` (this local log)

**Build errors:** `MqttTelemetryConsumer.java` cannot resolve `org.springframework.integration.handler.GenericHandler`, and `MqttConfig.java` calls a missing `MqttPahoMessageDrivenChannelAdapter.setRecoveryInterval(int)` method. These are outside the generated API sources.

## Checkpoint 4.1 — migrate ProjectController

**Status:** Controller migrated; full compile blocked by existing MQTT code  
**Date:** 2026-11-08

**Change made:** Made `ProjectController` implement generated `ProjectsApi`, removed duplicate route mappings, and returned generated OpenAPI response models while keeping project service/domain logic unchanged. Removed the handwritten project response DTO after confirming it had no other references. Ran `mvn compile`; generation succeeded and no errors were reported in the migrated controller or generated source, but compilation remains blocked by the same four MQTT errors recorded in checkpoint 3. No tests were run.

**Files to change:**

- `src/main/java/com/openroots/project/controller/ProjectController.java`
- `src/main/java/com/openroots/project/dto/ProjectResponse.java` (removed)
- `plans/logs/migrate_to_openapi_contract.md` (this local log)

**Build errors:** Same existing `GenericHandler` import and `setRecoveryInterval(int)` errors in MQTT sources; no new compile errors from this migration.

## Checkpoint 4.3 — migrate TelemetryController

**Status:** Controller migrated; full compile blocked by existing MQTT code  
**Date:** 2026-11-09

**Change made:** Made `TelemetryController` implement generated `TelemetryApi`, removed duplicate route mappings, and mapped generated API models to the existing service request/response types. Converted numeric payload values between generated `BigDecimal` maps and the service's `Map<String, Object>`. Retained `TelemetryIngestRequest` because the service and MQTT consumer use it; removed the handwritten `TelemetryResponse` after confirming it had no other references. Ran `mvn compile`; generation succeeded and no errors were reported in the migrated controller or generated source, but compilation remains blocked by the same four MQTT errors recorded in checkpoint 3. No tests were run.

**Files to change:**

- `src/main/java/com/openroots/telemetry/controller/TelemetryController.java`
- `src/main/java/com/openroots/telemetry/dto/TelemetryResponse.java` (removed)
- `plans/logs/migrate_to_openapi_contract.md` (this local log)

**Build errors:** Same existing `GenericHandler` import and `setRecoveryInterval(int)` errors in MQTT sources; no new compile errors from this migration.

## Checkpoint 4.2 — migrate DeviceController

**Status:** Controller migrated; full compile blocked by existing MQTT code  
**Date:** 2026-11-09

**Change made:** Made `DeviceController` implement generated `DevicesApi`; mapped generated request models into the existing service request records and domain entities into generated response models. Added OpenAPI patterns for non-blank device names, types, and sensor channel names so generated validation preserves the existing `@NotBlank` behavior. Removed handwritten device/sensor response DTOs after confirming there were no remaining references; retained request DTOs used by the service and tests. Ran `mvn compile`; generation succeeded and no errors were reported in the migrated controller or generated source, but compilation remains blocked by the same four MQTT errors recorded in checkpoint 3. No tests were run.

**Files to change:**

- `docs/openapi.yaml`
- `src/main/java/com/openroots/device/controller/DeviceController.java`
- `src/main/java/com/openroots/device/dto/DeviceResponse.java` (removed)
- `src/main/java/com/openroots/device/dto/SensorResponse.java` (removed)
- `plans/logs/migrate_to_openapi_contract.md` (this local log)

**Build errors:** Same existing `GenericHandler` import and `setRecoveryInterval(int)` errors in MQTT sources; no new compile errors from this migration.

## Build repair — MQTT compile compatibility

**Status:** Complete  
**Date:** 2026-11-09

**Planned change:** Correct the `GenericHandler` import and handler argument type in `MqttTelemetryConsumer` to match Spring Integration 6.3.5, obtaining the MQTT topic from `MessageHeaders`. Remove the unsupported `setRecoveryInterval(int)` call from `MqttConfig`; the existing Paho `setAutomaticReconnect(true)` setting remains in place. Run `mvn compile` only; do not run tests. Leave changes uncommitted as requested.

**Files to change:**

- `src/main/java/com/openroots/ingestion/mqtt/MqttTelemetryConsumer.java`
- `src/main/java/com/openroots/config/MqttConfig.java`
- `plans/logs/migrate_to_openapi_contract.md` (local log)

**Result:** `mvn compile` succeeded on 2026-11-09. OpenAPI Generator generated the API interfaces/models and javac compiled all 51 source files. Maven emitted one non-fatal warning about missing `javax.annotation.meta.When`; build completed successfully. No tests were run. All changes remain uncommitted.

# Repeat migration on `go-mqtt-ingestion`

## Checkpoint 1 — align the spec with this branch's current Spring API

**Status:** Complete  
**Date:** 2026-11-09

**Change made:** Updated `docs/architecture/openapi.yaml` to include the existing internal `GET /internal/v1/ingestion/device-registry` operation, its required `X-OpenRoots-Registry-Key` API-key header, its direct `DeviceRegistrySnapshot` response, and its unauthorized response. Preserved the existing project/device operations and their unauthenticated behavior, `ApiResponse` envelope, and status semantics. This branch has no Spring telemetry REST controller, so no telemetry endpoint was added. The user moved the contract from `src/main/resources/openapi.yaml` to `docs/architecture/openapi.yaml`; updated the Maven input path and added a narrow `.gitignore` exception so the moved contract remains trackable. No Java implementation files changed.

**Files to change:**

- `docs/architecture/openapi.yaml`
- `pom.xml` (input spec path follows the user-moved file)
- `.gitignore` (allow the spec to be tracked while keeping other docs ignored)
- `src/main/resources/openapi.yaml` (removed by the user's move)
- `plans/logs/migrate_to_openapi_contract.md` (this local log)

**Review:** Compared the route, API-key check, direct response DTO, public route access, and envelope behavior against the current Spring controllers, DTOs, `SecurityConfig`, and `GlobalExceptionHandler`. `git diff --check` passed. No code generation or tests were run in this checkpoint.

**Path update:** The user moved the contract to `docs/architecture/openapi.yaml` during this checkpoint. That location was covered by the current `/docs/` ignore rule, and Maven still pointed at the removed `src/main/resources/openapi.yaml`. This checkpoint therefore updates the relevant `.gitignore` exceptions and Maven `inputSpec` path. The earlier planned source path is superseded.

## Checkpoint 2 — configure deterministic Spring interface/model generation on `go-mqtt-ingestion`

**Status:** Complete  
**Date:** 2026-11-09

**Change made:** Updated the Maven OpenAPI Generator configuration in `pom.xml`: pinned generator 7.26.0 through a property; reads `docs/architecture/openapi.yaml`; writes to ignored `target/generated-sources/openapi`; registers generated sources for compilation; generates Spring API interfaces and models only; and disables generated tests, documentation, and supporting files. Configured Spring Boot 3/Jakarta-compatible interfaces, response entities, bean validation, API-interface request mappings, tag-based API grouping, and deterministic output. Preserved the existing `.gitignore` edits; they are outside this checkpoint.

**Files to change:**

- `pom.xml`
- `plans/logs/migrate_to_openapi_contract.md` (this local log)

**Review:** `pom.xml` parsed successfully as XML and `git diff --check` passed. Generation and tests were not run; those are checkpoint 3. The user's existing `.gitignore` change remains untouched.

## Checkpoint 3 — generate and compile on `go-mqtt-ingestion`

**Status:** Complete  
**Date:** 2026-11-09

**Check performed:** Ran `mvn clean compile`. OpenAPI Generator 7.26.0 accepted the moved contract and generated `ProjectsApi`, `DevicesApi`, and `IngestionRegistryApi`, plus the contract models under ignored `target/generated-sources/openapi`. Maven compiled all 55 Java source files successfully. It emitted a non-fatal warning that `javax.annotation.meta.When` was not found; the build completed successfully. No Java implementation files were edited and no tests were run.

**Expected generated files:** Project, device, and internal ingestion-registry API interfaces, plus their contract models.

**Files changed:** Generated artifacts under `target/` only (ignored); this local log records the command and outcome.

## Checkpoint 4.1 — migrate ProjectController on `go-mqtt-ingestion`

**Status:** Complete  
**Date:** 2026-11-09

**Change made:** Made `ProjectController` implement generated `ProjectsApi`, removed duplicate route annotations, and returned generated `ProjectListApiResponse`, `ProjectApiResponse`, and `ProjectResponse` models while preserving existing success messages and service behavior. Converted `Instant` timestamps to UTC `OffsetDateTime` to match generated OpenAPI date-time models. Removed the handwritten project response DTO after confirming it had no other references. Ran `mvn compile`; generation succeeded and all 54 Java source files compiled. The build emitted the non-fatal `javax.annotation.meta.When` warning. No tests were run.

**Files to change:**

- `src/main/java/com/openroots/project/controller/ProjectController.java`
- `src/main/java/com/openroots/project/dto/ProjectResponse.java` (remove if still unused)
- `plans/logs/migrate_to_openapi_contract.md` (this local log)

**Review:** `git diff --check` passed. No service or domain behavior changed.

## Checkpoint 4.2 — migrate DeviceController on `go-mqtt-ingestion`

**Status:** Complete  
**Date:** 2026-11-09

**Change made:** Made `DeviceController` implement generated `DevicesApi`, removed duplicate route annotations, and used generated request/response models at the HTTP boundary. Mapped generated device-create requests into the existing service DTOs; preserved provisioning, transaction, status, and response-envelope behavior through generated response models. Converted `Instant` response timestamps to UTC `OffsetDateTime`. Added OpenAPI `pattern: '\S'` constraints for device name, device type, and sensor channel name; confirmed the generated models contain corresponding `@Pattern` validation alongside size validation, preserving the previous `@NotBlank` behavior. Removed handwritten response-only device, sensor, and registration DTOs after verifying references; retained request DTOs used by the service/tests and `MqttDeviceCredentials` used by provisioning. Ran `mvn compile`; generation succeeded and all 51 Java source files compiled. The build emitted the non-fatal `javax.annotation.meta.When` warning. No tests were run.

**Files to change:**

- `docs/architecture/openapi.yaml`
- `src/main/java/com/openroots/device/controller/DeviceController.java`
- `src/main/java/com/openroots/device/dto/DeviceResponse.java` (remove)
- `src/main/java/com/openroots/device/dto/SensorResponse.java` (remove)
- `src/main/java/com/openroots/device/dto/DeviceRegistrationResponse.java` (remove)
- `plans/logs/migrate_to_openapi_contract.md` (this local log)

**Review:** `git diff --check` passed. No service or provisioning behavior changed.

## Checkpoint 4.3 — migrate IngestionRegistryController on `go-mqtt-ingestion`

**Status:** Complete  
**Date:** 2026-11-09

**Change made:** Made `IngestionRegistryController` implement generated `IngestionRegistryApi` and return generated `DeviceRegistrySnapshot`, `DeviceRegistryEntry`, and `DeviceRegistrySensor` models while preserving the constant-time API-key check and 401 behavior. Added an explicit optional `X-OpenRoots-Registry-Key` header parameter to the OpenAPI operation because the generated interface otherwise omitted a Java argument for its security scheme; optional allows a missing header to reach the existing 401 check rather than becoming a Spring 400. Regenerated sources, mapped repository entities to generated models, and removed the handwritten registry snapshot DTO after confirming it had no other references. `mvn generate-sources` and `mvn compile` succeeded; all 50 Java source files compiled. Maven emitted the non-fatal `javax.annotation.meta.When` warning. No tests were run.

**Files to change:**

- `docs/architecture/openapi.yaml`
- `src/main/java/com/openroots/device/controller/IngestionRegistryController.java`
- `src/main/java/com/openroots/device/dto/DeviceRegistrySnapshot.java` (remove)
- `plans/logs/migrate_to_openapi_contract.md` (this local log)

**Review:** `git diff --check` passed. No authorization logic or registry data behavior changed.

## Docker Compose verification — include OpenAPI contract in backend build context

**Status:** Complete  
**Date:** 2026-11-09

**Change and verification:** The user requested `docker compose up --build -d` and repair of every reported error. The build reproduced a backend failure because the Dockerfile copied only `pom.xml` and `src/`, while the Maven generator reads `docs/openapi.yaml`. Added `COPY docs/openapi.yaml ./docs/openapi.yaml` before Maven packaging. The backend image then built successfully as part of Compose.

**Files to change:**

- `Dockerfile`
- `plans/logs/migrate_to_openapi_contract.md` (this local log)

## Docker Compose runtime repair — preserve LF in Mosquitto startup script

**Status:** Complete  
**Date:** 2026-11-09

**Change and verification:** Compose built all images after the OpenAPI contract was copied into the backend build image, but startup failed because `mosquitto/config/start-broker.sh` was CRLF in the Windows worktree. The Alpine `/bin/sh` reported `set: line 2: illegal option -` and repeatedly restarted Mosquitto. Added `.gitattributes` to keep the script LF and normalized the current file. Recreated Compose; Mosquitto became healthy.

**Files to change:**

- `.gitattributes`
- `mosquitto/config/start-broker.sh` (line endings only)
- `plans/logs/migrate_to_openapi_contract.md` (this local log)

## Frontend structure and component diagram

**Status:** Planned; implementation in progress  
**Date:** 2026-11-10

**Planned change:** Refactor the React frontend without changing its routes, API behavior, visual design, or dependencies. Remove unused legacy components from `App.tsx`; define shared frontend domain/API types; move HTTP request handling and endpoint functions into a frontend API module; separate project index/workspace views; move device registration form state and validation into a dedicated component; replace name-based click delegation in `DevicesView` with explicit accessible controls. After the source boundaries are established, add a C4 frontend component view and link it from the architecture index. Preserve the existing 5-second device refresh behavior.

**Files planned:**

- `frontend/src/App.tsx`
- `frontend/src/DevicesView.tsx`
- `frontend/src/TelemetryPanel.tsx`
- new frontend API, shared type, and view/component files under `frontend/src/`
- `docs/architecture/c4-frontend-components.md`
- `docs/architecture/README.md`
- `plans/logs/migrate_to_openapi_contract.md` (this local log)

**Verification plan:** Run the frontend production build and `git diff --check`; do not run tests unless requested. Record actual results and any deviations below.

**Result:** Extracted shared frontend API/domain types, centralized endpoint and response handling, separated the project index and workspace views, and moved device registration state/validation into `DeviceRegistrationForm`. Removed the unused legacy device components, replaced text-based click delegation with explicit device-name buttons, and retained five-second polling only while the Devices tab is open. Added the frontend C3 diagram and linked it in the architecture index. `npm run build` succeeded; `git diff --check` passed. No tests were run. The changes remain uncommitted.

## Resolve frontend cherry-pick on `go-mqtt-ingestion`

**Status:** Planned; implementation in progress  
**Date:** 2026-11-10

**Planned change:** Resolve the in-progress frontend cherry-pick while retaining this branch's one-time MQTT credential response and display. Integrate the credential response into the new API client/device registration component, resolve moved/deleted frontend paths, and retain the incoming documentation updates. Do not continue/commit the cherry-pick; leave resolved changes for user review.

**Files to change:**

- `frontend/src/App.tsx`
- `frontend/src/api/client.ts`
- `frontend/src/types/api.ts`
- `frontend/src/features/devices/DeviceRegistrationForm.tsx`
- `frontend/src/features/devices/DevicesView.tsx`
- `frontend/src/views/ProjectWorkspace.tsx`
- `frontend/src/DevicesView.tsx` (remove as replaced by feature component)
- `frontend/src/TelemetryPanel.tsx`
- `README.md`
- `plans/logs/migrate_to_openapi_contract.md` (this local log)

**Verification plan:** Run the frontend production build and Docker Compose image build; do not run tests. Leave the cherry-pick uncontinued and changes uncommitted.

**Result:** The initial `docker compose build` failed because the cherry-pick had unresolved markers in `frontend/src/App.tsx`; Docker itself and the backend/Go images built. Resolved the source conflicts and adapted device registration to the Go branch's `DeviceRegistrationResponse`, preserving the one-time MQTT username/password/topic display and dismissal behavior. The old `frontend/src/DevicesView.tsx` is removed in favor of `features/devices/DevicesView.tsx`. `npm run build` and `docker compose build` both succeeded. No tests were run. Conflict resolutions are staged, but the cherry-pick was not continued or committed, per the user's preference.

**Follow-up plan:** Keep the Go branch's telemetry viewer at the ingestion service's `/viewer` on port 8081. Remove the Spring-only React `TelemetryPanel` and its `/api/v1/telemetry?deviceId=...` request from this frontend refactor; that endpoint is not served by the Go branch. Update the frontend source map and component diagram accordingly. Rebuild the frontend and Compose images; leave the cherry-pick uncontinued and uncommitted.

## Docker Compose startup ordering — wait for backend readiness

**Status:** Complete  
**Date:** 2026-11-09

**Change and verification:** The first Compose run showed frontend proxy requests returning 502 and ingestion retrying while Spring Boot was still starting. Added a backend healthcheck against `/actuator/health/readiness` and changed frontend and ingestion dependencies to wait for `service_healthy`. Recreated Compose: all five services are running, backend/database/Mosquitto report healthy, the frontend and proxied API both return HTTP 200, and the recreated frontend/ingestion logs show no startup 502 or registry retry. `git diff --check` passed. The existing database volume reports schema version 4 while this branch contains migrations through V3; Flyway warns but starts successfully and makes no migration. Other startup warnings are non-fatal and were not changed.

**Files to change:**

- `docker-compose.yml`
- `plans/logs/migrate_to_openapi_contract.md` (this local log)

## Docker Compose build repair — `spring-mqtt-ingestion`

**Status:** Complete  
**Date:** 2026-11-09

**Change and verification:** `docker compose up --build -d` initially failed in Maven generation because `pom.xml` reads `docs/openapi.yaml` while the Dockerfile copied only `pom.xml` and `src/`. Added the spec to the backend build stage. The backend and frontend images built successfully on rerun.

**Files to change:**

- `Dockerfile`
- `plans/logs/migrate_to_openapi_contract.md` (this local log)

## Docker Compose runtime repair — `spring-mqtt-ingestion` Mosquitto line endings

**Status:** Complete  
**Date:** 2026-11-09

**Change and verification:** After fixing the backend build context, Compose failed because Mosquitto's startup script contained CRLF in the Windows worktree; Alpine `/bin/sh` exited with `set: line 2: illegal option -`. Added a `.gitattributes` LF rule and normalized the script. Compose now has database and Mosquitto healthy, with backend and frontend running. Frontend root and backend actuator health both returned HTTP 200; Compose config validation and `git diff --check` passed. The existing database volume still produces a non-fatal Flyway warning because it is at schema version 4 while this branch contains migrations only through V3. Compose also reports the stopped `openroots-ingestion` container as an orphan from the other branch; it was left untouched. No application errors appeared in the inspected startup logs.

**Files to change:**

- `.gitattributes`
- `mosquitto/config/start-broker.sh` (line endings only)
- `plans/logs/migrate_to_openapi_contract.md` (this local log)

**Follow-up result:** Removed the Spring-only React telemetry panel and its `/api/v1/telemetry?deviceId=...` client call from the Go branch frontend; updated the frontend guide and C4 component diagram to identify Go's separate telemetry viewer at port 8081. The Go ingestion viewer/service was not modified. `npm run build` passed, and `docker compose build` successfully built frontend, backend, and ingestion images. Cherry-pick remains in progress and uncommitted.

## Beginner-friendly frontend code comments — `spring-mqtt-ingestion`

**Status:** In progress  
**Date:** 2026-11-10

**Planned change:** Add plain-language comments across the frontend TypeScript/TSX source so a less experienced frontend developer can follow the folder roles, shared data shapes, API request/response handling, React state and effects, form validation/submission, and view composition. Comments will favor explaining the flow and purpose, including some straightforward steps, since the maintainer plans to prune comments later. No runtime behavior or dependencies should change.

**Files to change:**

- `frontend/src/App.tsx`
- `frontend/src/api/client.ts`
- `frontend/src/types/api.ts`
- `frontend/src/features/devices/DeviceRegistrationForm.tsx`
- `frontend/src/features/devices/DevicesView.tsx`
- `frontend/src/views/ProjectWorkspace.tsx`
- `frontend/src/views/ProjectsIndex.tsx`
- `frontend/src/TelemetryPanel.tsx`
- `frontend/src/main.tsx`
- `plans/logs/migrate_to_openapi_contract.md` (this local log)

**Status:** Complete

**Result:** Added plain-language comments throughout the frontend source. Comments explain the app/view responsibilities, the API envelope, shared request/response types, form state and validation, device and telemetry display states, and React effects such as loading and polling. No runtime logic changed. `npm run build` passed; `git diff --check` passed. Changes remain uncommitted.
