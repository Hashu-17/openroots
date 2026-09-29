# Business Rules

## Identity

- BR-001: A user email must be unique.
- BR-002: Passwords must be stored as one-way password hashes.
- BR-003: Authentication failures must not reveal whether an email exists.

## Projects and access

- BR-004: Every project must have at least one owner.
- BR-005: A project may be shared with multiple users.
- BR-006: A project member may access devices belonging to that project.
- BR-007: A viewer may read project resources but may not modify them.
- BR-008: A member may use project devices but may not manage project membership.
- BR-009: An owner or administrator may manage project membership and devices.
- BR-010: A user who is not a project member must not infer whether a project resource exists.

## Devices

- BR-011: Device credentials belong to one device.
- BR-012: Device secrets and API keys are stored only as hashes.
- BR-013: Revoked or expired device credentials cannot authenticate.
- BR-014: Device credentials can be rotated without changing the device identity.

## Telemetry

- BR-015: Telemetry must reference an existing device.
- BR-016: Telemetry must contain a device identifier, timestamp, and payload.
- BR-017: The server may assign the ingestion timestamp separately from the device timestamp.

Database constraints should enforce structural rules such as uniqueness and foreign keys. Services should enforce workflows and authorization rules.