#!/usr/bin/env python3
"""Publish periodic MQTT telemetry from a fleet of registered test devices."""

from __future__ import annotations

import argparse
import json
import os
import statistics
import threading
import time
import uuid
from dataclasses import dataclass, field
from datetime import datetime, timezone
from pathlib import Path

import paho.mqtt.client as mqtt


def load_local_env() -> None:
    """Load examples/.env for local runs without overriding workflow variables."""
    env_file = Path(__file__).with_name(".env")
    if not env_file.is_file():
        return
    for raw_line in env_file.read_text(encoding="utf-8").splitlines():
        line = raw_line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        name, value = line.split("=", 1)
        name = name.strip()
        value = value.strip()
        if len(value) >= 2 and value[0] == value[-1] and value[0] in ("'", '\"'):
            value = value[1:-1]
        if name:
            os.environ.setdefault(name, value)


@dataclass
class Results:
    lock: threading.Lock = field(default_factory=threading.Lock)
    acknowledged: int = 0
    failed: int = 0
    latencies_ms: list[float] = field(default_factory=list)
    errors: list[str] = field(default_factory=list)

    def record(self, latency_ms: float | None, error: str | None = None) -> None:
        with self.lock:
            if error is None:
                self.acknowledged += 1
                self.latencies_ms.append(latency_ms or 0.0)
            else:
                self.failed += 1
                if len(self.errors) < 10:
                    self.errors.append(error)


def read_device_ids(path: str) -> list[str]:
    with open(path, encoding="utf-8") as device_file:
        device_ids = [line.strip() for line in device_file
                      if line.strip() and not line.lstrip().startswith("#")]
    if not device_ids:
        raise ValueError("device file contains no device UUIDs")
    if len(set(device_ids)) != len(device_ids):
        raise ValueError("device file contains duplicate UUIDs")
    for device_id in device_ids:
        uuid.UUID(device_id)
    return device_ids


def percentile(values: list[float], fraction: float) -> float:
    if not values:
        return 0.0
    ordered = sorted(values)
    index = min(len(ordered) - 1, int((len(ordered) - 1) * fraction))
    return ordered[index]


