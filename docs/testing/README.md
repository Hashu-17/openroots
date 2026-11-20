# Testing and CI

OpenRoots has one CI workflow that gates every pull request, plus manual
workflows for tests that need a running stack. Workflows are split by **when
and where they run**, not by test type.

## Where tests live and when they run

| Layer | What it checks | Where it lives | Run locally | Runs in CI |
|---|---|---|---|---|
| Backend unit | One class at a time, collaborators mocked (JUnit 5, Mockito, MockMvc). Name: `*Test.java` | `src/test/java/...` | `mvn test` (no Docker) | every PR and push, `ci.yml` → `backend` |
| Backend integration | The real app against a throwaway TimescaleDB (Testcontainers): migrations, JPA mappings, wiring. Name: `*IT.java` | `src/test/java/...` | `mvn verify` (needs Docker) | every PR and push, `ci.yml` → `backend` |
| Frontend unit | Components and helpers in jsdom (Vitest, Testing Library) | `frontend/tests/unit/`, mirroring `frontend/src/` | `npm test` in `frontend/` | every PR and push, `ci.yml` → `frontend` |
| Static checks | Lint (oxlint), type-check (`tsc`), OpenAPI codegen compile, production build | — | `npm run lint`, `npm run typecheck`, `npm run build` | every PR and push, `ci.yml` |
| Container images | Both Dockerfiles still build | `Dockerfile`, `frontend/Dockerfile` | `docker build .` / `docker build frontend` | every PR and push, `ci.yml` → `docker` |
| End-to-end (MQTT) | One device publishes over MQTT; the reading comes back from the API | `tests/e2e/mqtt/` | see [MQTT smoke and load tests](mqtt-smoke-and-load.md) | manual: **Actions → MQTT smoke test** |
| Load (MQTT) | A fleet of virtual devices; every reading verified in PostgreSQL, publish-to-database latency, container CPU/RAM | `tests/load/mqtt/` | see [MQTT smoke and load tests](mqtt-smoke-and-load.md) | manual: **Actions → MQTT load test** |

Shared helpers for the MQTT tests (device provisioning, Python requirements,
the local `.env`) are in `tests/support/mqtt/`.

Not automated yet: the broker sync for device credentials is unit tested against
a stubbed broker (`MosquittoDeviceProvisionerTest`, `DeviceCredentialServiceTest`,
`BrokerCredentialExpiryTest`) and was verified by hand against a real Mosquitto
2.1 broker with the production ACL defaults. The live WebSocket is covered by
unit tests of its handler and hub and of the frontend helpers, but not end to
end with a database and broker running together.

Why the layout differs per stack: Java tests stay in `src/test/java` because
Maven, Surefire/Failsafe and IDEs expect them there. Frontend tests stay inside
`frontend/` because they import packages from `frontend/node_modules`. The
top-level `tests/` holds suites that test the running system from outside.

## What happens on a pull request

1. `ci.yml` starts on GitHub-hosted Ubuntu runners with three parallel jobs:
   `backend`, `frontend` and `docker`.
2. Each job reports a check on the PR. If `backend` fails, its Surefire and
   Failsafe reports are attached to the run as the `backend-test-reports`
   artifact.
3. Branch protection blocks the merge until all three checks pass and a
   reviewer approves.
4. Pushing again to the PR cancels the outdated run.

The MQTT workflows use a self-hosted runner on a developer machine and are
manual only. Never add a `pull_request` trigger to them: that would run a PR's
code on that machine.

## Before you push

```bash
# backend
mvn test          # quick; add `verify` when you touch persistence or wiring

# frontend
cd frontend
npm run lint && npm run typecheck && npm test && npm run build
```

## Adding tests

- **Backend unit test:** `src/test/java/com/openroots/<package>/<ClassName>Test.java`.
- **Backend integration test:** end the class name with `IT` so Failsafe runs
  it in `mvn verify`. See `IoTApplicationIT` for the Testcontainers setup.
- **Frontend test:** mirror the source path, for example
  `frontend/src/views/AuthScreen.tsx` → `frontend/tests/unit/views/AuthScreen.test.tsx`.
