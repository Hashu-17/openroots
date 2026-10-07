# Product Requirements

## Product

OpenRoots is a self-hosted IoT platform for registering devices, receiving telemetry, sending commands, and exposing device data to web or mobile clients.

## Version 1 requirements

- The backend exposes a versioned REST API under `/api/v1`.
- Users can authenticate with an email address and password.
- Authenticated users can access only resources they are authorized to use.
- Projects group devices and support sharing with multiple users.
- Devices can send telemetry through supported uplinks such as HTTPS and MQTT.
- Users can list and inspect devices and telemetry.
- Device credentials are separate from human user credentials.
- Passwords and device secrets are never stored in plaintext.
- Invalid requests return structured error responses.

## Phase 4 scope

- User registration and login.
- Stateless JWT access tokens.
- Project membership and roles.
- Project-level authorization for devices and telemetry.
- Hashed device secrets or API keys with revocation and rotation.

Certificates are explicitly deferred until a later phase.

## Non-goals for Phase 4

- Refresh-token rotation.
- Social login.
- Multi-factor authentication.
- Certificate-based device authentication.
- Fine-grained per-field permissions.