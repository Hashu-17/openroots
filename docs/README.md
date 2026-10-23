# OpenRoots documentation

This folder documents the current `spring-mqtt-ingestion` implementation. Where a document describes a target or proposed feature rather than implemented behavior, it says so explicitly.

## Product and behavior

- [Requirements](product/requirements.md)
- [Use cases](product/use-cases.md)
- [Business rules](product/business-rules.md)

## Architecture

- [Architecture guide and C4 diagrams](architecture/README.md)

## Security

- [Authentication status and target design](security/authentication.md)

## Contracts and integrations

- [Telemetry MQTT contract](contracts/mqtt-telemetry.md)

## API contract

- [OpenAPI description](openapi.yaml) is the contract used to generate Spring API interfaces and models during Maven builds.

The `v1` in `/api/v1` is the HTTP API's major compatibility version. It is independent of the product release number and the OpenAPI document's `info.version` field.
