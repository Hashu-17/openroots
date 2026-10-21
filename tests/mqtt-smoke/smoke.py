#!/usr/bin/env python3
"""Publish one authenticated telemetry message and verify TimescaleDB persistence."""

from __future__ import annotations

import json
import os
import sys
import time
import uuid
from datetime import datetime, timezone

import psycopg
from paho.mqtt import client as mqtt


def required(name: str) -> str:
    value = os.environ.get(name)
    if not value:
        raise RuntimeError(f"{name} is required")
    return value


def utc_now() -> datetime:
    return datetime.now(timezone.utc).replace(microsecond=0)


def publish(topic: str, payload: dict[str, object]) -> None:
    connected = False
    client = mqtt.Client(
        callback_api_version=mqtt.CallbackAPIVersion.VERSION2,
        client_id=f"openroots-smoke-{uuid.uuid4()}",
    )
    client.username_pw_set(required("MQTT_USERNAME"), required("MQTT_PASSWORD"))

    def on_connect(_client: mqtt.Client, _userdata: object, _flags: dict, reason_code, _properties):
        nonlocal connected
        if reason_code.is_failure:
            raise RuntimeError(f"MQTT connection failed: {reason_code}")
        connected = True

    client.on_connect = on_connect
    client.connect(os.environ.get("MQTT_HOST", "127.0.0.1"), int(os.environ.get("MQTT_PORT", "1883")), 10)
    client.loop_start()
    try:
        deadline = time.monotonic() + 15
        while not connected and time.monotonic() < deadline:
            time.sleep(0.1)
        if not connected:
            raise RuntimeError("timed out waiting for MQTT connection")
        result = client.publish(topic, json.dumps(payload), qos=1)
        if result.rc != mqtt.MQTT_ERR_SUCCESS:
            raise RuntimeError(f"MQTT publish failed: {mqtt.error_string(result.rc)}")
        result.wait_for_publish(timeout=15)
        if not result.is_published():
            raise RuntimeError("MQTT publish was not acknowledged")
    finally:
        client.loop_stop()
        client.disconnect()


def verify_rows(device_id: str, channels: list[str], captured_at: datetime) -> None:
    connection = {
        "host": os.environ.get("DATABASE_HOST", "127.0.0.1"),
        "port": int(os.environ.get("DATABASE_PORT", "5432")),
        "dbname": required("DATABASE_NAME"),
        "user": required("DATABASE_USER"),
        "password": required("DATABASE_PASSWORD"),
    }
    deadline = time.monotonic() + int(os.environ.get("VERIFY_TIMEOUT_SECONDS", "30"))
    query = """
        SELECT s.channel_name
        FROM telemetry t
        JOIN sensor s ON s.id = t.sensor_id
        WHERE s.device_id = %s
          AND t.recorded_at BETWEEN %s - INTERVAL '2 seconds'
                                AND %s + INTERVAL '2 seconds'
    """
    while time.monotonic() < deadline:
        with psycopg.connect(**connection) as database:
            rows = database.execute(
                query,
                (device_id, captured_at, captured_at),
            ).fetchall()
        found = {row[0] for row in rows}
        if set(channels).issubset(found):
            return
        time.sleep(1)
    raise RuntimeError(f"telemetry was not persisted for channels: {sorted(set(channels) - found)}")


def main() -> int:
    device_id = required("DEVICE_ID")
    values = json.loads(required("SENSOR_VALUES_JSON"))
    if not isinstance(values, dict) or not values:
        raise RuntimeError("SENSOR_VALUES_JSON must be a non-empty JSON object")
    captured_at = utc_now()
    topic = f"openroots/v1/devices/{device_id}/telemetry"
    publish(topic, {"capturedAt": captured_at.isoformat(), "payload": values})
    verify_rows(device_id, list(values), captured_at)
    print(f"PASS: persisted {len(values)} telemetry channels for {device_id}")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as error:
        print(f"FAIL: {error}", file=sys.stderr)
        raise SystemExit(1)
