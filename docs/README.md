# Overview

This folder documents the current `spring-mqtt-ingestion` implementation. Where a document describes a target or proposed feature rather than implemented behavior, it says so explicitly.

## Product and Behavior

- [Requirements](product/requirements.md)
- [Use cases](product/use-cases.md)
- [Business rules](product/business-rules.md)

## Architecture

- [Architecture guide and C4 diagrams](architecture/README.md)

## Developer Guides

- [Frontend Guide](frontend/README.md)
- Backend Guide
- Testing

## References

- [Authentication and Authorization](security/authentication.md)
- [Telemetry MQTT contract](contracts/mqtt-telemetry.md)
- [OpenAPI description](openapi.yaml) 

The `v1` in `/api/v1` is the HTTP API's major compatibility version. It is independent of the product release number and the OpenAPI document's `info.version` field.


