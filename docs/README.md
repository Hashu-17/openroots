# OpenRoots documentation

This folder documents the current `spring-mqtt-ingestion` implementation. Where a document describes a target or proposed feature rather than implemented behavior, it says so explicitly.

## Product and behavior

- [Requirements](feature_specs/requirements.md)
- [Use cases](feature_specs/use-cases.md)
- [Business rules](feature_specs/business-rules.md)

## Architecture

- [Architecture guide and C4 diagrams](architecture/README.md)
- [Authentication status and target design](architecture/authentication.md)
- [Telemetry MQTT contract](architecture/telemetry-contract.md)

## API contract

- [OpenAPI description](openapi.yaml) is the contract used to generate Spring API interfaces and models during Maven builds.

The `v1` in `/api/v1` is the HTTP API's major compatibility version. It is independent of the product release number and the OpenAPI document's `info.version` field.
