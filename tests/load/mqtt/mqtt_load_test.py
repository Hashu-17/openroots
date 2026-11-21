#!/usr/bin/env python3
"""MQTT ingestion load test: publish a device fleet, then prove the readings reached PostgreSQL.

The script is implementation-neutral. It talks only to the MQTT broker and the
database, so the same run can be pointed at the Spring or the Go ingestion
service. For each run it records every reading it sent, polls the telemetry
table until each reading appears (publish-to-database latency), samples Docker
resource usage, and writes a structured report to
``<output-dir>/<label>-<run-id>/``.
"""

from __future__ import annotations

import argparse
import csv
import heapq
import json
import math
import os
import re
import statistics
import subprocess
import sys
import threading
import time
import uuid
from dataclasses import dataclass, field
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

import paho.mqtt.client as mqtt

sys.path.insert(0, str(Path(__file__).resolve().parents[2] / "support" / "mqtt"))
from openroots_api import load_local_env  # noqa: E402

DEFAULT_CONTAINERS = ("iot-backend", "iot-database", "openroots-mosquitto")
RESOURCE_FIELDS = [
    "timestamp_utc", "phase", "container", "cpu_percent",
    "memory_used_bytes", "memory_limit_bytes", "memory_used_mib",
    "memory_limit_mib", "pids",
]
READING_FIELDS = [
    "device_id", "channel", "sequence", "phase", "captured_at", "sent_at_utc",
    "publish_rc", "puback_ms", "db_seen_at_utc", "db_latency_ms", "status",
]


# --------------------------------------------------------------------------- helpers

def utc_iso(epoch_seconds: float) -> str:
    return datetime.fromtimestamp(epoch_seconds, timezone.utc).isoformat(timespec="milliseconds").replace("+00:00", "Z")


def percentile(values: list[float], fraction: float) -> float | None:
    """Nearest-rank percentile; None when there are no values."""
    if not values:
        return None
    ordered = sorted(values)
    rank = max(1, math.ceil(fraction * len(ordered)))
    return ordered[min(rank, len(ordered)) - 1]


def distribution(values: list[float]) -> dict[str, float | int | None]:
    def rounded(value: float | None) -> float | None:
        return None if value is None else round(value, 2)
    return {
        "count": len(values),
        "mean": rounded(statistics.fmean(values)) if values else None,
        "p50": rounded(percentile(values, 0.50)),
        "p95": rounded(percentile(values, 0.95)),
        "p99": rounded(percentile(values, 0.99)),
        "max": rounded(max(values)) if values else None,
    }


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


def read_device_ids(path: str) -> list[str]:
    with open(path, encoding="utf-8") as device_file:
        device_ids = [line.strip() for line in device_file
                      if line.strip() and not line.lstrip().startswith("#")]
    if not device_ids:
        raise ValueError("device file contains no device UUIDs")
    if len(set(device_ids)) != len(device_ids):
        raise ValueError("device file contains duplicate UUIDs")
    return [str(uuid.UUID(device_id)) for device_id in device_ids]


def git_revision() -> dict[str, str | None]:
    def run(*command: str) -> str | None:
        try:
            result = subprocess.run(command, capture_output=True, text=True, timeout=10, check=True)
            return result.stdout.strip() or None
        except (OSError, subprocess.SubprocessError):
            return None
    return {"commit": run("git", "rev-parse", "HEAD"),
            "branch": run("git", "rev-parse", "--abbrev-ref", "HEAD")}


# --------------------------------------------------------------------------- resources

