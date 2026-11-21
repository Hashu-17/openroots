#!/usr/bin/env python3
"""Publish one MQTT reading and wait until the backend exposes it via its API.

Each run signs in as the test user, issues a one-hour credential for the smoke device
(the backend syncs it to the broker), publishes with it, and revokes it at the end.
"""

from __future__ import annotations

import json
import os
import sys
import threading
import time
import uuid
from datetime import datetime, timedelta, timezone
from pathlib import Path
from urllib.parse import urlencode

import paho.mqtt.client as mqtt

sys.path.insert(0, str(Path(__file__).resolve().parents[2] / "support" / "mqtt"))
from openroots_api import ApiError, OpenRootsApi, user_credentials_from_env  # noqa: E402

CREDENTIAL_LIFETIME = timedelta(hours=1)


def required_env(name: str) -> str:
    value = os.getenv(name)
    if not value:
        raise ValueError(f"required environment variable is missing: {name}")
    return value


def load_local_env() -> None:
    """Load tests/support/mqtt/.env for local runs without overriding workflow variables."""
    env_file = Path(__file__).resolve().parents[2] / "support" / "mqtt" / ".env"
    if not env_file.is_file():
        return
    for raw_line in env_file.read_text(encoding="utf-8").splitlines():
        line = raw_line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        name, value = line.split("=", 1)
        name = name.strip()
        value = value.strip()
        if len(value) >= 2 and value[0] == value[-1] and value[0] in ("'", '"'):
            value = value[1:-1]
        if name:
            os.environ.setdefault(name, value)


def telemetry_visible(
    api: OpenRootsApi, device_id: str, captured_at: datetime, channel: str, value: float
) -> bool:
    query = urlencode({"deviceId": device_id})
    result = api.request(f"/api/v1/telemetry?{query}")
    for record in result.get("data", []):
        received_at = datetime.fromisoformat(record["capturedAt"].replace("Z", "+00:00"))
        actual = record.get("payload", {}).get(channel)
        if received_at == captured_at and actual is not None and float(actual) == value:
            return True
    return False


def publish_reading(
    broker_host: str, broker_port: int, device_id: str, password: str, channel: str, value: float,
    captured_at_text: str,
) -> None:
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
    finally:
        if client.is_connected():
            client.disconnect()
        client.loop_stop()


def wait_for_telemetry(
    api: OpenRootsApi, device_id: str, captured_at: datetime, channel: str, value: float, timeout_seconds: float
) -> bool:
    deadline = time.monotonic() + timeout_seconds
    while time.monotonic() < deadline:
        try:
            if telemetry_visible(api, device_id, captured_at, channel, value):
                return True
        except (ApiError, KeyError, ValueError, json.JSONDecodeError) as error:
            print(f"Waiting for telemetry in API: {error}")
        time.sleep(1)
    return False


def main() -> int:
    load_local_env()
    try:
        device_id = str(uuid.UUID(required_env("MQTT_SMOKE_DEVICE_ID")))
        email, user_password = user_credentials_from_env()
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

    api = OpenRootsApi(api_url, email, user_password)
    try:
        api.login()
        project_id = os.getenv("OPENROOTS_PROJECT_ID") or api.project_for_device(device_id)
        credential_id, secret = api.issue_credential(
            project_id, device_id, f"smoke-{captured_at_text}", CREDENTIAL_LIFETIME
        )
    except ApiError as error:
        print(f"MQTT smoke test setup failed: {error}", file=sys.stderr)
        return 1
    print(f"Issued a temporary credential for device {device_id} in project {project_id}")

    try:
        try:
            publish_reading(broker_host, broker_port, device_id, secret, channel, value, captured_at_text)
        except Exception as error:
            print(f"MQTT smoke test failed: {error}", file=sys.stderr)
            return 1

        if wait_for_telemetry(api, device_id, captured_at, channel, value, timeout_seconds):
            print(f"Backend persisted telemetry for device {device_id} ({channel}={value})")
            return 0
        print(
            f"MQTT publish reached the broker, but telemetry was not visible from {api_url} "
            f"within {timeout_seconds:g}s. Check backend ingestion, the registered channel, and database.",
            file=sys.stderr,
        )
        return 1
    finally:
        try:
            api.revoke_credential(project_id, device_id, credential_id)
            print("Revoked the temporary credential")
        except ApiError as error:
            # It still expires on its own after CREDENTIAL_LIFETIME.
            print(f"Could not revoke the temporary credential: {error}", file=sys.stderr)


if __name__ == "__main__":
    raise SystemExit(main())
