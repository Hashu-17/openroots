# Overview

This folder documents the current `go-mqtt-ingestion` implementation. It matches `spring-mqtt-ingestion` feature for feature; the only difference is the ingestion layer: MQTT telemetry is consumed and stored by the Go service in `ingestion/` instead of by Spring. Where a document describes a target or proposed feature rather than implemented behavior, it says so explicitly.

## Product and Behavior

- [Requirements](product/requirements.md)
- [Use cases](product/use-cases.md)
- [Business rules](product/business-rules.md)

## Architecture

- [Architecture guide and C4 diagrams](architecture/README.md)

## Developer Guides

- [Frontend Guide](frontend/README.md)
- [Backend Javadoc](../target/reports/apidocs/index.html)
- [Testing and CI](testing/README.md): where tests live, when they run, what a pull request checks

### Backend 
Generate the Javadoc from the repository root:

```powershell
mvn compile javadoc:javadoc -DskipTests
```

Then open `target/reports/apidocs/index.html` in a browser.

## References

- [Authentication and Authorization](security/authentication.md)
- [Telemetry MQTT contract](contracts/mqtt-telemetry.md)
- [OpenAPI description](openapi.yaml) 

The `v1` in `/api/v1` is the HTTP API's major compatibility version. It is independent of the product release number and the OpenAPI document's `info.version` field.