def main() -> int:
    load_local_env()
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--devices", required=True, help="text file with one registered device UUID per line")
    parser.add_argument("--host", default=os.getenv("OPENROOTS_MQTT_HOST", "127.0.0.1"))
    parser.add_argument("--port", type=int, default=int(os.getenv("OPENROOTS_MQTT_PORT", "1883")))
    parser.add_argument("--period", type=float, default=10.0, help="seconds between each device's messages")
    parser.add_argument("--duration", type=float, default=120.0, help="test duration in seconds")
    parser.add_argument("--connect-timeout", type=float, default=15.0,
                        help="maximum seconds to wait for each broker CONNACK")
    parser.add_argument("--qos", type=int, choices=(0, 1, 2), default=1)
    parser.add_argument("--channel", action="append", dest="channels",
                        help="registered numeric sensor channel; repeat to send multiple readings")
    args = parser.parse_args()

    if args.period <= 0 or args.duration <= 0 or args.connect_timeout <= 0:
        parser.error("--period, --duration, and --connect-timeout must be greater than zero")
    device_ids = read_device_ids(args.devices)
    channels = args.channels or ["temperature"]
    password = os.getenv("OPENROOTS_MQTT_DEVICE_PASSWORD")
    if not password:
        parser.error("set OPENROOTS_MQTT_DEVICE_PASSWORD to the password provisioned for each test device")

    results = Results()
    clients: list[tuple[str, mqtt.Client]] = []
    barrier = threading.Barrier(len(device_ids) + 1)
    stop = threading.Event()
    start_time = 0.0
    deadline = 0.0

    print(f"Connecting {len(device_ids)} devices to {args.host}:{args.port} (QoS {args.qos})...")
    try:
        for device_id in device_ids:
            connack_received = threading.Event()
            connection_result: list[tuple[bool, str]] = []
            client = mqtt.Client(
                mqtt.CallbackAPIVersion.VERSION2,
                client_id=f"openroots-load-{device_id}",
                protocol=mqtt.MQTTv311,
                clean_session=True,
            )
            client.username_pw_set(device_id, password)

            def on_connect(client, userdata, flags, reason_code, properties,
                           received=connack_received, result=connection_result):
                result.append((reason_code.is_failure, str(reason_code)))
                received.set()

            client.on_connect = on_connect
            client.connect(args.host, args.port, keepalive=30)
            client.loop_start()
            clients.append((device_id, client))
            if not connack_received.wait(args.connect_timeout):
                raise TimeoutError(f"{device_id}: timed out waiting for broker CONNACK")
            if not connection_result or connection_result[-1][0]:
                reason = connection_result[-1] if connection_result else "no CONNACK received"
                if isinstance(reason, tuple):
                    reason = reason[1]
                raise ConnectionError(f"{device_id}: broker rejected MQTT connection ({reason})")
    except Exception as error:
        for _, client in clients:
            client.disconnect()
            client.loop_stop()
        print(f"Connection failed: {error}")
        return 2

    print(f"Broker connections accepted: {len(clients)}/{len(device_ids)}")

    def publish_loop(device_id: str, client: mqtt.Client) -> None:
        topic = f"openroots/v1/devices/{device_id}/telemetry"
        sequence = 0
        try:
            barrier.wait()
            while not stop.is_set() and time.monotonic() < deadline:
                captured_at = datetime.now(timezone.utc).isoformat(timespec="milliseconds").replace("+00:00", "Z")
                payload_values = {
                    channel: round(20.0 + (sequence % 100) / 10.0 + index, 2)
                    for index, channel in enumerate(channels)
                }
                body = json.dumps({"deviceId": device_id, "capturedAt": captured_at,
                                   "payload": payload_values})
                sent_at = time.perf_counter()
                info = client.publish(topic, body, qos=args.qos, retain=False)
                if info.rc != mqtt.MQTT_ERR_SUCCESS:
                    results.record(None, f"{device_id}: publish returned rc={info.rc} ({mqtt.error_string(info.rc)})")
                else:
                    try:
                        info.wait_for_publish(timeout=max(5.0, args.period))
                        if not info.is_published():
                            raise TimeoutError("broker did not acknowledge publish before timeout")
                        results.record((time.perf_counter() - sent_at) * 1000)
                    except Exception as error:
                        results.record(None, f"{device_id}: {error}")
                sequence += 1
                next_publish = start_time + sequence * args.period
                stop.wait(max(0.0, next_publish - time.monotonic()))
        except threading.BrokenBarrierError:
            pass

    workers = [threading.Thread(target=publish_loop, args=(device_id, client), daemon=True)
               for device_id, client in clients]
    start_time = time.monotonic()
    deadline = start_time + args.duration
    for worker in workers:
        worker.start()
    barrier.wait()
    print(f"Publishing {', '.join(channels)} every {args.period:g}s for {args.duration:g}s; first message is synchronized.")
    try:
        stop.wait(args.duration)
    except KeyboardInterrupt:
        print("Stopping early...")
    stop.set()
    for worker in workers:
        worker.join()
    for _, client in clients:
        client.disconnect()
        client.loop_stop()

    elapsed = max(time.monotonic() - start_time, 0.001)
    latency = results.latencies_ms
    print("\nResults")
    print(f"  devices connected: {len(clients)}")
    print(f"  acknowledged:      {results.acknowledged}")
    print(f"  failed:            {results.failed}")
    print(f"  publish rate:      {results.acknowledged / elapsed:.2f} messages/s")
    if latency:
        print(f"  PUBACK latency ms: mean {statistics.fmean(latency):.2f}, "
              f"p95 {percentile(latency, 0.95):.2f}, max {max(latency):.2f}")
    if results.errors:
        print("  sample errors:")
        for error in results.errors:
            print(f"    - {error}")
    print("\nPUBACK confirms broker receipt; check the database separately to confirm ingestion and persistence.")
    return 1 if results.failed else 0


if __name__ == "__main__":
    raise SystemExit(main())
