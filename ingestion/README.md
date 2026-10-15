# Go Telemetry Ingestion Service

This root-level directory is reserved for the OpenRoots Go telemetry service.
The service will consume authenticated MQTT messages from Eclipse Mosquitto,
validate them against provisioned device and sensor configuration, and write
telemetry to TimescaleDB. Its implementation will be introduced in a later
integration step.
