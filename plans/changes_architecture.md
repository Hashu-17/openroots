# Architecture Change Plan

> **Status:** Proposed. This plan records the intended Spring/Go split and the authentication decisions needed before implementation. No application code has been changed as part of this document update.

## Goal

Keep Spring Boot as the business/control plane and move MQTT telemetry ingestion, time-series persistence, telemetry history queries, and live telemetry delivery to a Go service in the repository-root `ingestion/` directory. See [`docs/architecture/architectural_changes.md`](../architecture/architectural_changes.md) for the target architecture and diagrams.

## Backend changes

1. Keep project, user, membership, device-registration, and sensor-configuration APIs in Spring Boot.
2. Add a Go telemetry service that consumes from Eclipse Mosquitto and writes telemetry to TimescaleDB.
3. Define a trusted provisioning path from Spring-managed device/sensor configuration to Go. Do not call Spring synchronously for every MQTT message.
4. Make Go own bounded telemetry history queries and live WebSocket updates.
5. Remove the current Spring telemetry path only after the equivalent Go path is running and verified.
6. Refactor the Spring Java package root from `com.yourcompany.ingestion` to `com.openroots`; place Go under repository-root `ingestion/`, not under `src/`.
7. Do not add Kafka or a separate processing engine in the first iteration.

## Frontend changes

Keep React as the dashboard. Use Spring APIs for projects, devices, users, and other business data. Use Go APIs for telemetry history and live updates. Add project/device/sensor selection and bounded time-range queries before building large historical charts.

## Authentication and device trust

### Current state

- Update (Phase 4): `SecurityConfig` now requires a JWT for every route except registration/login, health and API docs, and services enforce project-level authorization. The notes below describe the state before Phase 4; device-side authentication is still outstanding. See `docs/security/authentication.md`.
- (Historical) `SecurityConfig` permitted every request. HTTP Basic was configured, but `anyRequest().permitAll()` meant this was not an effective authentication boundary.
- The current device registration service creates device and sensor records but does not issue device credentials.
- `MqttConfig` and `WebSocketConfig` are empty configuration classes. No MQTT broker or Go telemetry service is present in the inspected deployment.
- The current Spring telemetry POST accepts a device ID in the request and checks that the device exists; this is not proof that the sender is that device.

Treat this as development behavior only. Do not expose the current API or a future MQTT broker publicly with these settings.

### Are these controls required during development?

It depends on what is being developed and where it is reachable:

| Development scenario | Minimum needed now |
|---|---|
| Frontend or business API work using local seeded/mock data | Full JWT and device authentication can be deferred while the environment remains local and private. Keep the unauthenticated profile unavailable from public interfaces. |
| Go ingestion integration using a simulator on a private Compose network | Use development-only broker credentials and topic restrictions so the identity-to-topic flow can be exercised. These credentials must not be reused outside development. |
| Testing an actual device on a trusted LAN | Give each device its own credential/identity and restrict it to its own publish topics. This is the right point to implement the first device-authentication slice. |
| Any broker/API reachable from the internet or carrying real customer data | Authentication, authorization, TLS where credentials cross an untrusted network, secret rotation/revocation, and monitoring are prerequisites. Do not use `permitAll()` or anonymous MQTT access. |

Human login and device MQTT authentication solve different problems. The React user proves who they are to business/telemetry APIs. A device proves its identity to Mosquitto. Do not reuse a human JWT as a device credential.

### JWT between services: conditional, not a blanket requirement

JWT validation is required wherever a service accepts a JWT. If Go accepts a user access token for history or WebSocket authorization, it must verify the signature and expected issuer, audience, expiry, and required claims; a well-formed token is not sufficient. If Spring and Go use service JWTs to call each other, those tokens need the same validation and a service-specific audience/identity.

However, a JWT is not automatically required just because there are two services. If Go receives device metadata through a separately authenticated provisioning mechanism, it does not need to call Spring for every reading. Service-to-service authentication can use another well-defined mechanism, such as mutual TLS or scoped service credentials. Choose one trust model for each connection and document key ownership and rotation.

### Device authentication and authorization

Device authorization means more than checking that an ID exists. The ingestion path must establish which device published a message and what that device is allowed to publish.

For the initial MQTT path:

