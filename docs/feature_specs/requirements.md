# Product Requirements

## Product

Our platform currently supports project creation, registering of devices, receiving telemetry from registered devices and exposing live telemetry data to a web client.

## Version 1

- The backend exposes a versioned REST API under `/api/v1`.
- Users can authenticate with an email address and password.
- Authenticated users can access only resources they are authorized to use.
- Projects group devices and support sharing with multiple users.
- Devices can send telemetry through supported uplinks such as HTTPS and MQTT.
- Users can list and inspect devices and telemetry.
- Device credentials are separate from human user credentials.
- Passwords and device secrets are never stored in plaintext.
- Invalid requests return structured error responses.

## Auth Scope

- User registration and login.
- Stateless JWT access tokens.
- Project membership and roles.
- Project-level authorization for devices and telemetry.
- Hashed device secrets or API keys with revocation and rotation.

Certificates are explicitly deferred until a later phase.

## Extended Goals

- Refresh-token rotation.
- Multi-factor authentication.
- Certificate-based device authentication.
- Fine-grained per-field permissions.