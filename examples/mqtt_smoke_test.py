#!/usr/bin/env python3
"""Publish one MQTT reading and wait until the backend exposes it via its API."""

from __future__ import annotations

import json
import os
import sys
import threading
import time
import uuid
from datetime import datetime, timezone
from urllib.error import URLError
from urllib.parse import urlencode
from urllib.request import urlopen

import paho.mqtt.client as mqtt


def required_env(name: str) -> str:
    value = os.getenv(name)
    if not value:
        raise ValueError(f"required environment variable is missing: {name}")
    return value


def telemetry_visible(api_url: str, device_id: str, captured_at: datetime, channel: str, value: float) -> bool:
    query = urlencode({"deviceId": device_id})
    with urlopen(f"{api_url.rstrip('/')}/api/v1/telemetry?{query}", timeout=5) as response:
        result = json.load(response)
    for record in result.get("data", []):
        received_at = datetime.fromisoformat(record["capturedAt"].replace("Z", "+00:00"))
        actual = record.get("payload", {}).get(channel)
        if received_at == captured_at and actual is not None and float(actual) == value:
            return True
    return False


def main() -> int:
    try:
        device_id = str(uuid.UUID(required_env("MQTT_SMOKE_DEVICE_ID")))
        password = required_env("MQTT_SMOKE_DEVICE_PASSWORD")
    except ValueError as error:
        print(error, file=sys.stderr)
        return 2

    broker_host = os.getenv("OPENROOTS_MQTT_HOST", "127.0.0.1")
    broker_port = int(os.getenv("OPENROOTS_MQTT_PORT", "1883"))
    api_url = os.getenv("OPENROOTS_API_URL", "http://127.0.0.1:8080")
    channel = os.getenv("MQTT_SMOKE_CHANNEL", "temperature")
    timeout_seconds = float(os.getenv("MQTT_SMOKE_TIMEOUT", "30"))
    value = 23.75
    now = datetime.now(timezone.utc)
    captured_at = now.replace(microsecond=(now.microsecond // 1000) * 1000)
    captured_at_text = captured_at.isoformat(timespec="milliseconds").replace("+00:00", "Z")
    connected = threading.Event()
    connection_failure: list[str] = []

    def on_connect(client, userdata, flags, reason_code, properties):
        if reason_code.is_failure:
            connection_failure.append(str(reason_code))
        connected.set()

    client = mqtt.Client(
        mqtt.CallbackAPIVersion.VERSION2,
        client_id=f"openroots-smoke-{uuid.uuid4()}",
        protocol=mqtt.MQTTv311,
        clean_session=True,
    )
    client.username_pw_set(device_id, password)
    client.on_connect = on_connect

    try:
        client.connect(broker_host, broker_port, keepalive=30)
        client.loop_start()
        if not connected.wait(10):
            raise TimeoutError("timed out waiting for MQTT connection")
        if connection_failure:
            raise ConnectionError(f"MQTT connection refused: {connection_failure[0]}")

        topic = f"openroots/v1/devices/{device_id}/telemetry"
        message = json.dumps({
            "deviceId": device_id,
            "capturedAt": captured_at_text,
            "payload": {channel: value},
        })
        sent_at = time.monotonic()
        info = client.publish(topic, message, qos=1, retain=False)
        if info.rc != mqtt.MQTT_ERR_SUCCESS:
            raise RuntimeError(f"MQTT publish failed immediately with rc={info.rc}")
        info.wait_for_publish(timeout=10)
        if not info.is_published():
            raise TimeoutError("timed out waiting for MQTT PUBACK")
        print(f"Broker acknowledged the QoS 1 publish in {(time.monotonic() - sent_at) * 1000:.1f} ms")
    except Exception as error:
        print(f"MQTT smoke test failed: {error}", file=sys.stderr)
        return 1
    finally:
        if client.is_connected():
            client.disconnect()
        client.loop_stop()

    deadline = time.monotonic() + timeout_seconds
    while time.monotonic() < deadline:
        try:
            if telemetry_visible(api_url, device_id, captured_at, channel, value):
                print(f"Backend persisted telemetry for device {device_id} ({channel}={value})")
                return 0
        except (URLError, TimeoutError, KeyError, ValueError, json.JSONDecodeError) as error:
            print(f"Waiting for telemetry in API: {error}")
        time.sleep(1)

    print(
        f"MQTT publish reached the broker, but telemetry was not visible from {api_url} "
        f"within {timeout_seconds:g}s. Check backend ingestion, the registered channel, and database.",
        file=sys.stderr,
    )
    return 1


if __name__ == "__main__":
    raise SystemExit(main())