class ResourceSampler:
    """Capture Docker container CPU and memory while the publishers are active."""

    def __init__(self, output: Path, interval: float, containers: list[str]):
        self.output = output
        self.interval = interval
        self.containers = containers
        self.stop = threading.Event()
        self.thread: threading.Thread | None = None
        self.rows: list[dict[str, Any]] = []
        self.errors: list[str] = []

    def sample(self, phase: str) -> None:
        command = ["docker", "stats", "--no-stream", "--format",
                   "{{.Name}}|{{.CPUPerc}}|{{.MemUsage}}|{{.PIDs}}", *self.containers]
        try:
            result = subprocess.run(command, check=True, capture_output=True, text=True, timeout=30)
            parsed: dict[str, tuple[str, str, str, str]] = {}
            for line in (line for line in result.stdout.splitlines() if line.strip()):
                fields = line.split("|")
                if len(fields) != 4:
                    raise ValueError(f"unexpected docker stats output: {line!r}")
                name, cpu, memory, pids = fields
                used, separator, limit = memory.partition(" / ")
                if not separator:
                    raise ValueError(f"unexpected Docker memory output: {memory!r}")
                parsed[name] = (cpu.rstrip("%"), used, limit, pids)
            missing = [name for name in self.containers if name not in parsed]
            if missing:
                raise RuntimeError(f"docker stats did not return running containers: {', '.join(missing)}")
            timestamp = utc_iso(time.time())
            with self.output.open("a", newline="", encoding="utf-8") as output_file:
                writer = csv.DictWriter(output_file, fieldnames=RESOURCE_FIELDS)
                for name in self.containers:
                    cpu_text, used_text, limit_text, pids_text = parsed[name]
                    used_bytes = parse_memory_bytes(used_text)
                    limit_bytes = parse_memory_bytes(limit_text)
                    row = {
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
                    print(f"[resources {phase} {timestamp}] {name}: CPU {row['cpu_percent']:.1f}%, "
                          f"RAM {row['memory_used_mib']:.1f} MiB, PIDs {row['pids']}", flush=True)
        except Exception as error:  # sampling must never abort the load run
            message = f"resource sample failed: {error}"
            self.errors.append(message)
            print(f"[resources] WARNING: {message}", flush=True)

    def start(self) -> None:
        self.output.parent.mkdir(parents=True, exist_ok=True)
        with self.output.open("w", newline="", encoding="utf-8") as output_file:
            csv.DictWriter(output_file, fieldnames=RESOURCE_FIELDS).writeheader()
        self.sample("baseline")
        self.thread = threading.Thread(target=self._loop, name="docker-resource-sampler", daemon=True)
        self.thread.start()

    def _loop(self) -> None:
        next_sample = time.monotonic() + self.interval
        while not self.stop.wait(max(0.0, next_sample - time.monotonic())):
            self.sample("during")
            next_sample += self.interval
            now = time.monotonic()
            while next_sample <= now:
                next_sample += self.interval

    def finish(self) -> None:
        self.stop.set()
        if self.thread:
            self.thread.join()

    def summary(self) -> dict[str, Any]:
        per_container: dict[str, Any] = {}
        for name in self.containers:
            samples = [row for row in self.rows if row["phase"] == "during" and row["container"] == name]
            if not samples:
                per_container[name] = None
                continue
            cpus = [float(row["cpu_percent"]) for row in samples]
            memory = [float(row["memory_used_mib"]) for row in samples]
            per_container[name] = {
                "samples": len(samples),
                "cpu_percent_avg": round(statistics.fmean(cpus), 2),
                "cpu_percent_max": round(max(cpus), 2),
                "memory_mib_avg": round(statistics.fmean(memory), 2),
                "memory_mib_max": round(max(memory), 2),
                "memory_limit_mib": samples[-1]["memory_limit_mib"],
            }
        return {"interval_seconds": self.interval, "containers": per_container,
                "warnings": len(self.errors), "warning_samples": self.errors[:5]}


# --------------------------------------------------------------------------- readings

@dataclass
class Reading:
    device_id: str
    channel: str
    sequence: int
    phase: str                 # "warmup" or "measured"
    captured_at_ms: int        # epoch ms; becomes telemetry.recorded_at
    value: float
    sent_at: float | None = None
    publish_rc: int | None = None
    mid: int | None = None
    puback_at: float | None = None
    db_seen_at: float | None = None

    @property
    def key(self) -> tuple[str, str, int]:
        return self.device_id, self.channel, self.captured_at_ms

    @property
    def captured_at_text(self) -> str:
        return utc_iso(self.captured_at_ms / 1000)

    @property
    def puback_ms(self) -> float | None:
        if self.sent_at is None or self.puback_at is None:
            return None
        return max(0.0, (self.puback_at - self.sent_at) * 1000)

    @property
    def db_latency_ms(self) -> float | None:
        if self.sent_at is None or self.db_seen_at is None:
            return None
        return max(0.0, (self.db_seen_at - self.sent_at) * 1000)

    @property
    def status(self) -> str:
        if self.publish_rc not in (None, mqtt.MQTT_ERR_SUCCESS):
            return "publish_failed"
        if self.db_seen_at is not None:
            return "stored"
        return "missing"


@dataclass
class Device:
    device_id: str
    client: mqtt.Client
    lock: threading.Lock = field(default_factory=threading.Lock)
    acks: dict[int, float] = field(default_factory=dict)
    disconnects: int = 0


# --------------------------------------------------------------------------- database

class TelemetryWatcher:
    """Polls the telemetry table and stamps each sent reading with the time it became visible.

    A reading counts as visible at the start of the first poll whose snapshot
    contains it, so the latency resolution is one poll interval. All timing uses
    the load generator's clock; the database clock is never compared.
    """

    QUERY = """
        SELECT s.device_id::text, s.channel_name, t.recorded_at
        FROM telemetry t
        JOIN sensor s ON s.id = t.sensor_id
        WHERE s.device_id = ANY(%s::uuid[])
          AND t.recorded_at >= %s AND t.recorded_at <= %s
    """

    def __init__(self, dsn: str, device_ids: list[str], interval: float):
        import psycopg  # imported lazily so --help works without the driver

        self.connection = psycopg.connect(dsn, autocommit=True)
        self.device_ids = device_ids
        self.interval = interval
        self.lock = threading.Lock()
        self.pending: dict[tuple[str, str, int], Reading] = {}
        self.window_start_ms: int | None = None
        self.window_end_ms: int | None = None
        self.seen: set[tuple[str, str, int]] = set()
        self.unexpected: set[tuple[str, str, int]] = set()
        self.polls = 0
        self.errors: list[str] = []
        self.stop = threading.Event()
        self.thread: threading.Thread | None = None

    def expect(self, reading: Reading) -> None:
        with self.lock:
            self.pending[reading.key] = reading
            self.window_start_ms = min(self.window_start_ms or reading.captured_at_ms, reading.captured_at_ms)
            self.window_end_ms = max(self.window_end_ms or reading.captured_at_ms, reading.captured_at_ms)

    def forget(self, reading: Reading) -> None:
        """Stop waiting for a reading whose publish failed locally; it is reported as a publish failure."""
        with self.lock:
            self.pending.pop(reading.key, None)

    def pending_count(self) -> int:
        with self.lock:
            return len(self.pending)

    def poll_once(self) -> None:
        with self.lock:
            if not self.pending:
                return
            low = min(reading.captured_at_ms for reading in self.pending.values())
            high = self.window_end_ms or low
        poll_started = time.time()
        try:
            with self.connection.cursor() as cursor:
                cursor.execute(self.QUERY, (
                    self.device_ids,
                    datetime.fromtimestamp(low / 1000, timezone.utc),
                    datetime.fromtimestamp(high / 1000, timezone.utc),
                ))
                rows = cursor.fetchall()
        except Exception as error:
            if len(self.errors) < 20:
                self.errors.append(str(error))
            print(f"[database] WARNING: telemetry poll failed: {error}", flush=True)
            return
        self.polls += 1
        with self.lock:
            for device_id, channel, recorded_at in rows:
                key = (device_id, channel, round(recorded_at.timestamp() * 1000))
                if key in self.seen:
                    continue
                reading = self.pending.pop(key, None)
                if reading is not None:
                    reading.db_seen_at = poll_started
                    self.seen.add(key)
                elif self.window_start_ms is not None and key[2] >= self.window_start_ms:
                    self.unexpected.add(key)

    def start(self) -> None:
        self.thread = threading.Thread(target=self._loop, name="telemetry-watcher", daemon=True)
        self.thread.start()

    def _loop(self) -> None:
        while not self.stop.wait(self.interval):
            self.poll_once()

    def drain(self, timeout: float) -> None:
        """Keep polling after publishing stops until every reading is seen or the timeout passes."""
        deadline = time.monotonic() + timeout
        while self.pending_count() and time.monotonic() < deadline:
            time.sleep(self.interval)
        self.stop.set()
        if self.thread:
            self.thread.join()
        self.poll_once()
        self.connection.close()


# --------------------------------------------------------------------------- run

def connect_fleet(device_ids: list[str], args: argparse.Namespace, password: str) -> list[Device]:
    devices: list[Device] = []
    try:
        for device_id in device_ids:
            connack = threading.Event()
            outcome: list[str | None] = []
            client = mqtt.Client(
                mqtt.CallbackAPIVersion.VERSION2,
                client_id=f"openroots-load-{device_id}",
                protocol=mqtt.MQTTv311,
                clean_session=True,
            )
            client.username_pw_set(device_id, password)
            client.max_inflight_messages_set(100)
            device = Device(device_id, client)

            def on_connect(_client, _userdata, _flags, reason_code, _properties, event=connack, result=outcome):
                result.append(str(reason_code) if reason_code.is_failure else None)
                event.set()

            def on_publish(_client, _userdata, mid, _reason_code, _properties, owner=device):
                with owner.lock:
                    owner.acks.setdefault(mid, time.time())

            def on_disconnect(_client, _userdata, _flags, _reason_code, _properties, owner=device):
                with owner.lock:
                    owner.disconnects += 1

            client.on_connect = on_connect
            client.on_publish = on_publish
            client.on_disconnect = on_disconnect
            client.connect(args.host, args.port, keepalive=30)
            client.loop_start()
            devices.append(device)
            if not connack.wait(args.connect_timeout):
                raise TimeoutError(f"{device_id}: timed out waiting for broker CONNACK")
            if outcome and outcome[-1]:
                raise ConnectionError(f"{device_id}: broker rejected MQTT connection ({outcome[-1]})")
    except Exception:
        disconnect_fleet(devices)
        raise
    return devices


def disconnect_fleet(devices: list[Device]) -> None:
    for device in devices:
        device.client.disconnect()
        device.client.loop_stop()


def schedule_offsets(count: int, period: float, mode: str) -> list[float]:
    if mode == "synchronized":
        return [0.0] * count
    return [index * period / count for index in range(count)]


def publish_fleet(devices: list[Device], args: argparse.Namespace, channels: list[str],
                  watcher: TelemetryWatcher | None, stop: threading.Event) -> tuple[list[Reading], float, float]:
    """Publish on a fixed schedule without waiting for acknowledgements.

    Each message's due time is computed up front, so a slow broker or backend
    cannot stretch the interval or the run. ``capturedAt`` is the due time, which
    makes every (device, channel, capturedAt) unique within and across runs.
    """
    total = args.warmup + args.duration
    offsets = schedule_offsets(len(devices), args.period, args.schedule)
    start_wall = time.time() + 1.0
    start_wall = float(int(start_wall * 1000)) / 1000  # whole milliseconds
    start_mono = time.monotonic() + (start_wall - time.time())
    queue: list[tuple[float, int, int]] = []  # (offset_seconds, device_index, sequence)
    for index, offset in enumerate(offsets):
        if offset < total:
            heapq.heappush(queue, (offset, index, 0))

    readings: list[Reading] = []
    while queue and not stop.is_set():
        offset, index, sequence = heapq.heappop(queue)
        wait = start_mono + offset - time.monotonic()
        try:
            if wait > 0 and stop.wait(wait):
                break
        except KeyboardInterrupt:
            print("Stopping early; reporting what was sent so far...")
            stop.set()
            break
        device = devices[index]
        captured_at_ms = int(round((start_wall + offset) * 1000))
        phase = "warmup" if offset < args.warmup else "measured"
        message_readings = [
            Reading(device.device_id, channel, sequence, phase, captured_at_ms,
                    round(20.0 + (sequence % 100) / 10.0 + channel_index, 2))
            for channel_index, channel in enumerate(channels)
        ]
        # The topic identifies the device; the MQTT contract forbids deviceId in the body.
        body = json.dumps({
            "capturedAt": message_readings[0].captured_at_text,
            "payload": {reading.channel: reading.value for reading in message_readings},
        })
        if watcher is not None:
            for reading in message_readings:
                watcher.expect(reading)
        sent_at = time.time()
        info = device.client.publish(f"openroots/v1/devices/{device.device_id}/telemetry",
                                     body, qos=args.qos, retain=False)
        for reading in message_readings:
            reading.sent_at = sent_at
            reading.publish_rc = info.rc
            reading.mid = info.mid
            if info.rc != mqtt.MQTT_ERR_SUCCESS and watcher is not None:
                watcher.forget(reading)
        readings.extend(message_readings)
        next_offset = offset + args.period
        if next_offset < total:
            heapq.heappush(queue, (next_offset, index, sequence + 1))
    return readings, start_wall, time.time() - start_wall


def collect_acks(devices: list[Device], readings: list[Reading], timeout: float) -> None:
    by_device = {device.device_id: device for device in devices}
    deadline = time.monotonic() + timeout
    while True:
        outstanding = 0
        for reading in readings:
            if reading.puback_at is None and reading.publish_rc == mqtt.MQTT_ERR_SUCCESS:
                device = by_device[reading.device_id]
                with device.lock:
                    reading.puback_at = device.acks.get(reading.mid)
                outstanding += reading.puback_at is None
        if not outstanding or time.monotonic() >= deadline:
            return
        time.sleep(0.2)


def build_summary(args: argparse.Namespace, channels: list[str], devices: list[Device],
                  readings: list[Reading], start_wall: float, publish_seconds: float,
                  watcher: TelemetryWatcher | None, sampler: ResourceSampler) -> dict[str, Any]:
    measured = [reading for reading in readings if reading.phase == "measured"]
    messages = {(reading.device_id, reading.sequence): reading for reading in measured}
    message_list = list(messages.values())
    publish_failed = sum(1 for reading in message_list if reading.status == "publish_failed")
    acked = sum(1 for reading in message_list if reading.puback_at is not None)
    stored = [reading for reading in measured if reading.db_seen_at is not None]
    missing = [reading for reading in measured if reading.status == "missing"]
    puback = [reading.puback_ms for reading in message_list if reading.puback_ms is not None]
    db_latency = [reading.db_latency_ms for reading in stored if reading.db_latency_ms is not None]
    measured_seconds = max(args.duration, 0.001)

    failures: list[str] = []
    if publish_failed > args.max_publish_failures:
        failures.append(f"{publish_failed} publishes failed (allowed {args.max_publish_failures})")
    unacked = len(message_list) - acked - publish_failed
    if args.qos > 0 and unacked > args.max_publish_failures:
        failures.append(f"{unacked} publishes were never acknowledged (allowed {args.max_publish_failures})")
    if watcher is not None:
        if len(missing) > args.max_missing:
            failures.append(f"{len(missing)} readings missing from the database (allowed {args.max_missing})")
        p95 = percentile(db_latency, 0.95)
        p99 = percentile(db_latency, 0.99)
        if args.p95_ms is not None and p95 is not None and p95 > args.p95_ms:
            failures.append(f"publish-to-database p95 {p95:.0f} ms exceeds {args.p95_ms:g} ms")
        if args.p99_ms is not None and p99 is not None and p99 > args.p99_ms:
            failures.append(f"publish-to-database p99 {p99:.0f} ms exceeds {args.p99_ms:g} ms")
        if watcher.errors:
            failures.append(f"{len(watcher.errors)} database polls failed")
    if sampler.errors:
        failures.append(f"{len(sampler.errors)} resource samples failed")
    if not any(row["phase"] == "during" for row in sampler.rows):
        failures.append("no in-load resource samples were captured")

    return {
        "label": args.label,
        "run_id": args.run_id,
        "result": "fail" if failures else "pass",
        "failures": failures,
        "git": git_revision(),
        "started_at_utc": utc_iso(start_wall),
        "settings": {
            "devices": len(devices),
            "period_seconds": args.period,
            "warmup_seconds": args.warmup,
            "duration_seconds": args.duration,
            "schedule": args.schedule,
            "qos": args.qos,
            "channels": channels,
            "broker": f"{args.host}:{args.port}",
            "db_poll_interval_seconds": args.db_poll_interval if watcher else None,
            "drain_timeout_seconds": args.drain_timeout,
            "containers": args.containers,
        },
        "thresholds": {
            "max_missing": args.max_missing,
            "max_publish_failures": args.max_publish_failures,
            "p95_ms": args.p95_ms,
            "p99_ms": args.p99_ms,
        },
        "publish": {
            "connections": len(devices),
            "disconnects": sum(device.disconnects for device in devices),
            "warmup_messages": len({(r.device_id, r.sequence) for r in readings if r.phase == "warmup"}),
            "attempted": len(message_list),
            "acknowledged": acked,
            "failed": publish_failed,
            "unacknowledged": unacked if args.qos > 0 else None,
            "actual_publish_seconds": round(publish_seconds, 2),
            "attempted_rate_per_second": round(len(message_list) / measured_seconds, 2),
            "puback_ms": distribution(puback),
        },
        "persistence": None if watcher is None else {
            "expected_readings": len(measured),
            "stored": len(stored),
            "missing": len(missing),
            "unexpected_rows": len(watcher.unexpected),
            "stored_rate_per_second": round(len(stored) / measured_seconds, 2),
            "db_polls": watcher.polls,
            "publish_to_db_ms": distribution(db_latency),
            "note": "Duplicates cannot exist: telemetry is keyed by (sensor_id, recorded_at). "
                    "unexpected_rows counts rows for the fleet in the run window that this run did not send.",
        },
        "resources": sampler.summary(),
    }


def write_readings(path: Path, readings: list[Reading]) -> None:
    with path.open("w", newline="", encoding="utf-8") as output_file:
        writer = csv.DictWriter(output_file, fieldnames=READING_FIELDS)
        writer.writeheader()
        for reading in readings:
            writer.writerow({
                "device_id": reading.device_id,
                "channel": reading.channel,
                "sequence": reading.sequence,
                "phase": reading.phase,
                "captured_at": reading.captured_at_text,
                "sent_at_utc": utc_iso(reading.sent_at) if reading.sent_at else "",
                "publish_rc": reading.publish_rc,
                "puback_ms": "" if reading.puback_ms is None else round(reading.puback_ms, 2),
                "db_seen_at_utc": utc_iso(reading.db_seen_at) if reading.db_seen_at else "",
                "db_latency_ms": "" if reading.db_latency_ms is None else round(reading.db_latency_ms, 2),
                "status": reading.status,
            })


def format_ms(stats: dict[str, Any]) -> str:
    if not stats["count"]:
        return "n/a"
    return f"p50 {stats['p50']:.1f}, p95 {stats['p95']:.1f}, p99 {stats['p99']:.1f}, max {stats['max']:.1f}"


def render_markdown(summary: dict[str, Any]) -> str:
    settings, publish, persistence = summary["settings"], summary["publish"], summary["persistence"]
    lines = [
        f"## MQTT load test: {summary['label']} ({summary['run_id']}) - {summary['result'].upper()}",
        "",
        f"{settings['devices']} devices, one message every {settings['period_seconds']:g}s, "
        f"{settings['schedule']} schedule, QoS {settings['qos']}, channels {', '.join(settings['channels'])}, "
        f"{settings['warmup_seconds']:g}s warm-up + {settings['duration_seconds']:g}s measured. "
        f"Commit `{(summary['git']['commit'] or 'unknown')[:10]}` on `{summary['git']['branch']}`.",
        "",
        "| Metric | Value |",
        "|---|---|",
        f"| Connections / disconnects | {publish['connections']} / {publish['disconnects']} |",
        f"| Messages attempted / acknowledged / failed | {publish['attempted']} / {publish['acknowledged']} / {publish['failed']} |",
        f"| Attempted rate | {publish['attempted_rate_per_second']} msg/s |",
        f"| PUBACK ms | {format_ms(publish['puback_ms'])} |",
    ]
    if persistence:
        lines += [
            f"| Readings expected / stored / missing | {persistence['expected_readings']} / {persistence['stored']} / {persistence['missing']} |",
            f"| Unexpected rows | {persistence['unexpected_rows']} |",
            f"| Publish-to-database ms | {format_ms(persistence['publish_to_db_ms'])} |",
        ]
    for name, stats in summary["resources"]["containers"].items():
        if stats:
            lines.append(f"| {name} CPU avg/max, RAM avg/max | {stats['cpu_percent_avg']}% / {stats['cpu_percent_max']}%, "
                         f"{stats['memory_mib_avg']} / {stats['memory_mib_max']} MiB |")
    if summary["failures"]:
        lines += ["", "**Failures**", ""] + [f"- {failure}" for failure in summary["failures"]]
    return "\n".join(lines) + "\n"


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--devices", required=True, help="text file with one registered device UUID per line")
    parser.add_argument("--label", default=os.getenv("OPENROOTS_LOAD_LABEL", "unlabeled"),
                        help="implementation under test, e.g. spring or go; names the result folder")
    parser.add_argument("--run-id", default=datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ"),
                        help="identifier for this run (default: UTC start time)")
    parser.add_argument("--output-dir", default="load-test-results", help="parent folder for run results")
    parser.add_argument("--host", default=os.getenv("OPENROOTS_MQTT_HOST", "127.0.0.1"))
    parser.add_argument("--port", type=int, default=int(os.getenv("OPENROOTS_MQTT_PORT", "1883")))
    parser.add_argument("--period", type=float, default=10.0, help="seconds between each device's messages")
    parser.add_argument("--duration", type=float, default=120.0, help="measured test duration in seconds")
    parser.add_argument("--warmup", type=float, default=0.0,
                        help="seconds of traffic before measurement starts; excluded from stats and thresholds")
    parser.add_argument("--schedule", choices=("synchronized", "staggered"), default="synchronized",
                        help="synchronized: every device publishes at once; staggered: spread across the period")
    parser.add_argument("--connect-timeout", type=float, default=15.0, help="seconds to wait for each CONNACK")
    parser.add_argument("--ack-timeout", type=float, default=15.0,
                        help="seconds to wait for outstanding PUBACKs after publishing stops")
    parser.add_argument("--qos", type=int, choices=(0, 1, 2), default=1)
    parser.add_argument("--channel", action="append", dest="channels",
                        help="registered numeric sensor channel; repeat to send multiple readings")
    parser.add_argument("--containers", default=os.getenv("OPENROOTS_LOAD_CONTAINERS", ",".join(DEFAULT_CONTAINERS)),
                        help="comma-separated Docker containers to sample, e.g. add openroots-ingestion for Go")
    parser.add_argument("--resource-sample-interval", type=float, default=5.0,
                        help="seconds between Docker CPU/RAM samples")
    parser.add_argument("--db-dsn", default=os.getenv("OPENROOTS_LOAD_DB_DSN"),
                        help="PostgreSQL DSN used to verify persistence (env OPENROOTS_LOAD_DB_DSN)")
    parser.add_argument("--skip-db-verify", action="store_true",
                        help="publish only; report broker acknowledgements without checking the database")
    parser.add_argument("--db-poll-interval", type=float, default=0.5, help="seconds between telemetry polls")
    parser.add_argument("--drain-timeout", type=float, default=30.0,
                        help="seconds to keep polling for missing readings after publishing stops")
    parser.add_argument("--max-missing", type=int, default=0, help="readings allowed to be missing")
    parser.add_argument("--max-publish-failures", type=int, default=0,
                        help="failed or unacknowledged publishes allowed")
    parser.add_argument("--p95-ms", type=float, default=2000.0, help="publish-to-database p95 limit; 0 disables")
    parser.add_argument("--p99-ms", type=float, default=5000.0, help="publish-to-database p99 limit; 0 disables")
    args = parser.parse_args()

    for name in ("period", "duration", "connect_timeout", "ack_timeout", "resource_sample_interval",
                 "db_poll_interval"):
        if getattr(args, name) <= 0:
            parser.error(f"--{name.replace('_', '-')} must be greater than zero")
    for name in ("warmup", "drain_timeout"):
        if getattr(args, name) < 0:
            parser.error(f"--{name.replace('_', '-')} must not be negative")
    if not re.fullmatch(r"[A-Za-z0-9._-]+", args.label) or not re.fullmatch(r"[A-Za-z0-9._-]+", args.run_id):
        parser.error("--label and --run-id may contain only letters, digits, '.', '_' and '-'")
    if not args.skip_db_verify and not args.db_dsn:
        parser.error("set --db-dsn or OPENROOTS_LOAD_DB_DSN to verify persistence (or pass --skip-db-verify)")
    args.containers = [name.strip() for name in args.containers.split(",") if name.strip()]
    if not args.containers:
        parser.error("--containers must name at least one container")
    args.p95_ms = args.p95_ms or None
    args.p99_ms = args.p99_ms or None
    return args


def main() -> int:
    load_local_env()
    args = parse_args()
    device_ids = read_device_ids(args.devices)
    channels = args.channels or ["temperature"]
    password = os.getenv("OPENROOTS_MQTT_DEVICE_PASSWORD")
    if not password:
        print("Set OPENROOTS_MQTT_DEVICE_PASSWORD to the password provisioned for each test device.", file=sys.stderr)
        return 2

    run_dir = Path(args.output_dir) / f"{args.label}-{args.run_id}"
    run_dir.mkdir(parents=True, exist_ok=True)
    print(f"Run {args.label}-{args.run_id}: results in {run_dir}")

    print(f"Connecting {len(device_ids)} devices to {args.host}:{args.port} (QoS {args.qos})...")
    try:
        devices = connect_fleet(device_ids, args, password)
    except Exception as error:
        print(f"Connection failed: {error}", file=sys.stderr)
        return 2
    print(f"Broker connections accepted: {len(devices)}/{len(device_ids)}")

    watcher = None
    if not args.skip_db_verify:
        try:
            watcher = TelemetryWatcher(args.db_dsn, device_ids, args.db_poll_interval)
        except Exception as error:
            disconnect_fleet(devices)
            print(f"Could not connect to the database for verification: {error}", file=sys.stderr)
            return 2
        watcher.start()

    sampler = ResourceSampler(run_dir / "resource-samples.csv", args.resource_sample_interval, args.containers)
    sampler.start()

    stop = threading.Event()
    print(f"Publishing {', '.join(channels)} every {args.period:g}s ({args.schedule}) for "
          f"{args.warmup:g}s warm-up + {args.duration:g}s measured...")
    readings, start_wall, publish_seconds = publish_fleet(devices, args, channels, watcher, stop)
    collect_acks(devices, readings, args.ack_timeout)
    sampler.finish()
    disconnect_fleet(devices)
    if watcher is not None:
        print(f"Publishing finished; waiting up to {args.drain_timeout:g}s for "
              f"{watcher.pending_count()} readings still not visible in the database...")
        watcher.drain(args.drain_timeout)

    summary = build_summary(args, channels, devices, readings, start_wall, publish_seconds, watcher, sampler)
    write_readings(run_dir / "readings.csv", readings)
    (run_dir / "summary.json").write_text(json.dumps(summary, indent=2) + "\n", encoding="utf-8")
    markdown = render_markdown(summary)
    (run_dir / "summary.md").write_text(markdown, encoding="utf-8")
    print("\n" + markdown)
    step_summary = os.getenv("GITHUB_STEP_SUMMARY")
    if step_summary:
        with open(step_summary, "a", encoding="utf-8") as output_file:
            output_file.write(markdown + "\n")
    if watcher is None:
        print("Database verification skipped: PUBACK confirms broker receipt only, not persistence.")
    return 1 if summary["failures"] else 0


if __name__ == "__main__":
    raise SystemExit(main())
