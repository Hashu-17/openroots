# MQTT smoke test

This test publishes one authenticated telemetry payload and verifies that every
channel in the payload is persisted in TimescaleDB. It uses the same topic and
payload contract on both the Spring and Go branches:

```text
openroots/v1/devices/{deviceId}/telemetry
```

The device must already exist with matching sensor channel names and valid MQTT
credentials. Do not commit a `.env` file or real credentials.

```sh
python3 -m venv .venv
. .venv/bin/activate
python -m pip install -r tests/mqtt-smoke/requirements.txt

export MQTT_USERNAME=...
export MQTT_PASSWORD=...
export DEVICE_ID=...
export SENSOR_VALUES_JSON='{"temperature":22.5,"humidity":58}'
export DATABASE_NAME=iot_backend
export DATABASE_USER=iot_backend
export DATABASE_PASSWORD=...
python tests/mqtt-smoke/smoke.py
```

Set `MQTT_HOST`, `MQTT_PORT`, `DATABASE_HOST`, `DATABASE_PORT`, and
`VERIFY_TIMEOUT_SECONDS` only when the defaults do not match the local
environment.
