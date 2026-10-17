#!/usr/bin/env python3
"""Publish sample readings to a registered OpenRoots MQTT device topic."""

from datetime import datetime, timezone
import json
import math
import os
import sys
import time
from uuid import uuid4

import paho.mqtt.client as mqtt


def required_env(name: str) -> str:
    value = os.environ.get(name, "").strip()
    if not value:
        raise ValueError(f"Set {name} in the local environment before running this script")
    return value


def telemetry_values() -> dict[str, float]:
    values_json = os.environ.get("TELEMETRY_VALUES_JSON", "").strip()
    if values_json:
        values = json.loads(values_json)
        if not isinstance(values, dict) or not values:
            raise ValueError("TELEMETRY_VALUES_JSON must be a non-empty JSON object")

        readings: dict[str, float] = {}
        for channel, value in values.items():
            if not isinstance(channel, str) or not channel.strip():
                raise ValueError("telemetry channel names must be non-empty strings")
            if isinstance(value, bool) or not isinstance(value, (int, float)):
                raise ValueError(f"telemetry value for {channel!r} must be a JSON number")
            if not math.isfinite(value):
                raise ValueError(f"telemetry value for {channel!r} must be finite")
            readings[channel] = float(value)
        return readings

    # Keep the original single-channel configuration working for existing users.
    channel = required_env("TELEMETRY_CHANNEL")
    value = float(required_env("TELEMETRY_VALUE"))
    if not math.isfinite(value):
        raise ValueError("TELEMETRY_VALUE must be a finite number")
    return {channel: value}


def publish_interval() -> float:
    raw_value = os.environ.get("PUBLISH_INTERVAL_SECONDS", "10").strip()
    try:
        seconds = float(raw_value)
    except ValueError as exc:
        raise ValueError("PUBLISH_INTERVAL_SECONDS must be a number of seconds") from exc
    if not math.isfinite(seconds) or seconds < 1:
        raise ValueError("PUBLISH_INTERVAL_SECONDS must be at least 1 second")
    return seconds


def main() -> int:
    host = required_env("MQTT_HOST")
    username = required_env("MQTT_USERNAME")
    password = required_env("MQTT_PASSWORD")
    topic = required_env("MQTT_TOPIC")
    port = int(os.environ.get("MQTT_PORT", "1883"))
    readings = telemetry_values()
    interval = publish_interval()

    client = mqtt.Client(
        callback_api_version=mqtt.CallbackAPIVersion.VERSION2,
        client_id=f"openroots-pi-{uuid4().hex}",
        protocol=mqtt.MQTTv311,
    )
    client.username_pw_set(username, password)

    try:
        client.connect(host, port, keepalive=30)
        client.loop_start()
        summary = ", ".join(f"{channel}={value:g}" for channel, value in readings.items())
        print(f"Publishing {summary} to {topic} every {interval:g} seconds. Press Ctrl+C to stop.")
        try:
            while True:
                captured_at = datetime.now(timezone.utc).isoformat().replace("+00:00", "Z")
                message = {"capturedAt": captured_at, "payload": readings}
                result = client.publish(topic, json.dumps(message, allow_nan=False), qos=1, retain=False)
                if result.rc != mqtt.MQTT_ERR_SUCCESS:
                    raise RuntimeError(f"MQTT publish failed with status {result.rc}")
                result.wait_for_publish(timeout=10)
                if not result.is_published():
                    raise TimeoutError("The broker did not acknowledge the reading within 10 seconds")
                print(f"Published at {captured_at}: {summary}")
                time.sleep(interval)
        except KeyboardInterrupt:
            print("Publisher stopped.")
    finally:
        if client.is_connected():
            client.disconnect()
        client.loop_stop()

    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as exc:
        print(f"Publish failed: {exc}", file=sys.stderr)
        raise SystemExit(1) from exc