1. Assign a distinct credential or identity to each device.
2. Have Mosquitto authenticate the connection and restrict publish access with topic permissions.
3. Bind the ACL-protected topic to a server-provisioned device record. A normal MQTT subscriber receives the message topic and payload, not necessarily the publisher's broker username; do not assume publisher identity is forwarded as message metadata. If identity must be passed separately, use a broker integration that securely supplies it.
4. Do not accept a payload's claimed ID as the trusted identity.
5. Load the device's assigned sensor schema from trusted provisioning data. Reject unassigned channels, invalid value types, and unauthorized topics before storage.
6. Support disabling or rotating a credential when a device is removed or compromised.

TLS protects credentials and telemetry in transit when they cross an untrusted network. A private local development network may use development-only credentials while TLS configuration is being built, but that does not qualify the broker for public deployment.

### Device authorization flow

```mermaid
sequenceDiagram
    participant Admin as Admin in React
    participant Spring as Spring business API
    participant Store as Credential/provisioning store
    participant M as Mosquitto
    participant G as Go ingestion service
    participant T as TimescaleDB

    Admin->>Spring: Register device and sensor channels
    Spring->>Store: Provision device identity and allowed topics
    Spring->>Store: Provision device sensor schema for Go
    Note over Store,G: Use a protected sync/feed or read-only metadata path; do not call Spring for every message
    M->>M: Authenticate device connection and enforce topic ACL
    M->>G: Deliver message from the ACL-protected device topic
    G->>G: Resolve device from trusted topic mapping and assigned schema
    G->>G: Validate payload and reject unauthorized channels
    G->>T: Persist valid telemetry
```

### Is device authorization a lot to implement now?

**A local development slice is manageable; a production-ready end-to-end system is a meaningful cross-service task.** The first slice should stay narrow: unique development device credentials, Mosquitto topic restrictions, a trusted mapping to registered devices/sensors, and Go-side payload validation. That is more work than adding a password check to the telemetry handler, but it is the minimum useful test of the architecture because it verifies identity across the broker-to-Go boundary.

The production version also needs secure credential provisioning/storage, TLS deployment, rotation and revocation, failure behavior when provisioning data is stale, audit/monitoring, and tests for cross-device topic access. Those can be staged, but they should be completed before public device traffic or real customer data is accepted.

User/project authorization for browser history and WebSocket subscriptions is a separate slice. It must ensure the signed-in user can access the project owning each requested device. It can follow local ingestion development, but must be in place before exposing telemetry to multiple users or outside a trusted development environment.

## Authentication implementation sequence

### Development integration milestone

- [ ] Keep development-only API access private; do not use the development profile on a public host.
- [ ] Select a Mosquitto authentication and topic-ACL approach.
- [x] Define the v1 MQTT topic, JSON envelope, device-ID mapping rule, and payload validation contract in [`docs/architecture/telemetry-contract.md`](../architecture/telemetry-contract.md).
- [ ] Define how Go receives registered sensor schemas and how updates/revocation reach it.
- [ ] Add a simulator/device credential for development and verify that one device cannot publish to another device's topic.
- [ ] Validate payload channels and values in Go before writing to TimescaleDB.

### Before public or multi-user telemetry access

- [x] Replace `permitAll()` with explicit human-user authentication and project-level authorization in Spring.
- [ ] Define whether Go validates user JWTs directly or delegates authorization through another trusted service boundary.
- [ ] If JWT validation is used, validate signature, issuer, audience, expiry, and required claims; define key rotation.
- [ ] Require an authenticated device identity, topic authorization, server-side schema assignment, and credential revocation.
- [ ] Configure TLS for untrusted network paths and remove development credentials/settings.
- [ ] Verify that users cannot read or subscribe to another project's telemetry and devices cannot publish as one another.

## Open decisions before code implementation

- Device credential format and Mosquitto authentication/ACL mechanism.
- Mosquitto authentication/ACL mechanism and how credentials are provisioned/revoked.
- How Go receives device/sensor metadata: internal API, shared read-only database access, or a change feed/cache.
- Whether user JWTs are signed with a shared key or an asymmetric key and how Go obtains trusted verification keys.
- Whether telemetry and business tables share a PostgreSQL server or use separate deployments.
- MQTT QoS, retry, duplicate/idempotency, and backpressure behavior.
- WebSocket subscription authorization and reconnection behavior.
