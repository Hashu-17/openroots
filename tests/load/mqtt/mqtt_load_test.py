#!/usr/bin/env python3
"""Publish periodic MQTT telemetry from a fleet of registered test devices."""

from __future__ import annotations

import argparse
import csv
import json
import os
import re
import statistics
import subprocess
import threading
import time
import uuid
from dataclasses import dataclass, field
from datetime import datetime, timezone
from pathlib import Path

import paho.mqtt.client as mqtt

RESOURCE_CONTAINERS = ("iot-backend", "iot-database", "openroots-mosquitto")


def parse_memory_bytes(value: str) -> int:
    match = re.fullmatch(r"\s*([0-9.]+)\s*([KMGT]?i?B)\s*", value, re.IGNORECASE)
    if not match:
        raise ValueError(f"unrecognized Docker memory value: {value!r}")
    amount = float(match.group(1))
    unit = match.group(2).upper()
    powers = {"B": 0, "KB": 1, "KIB": 1, "MB": 2, "MIB": 2,
              "GB": 3, "GIB": 3, "TB": 4, "TIB": 4}
    base = 1024 if "I" in unit else 1000
    return int(amount * (base ** powers[unit]))


class ResourceSampler:
    """Capture Docker container CPU and memory while the publishers are active."""

    def __init__(self, output: Path, interval: float):
        self.output = output
        self.interval = interval
        self.stop = threading.Event()
        self.thread: threading.Thread | None = None
        self.rows: list[dict[str, str | int | float]] = []
        self.errors: list[str] = []

    def sample(self, phase: str) -> None:
        command = ["docker", "stats", "--no-stream", "--format",
                   "{{.Name}}|{{.CPUPerc}}|{{.MemUsage}}|{{.PIDs}}",
                   *RESOURCE_CONTAINERS]
        try:
            result = subprocess.run(command, check=True, capture_output=True, text=True, timeout=30)
            lines = [line for line in result.stdout.splitlines() if line.strip()]
            parsed: dict[str, tuple[str, str, str, str]] = {}
            for line in lines:
                fields = line.split("|")
                if len(fields) != 4:
                    raise ValueError(f"unexpected docker stats output: {line!r}")
                name, cpu, memory, pids = fields
                used, separator, limit = memory.partition(" / ")
                if not separator:
                    raise ValueError(f"unexpected Docker memory output: {memory!r}")
                parsed[name] = (cpu.rstrip("%"), used, limit, pids)
            missing = [name for name in RESOURCE_CONTAINERS if name not in parsed]
            if missing:
                raise RuntimeError(f"docker stats did not return running containers: {', '.join(missing)}")
            timestamp = datetime.now(timezone.utc).isoformat(timespec="seconds").replace("+00:00", "Z")
            self.output.parent.mkdir(parents=True, exist_ok=True)
            with self.output.open("a", newline="", encoding="utf-8") as output_file:
                writer = csv.DictWriter(output_file, fieldnames=[
                    "timestamp_utc", "phase", "container", "cpu_percent",
                    "memory_used_bytes", "memory_limit_bytes", "memory_used_mib",
                    "memory_limit_mib", "pids",
                ])
                for name in RESOURCE_CONTAINERS:
                    cpu_text, used_text, limit_text, pids_text = parsed[name]
                    used_bytes = parse_memory_bytes(used_text)
                    limit_bytes = parse_memory_bytes(limit_text)
                    row: dict[str, str | int | float] = {
                        "timestamp_utc": timestamp,
                        "phase": phase,
                        "container": name,
                        "cpu_percent": float(cpu_text),
                        "memory_used_bytes": used_bytes,
                        "memory_limit_bytes": limit_bytes,
                        "memory_used_mib": round(used_bytes / (1024 ** 2), 2),
                        "memory_limit_mib": round(limit_bytes / (1024 ** 2), 2),
                        "pids": int(pids_text),
                    }
                    writer.writerow(row)
                    self.rows.append(row)
                    print(f"[resources {phase} {timestamp}] {name}: "
                          f"CPU {row['cpu_percent']:.1f}%, RAM {row['memory_used_mib']:.1f} MiB, "
                          f"PIDs {row['pids']}", flush=True)
        except Exception as error:
            message = f"resource sample failed: {error}"
            self.errors.append(message)
            print(f"[resources] WARNING: {message}", flush=True)

    def start(self) -> None:
        self.output.parent.mkdir(parents=True, exist_ok=True)
        with self.output.open("w", newline="", encoding="utf-8") as output_file:
            csv.DictWriter(output_file, fieldnames=[
                "timestamp_utc", "phase", "container", "cpu_percent",
                "memory_used_bytes", "memory_limit_bytes", "memory_used_mib",
                "memory_limit_mib", "pids",
            ]).writeheader()
        self.sample("baseline")
        self.thread = threading.Thread(target=self._sample_loop, name="docker-resource-sampler", daemon=True)
        self.thread.start()

    def _sample_loop(self) -> None:
        next_sample = time.monotonic() + self.interval
        while True:
            if self.stop.wait(max(0.0, next_sample - time.monotonic())):
                return
            self.sample("during")
            next_sample += self.interval
            now = time.monotonic()
            while next_sample <= now:
                next_sample += self.interval

    def finish(self) -> None:
        self.stop.set()
        if self.thread:
            self.thread.join()

    def print_summary(self) -> None:
        during_by_container: dict[str, list[dict[str, str | int | float]]] = {}
        for row in self.rows:
            if row["phase"] == "during":
                during_by_container.setdefault(str(row["container"]), []).append(row)
        print(f"  resource samples:  {self.output} ({sum(map(len, during_by_container.values()))} during-load container samples)")
        for name in RESOURCE_CONTAINERS:
            samples = during_by_container.get(name, [])
            if samples:
                cpus = [float(row["cpu_percent"]) for row in samples]
                memory = [float(row["memory_used_mib"]) for row in samples]
                print(f"  {name}: CPU avg/max {statistics.fmean(cpus):.1f}%/{max(cpus):.1f}%, "
                      f"RAM avg/max {statistics.fmean(memory):.1f}/{max(memory):.1f} MiB")
        if self.errors:
            print(f"  resource sampling warnings: {len(self.errors)}")

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


def read_devices(path: str) -> dict[str, str | None]:
    """Read ``uuid`` or ``uuid secret`` lines; a missing secret falls back to the shared password."""
    devices: dict[str, str | None] = {}
    with open(path, encoding="utf-8") as device_file:
        for line in device_file:
            if not line.strip() or line.lstrip().startswith("#"):
                continue
            parts = line.split()
            if len(parts) > 2:
                raise ValueError("device file lines must be 'uuid' or 'uuid secret'")
            device_id = str(uuid.UUID(parts[0]))
            if device_id in devices:
                raise ValueError("device file contains duplicate UUIDs")
            devices[device_id] = parts[1] if len(parts) == 2 else None
    if not devices:
        raise ValueError("device file contains no device UUIDs")
    return devices


def percentile(values: list[float], fraction: float) -> float:
    if not values:
        return 0.0
    ordered = sorted(values)
    index = min(len(ordered) - 1, int((len(ordered) - 1) * fraction))
    return ordered[index]


def main() -> int:
    load_local_env()
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--devices", required=True,
                        help="file with one 'uuid secret' (or bare 'uuid') line per registered device")
    parser.add_argument("--host", default=os.getenv("OPENROOTS_MQTT_HOST", "127.0.0.1"))
    parser.add_argument("--port", type=int, default=int(os.getenv("OPENROOTS_MQTT_PORT", "1883")))
    parser.add_argument("--period", type=float, default=10.0, help="seconds between each device's messages")
    parser.add_argument("--duration", type=float, default=120.0, help="test duration in seconds")
    parser.add_argument("--connect-timeout", type=float, default=15.0,
                        help="maximum seconds to wait for each broker CONNACK")
    parser.add_argument("--qos", type=int, choices=(0, 1, 2), default=1)
    parser.add_argument("--resource-sample-interval", type=float, default=5.0,
                        help="seconds between Docker CPU/RAM samples")
    parser.add_argument("--resource-output", default="load-test-results/resource-samples.csv",
                        help="CSV path for Docker resource samples")
    parser.add_argument("--channel", action="append", dest="channels",
                        help="registered numeric sensor channel; repeat to send multiple readings")
    args = parser.parse_args()

    if args.period <= 0 or args.duration <= 0 or args.connect_timeout <= 0 or args.resource_sample_interval <= 0:
        parser.error("--period, --duration, --connect-timeout, and --resource-sample-interval must be greater than zero")
    devices = read_devices(args.devices)
    device_ids = list(devices)
    channels = args.channels or ["temperature"]
    shared_password = os.getenv("OPENROOTS_MQTT_DEVICE_PASSWORD")
    if any(secret is None for secret in devices.values()) and not shared_password:
        parser.error("the device file has devices without a secret; run provision_mqtt_fleet.py "
                     "or set OPENROOTS_MQTT_DEVICE_PASSWORD")

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
            client.username_pw_set(device_id, devices[device_id] or shared_password)

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
    resource_sampler = ResourceSampler(Path(args.resource_output), args.resource_sample_interval)
    resource_sampler.start()

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
    resource_sampler.finish()
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
    resource_sampler.print_summary()
    if latency:
        print(f"  PUBACK latency ms: mean {statistics.fmean(latency):.2f}, "
              f"p95 {percentile(latency, 0.95):.2f}, max {max(latency):.2f}")
    if results.errors:
        print("  sample errors:")
        for error in results.errors:
            print(f"    - {error}")
    print("\nPUBACK confirms broker receipt; check the database separately to confirm ingestion and persistence.")
    has_in_load_samples = any(row["phase"] == "during" for row in resource_sampler.rows)
    return 1 if results.failed or resource_sampler.errors or not has_in_load_samples else 0


if __name__ == "__main__":
    raise SystemExit(main())
