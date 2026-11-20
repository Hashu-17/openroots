#!/usr/bin/env python3
"""Create/reuse registered test devices and their Mosquitto publisher accounts."""

from __future__ import annotations

import argparse
import json
import os
import subprocess
import sys
import urllib.error
import urllib.request
from pathlib import Path
from typing import Any
from uuid import UUID


DEFAULT_ENV_FILE = Path(__file__).resolve().parent / ".env"
DEFAULT_DEVICE_FILE = Path(__file__).resolve().parent / "load-devices.txt"


def read_env_file(path: Path) -> dict[str, str]:
    values: dict[str, str] = {}
    if not path.is_file():
        return values
    for raw_line in path.read_text(encoding="utf-8").splitlines():
        line = raw_line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        value = value.strip()
        if len(value) >= 2 and value[0] == value[-1] and value[0] in "\"'":
            value = value[1:-1]
        values[key.strip()] = value
    return values


def api_json(base_url: str, path: str, method: str = "GET", body: dict[str, Any] | None = None) -> dict[str, Any]:
    data = json.dumps(body).encode("utf-8") if body is not None else None
    request = urllib.request.Request(
        f"{base_url.rstrip('/')}{path}",
        data=data,
        method=method,
        headers={"Content-Type": "application/json"},
    )
    try:
        with urllib.request.urlopen(request, timeout=30) as response:
            return json.load(response)
    except urllib.error.HTTPError as error:
        details = error.read().decode("utf-8", errors="replace")
        raise RuntimeError(f"OpenRoots API returned HTTP {error.code}: {details}") from error
    except urllib.error.URLError as error:
        raise RuntimeError(f"Could not reach OpenRoots API: {error.reason}") from error


def project_for_device(base_url: str, device_id: str) -> str:
    try:
        wanted_id = str(UUID(device_id))
    except ValueError as error:
        raise RuntimeError("MQTT_SMOKE_DEVICE_ID must be a valid UUID to find the test project") from error
    projects = api_json(base_url, "/api/v1/projects").get("data", [])
    for project in projects:
        project_id = project.get("id")
        if not project_id:
            continue
        devices = api_json(base_url, f"/api/v1/projects/{project_id}/devices").get("data", [])
        if any(device.get("id") == wanted_id for device in devices):
            return str(UUID(project_id))
    raise RuntimeError(
        f"Could not find smoke-test device {wanted_id} in any project. "
        "Pass --project-id explicitly or check the MQTT_SMOKE_DEVICE_ID secret."
    )


def provision_broker_client(device_id: str, password: str) -> None:
    # Send the password over stdin, rather than placing it in the docker command
    # line where local process-listing tools could expose it.
    script = r'''set -eu
IFS= read -r DEVICE_PASSWORD

dynsec() {
  OUTPUT=$(mosquitto_ctrl -h 127.0.0.1 -u "$MQTT_ADMIN_USERNAME" -P "$MQTT_ADMIN_PASSWORD" dynsec "$@" 2>&1) || {
    printf '%s\n' "$OUTPUT" >&2
    return 1
  }
  if printf '%s\n' "$OUTPUT" | grep -Fq 'Error:'; then
    printf '%s\n' "$OUTPUT" >&2
    return 1
  fi
  printf '%s\n' "$OUTPUT"
}

get_client() {
  mosquitto_ctrl -h 127.0.0.1 -u "$MQTT_ADMIN_USERNAME" -P "$MQTT_ADMIN_PASSWORD" dynsec getClient "$1" 2>&1 || true
}

DETAILS=$(get_client "$1")
if printf '%s\n' "$DETAILS" | grep -Fq 'Error: Client not found'; then
  dynsec createClient "$1" -p "$DEVICE_PASSWORD" >/dev/null
  dynsec addClientRole "$1" device-publisher >/dev/null
elif printf '%s\n' "$DETAILS" | grep -Fq 'Username:'; then
  dynsec setClientPassword "$1" "$DEVICE_PASSWORD" >/dev/null
  if ! printf '%s\n' "$DETAILS" | grep -Fq 'device-publisher'; then
    dynsec addClientRole "$1" device-publisher >/dev/null
  fi
else
  printf '%s\n' "$DETAILS" >&2
  echo "Could not determine whether broker client $1 exists" >&2
  exit 1
fi

DETAILS=$(dynsec getClient "$1")
printf '%s\n' "$DETAILS" | grep -Fq 'Username:'
printf '%s\n' "$DETAILS" | grep -Fq 'device-publisher'
'''
    result = subprocess.run(
        ["docker", "exec", "-i", "openroots-mosquitto", "sh", "-c", script, "sh", device_id],
        # Use bytes: text-mode stdin translates ``\n`` to ``\r\n`` on Windows.
        # The container's ``read`` would then retain ``\r`` in the password,
        # so the broker would store a different password than Paho later uses.
        input=password.encode("utf-8") + b"\n",
        capture_output=True,
        check=False,
    )
    response = (result.stdout + result.stderr).decode("utf-8", errors="replace")
    if result.returncode or "Error:" in response:
        detail = response.strip() or "docker compose exec failed"
        raise RuntimeError(f"Could not provision broker account for {device_id}: {detail}")


def broker_diagnostics(device_id: str) -> None:
    """Show which local Docker broker and dynsec client the load test will use."""
    context = subprocess.run(
        ["docker", "context", "show"], text=True, capture_output=True, check=False
    )
    if context.returncode == 0:
        print(f"Docker context: {context.stdout.strip()}")

    port = subprocess.run(
        ["docker", "port", "openroots-mosquitto", "1883/tcp"],
        text=True, capture_output=True, check=False,
    )
    if port.returncode == 0:
        print("Mosquitto published endpoint(s):")
        print(port.stdout.strip() or "  none")
    else:
        detail = port.stderr.strip() or port.stdout.strip()
        raise RuntimeError(f"Could not inspect the Mosquitto port mapping: {detail}")

    command = r'''mosquitto_ctrl -h 127.0.0.1 -u "$MQTT_ADMIN_USERNAME" -P "$MQTT_ADMIN_PASSWORD" dynsec getClient "$1"'''
    result = subprocess.run(
        ["docker", "exec", "openroots-mosquitto", "sh", "-c", command, "sh", device_id],
        text=True, capture_output=True, check=False,
    )
    response = f"{result.stdout}{result.stderr}"
    if result.returncode or "Error:" in response:
        detail = response.strip()
        raise RuntimeError(f"Broker account verification failed for {device_id}: {detail}")
    print(f"Broker account details for first device ({device_id}):")
    print(response.strip())


def save_device_file(path: Path, device_ids: list[str]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_suffix(path.suffix + ".tmp")
    temporary.write_text("\n".join(device_ids) + "\n", encoding="utf-8")
    temporary.replace(path)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--project-id", help="existing OpenRoots project UUID; inferred from the smoke device if omitted")
    parser.add_argument("--count", type=int, default=100, help="number of virtual devices to create/reuse")
    parser.add_argument("--api-url", help="OpenRoots backend URL (defaults to tests/support/mqtt/.env or localhost)")
    parser.add_argument("--env-file", type=Path, default=DEFAULT_ENV_FILE,
                        help="ignored local env file containing OPENROOTS_MQTT_DEVICE_PASSWORD")
    parser.add_argument("--output", type=Path, default=DEFAULT_DEVICE_FILE,
                        help="UUID list consumed by mqtt_load_test.py")
    parser.add_argument("--name-prefix", default="openroots-load-test",
                        help="stable names make repeated setup runs reuse the same devices")
    parser.add_argument("--channel", default="temperature")
    parser.add_argument("--unit", default="C")
    args = parser.parse_args()

    if args.count < 1:
        parser.error("--count must be at least 1")
    if not args.name_prefix.strip() or not args.channel.strip():
        parser.error("--name-prefix and --channel must not be blank")

    env_file_values = read_env_file(args.env_file)
    password = os.getenv("OPENROOTS_MQTT_DEVICE_PASSWORD") or env_file_values.get("OPENROOTS_MQTT_DEVICE_PASSWORD")
    if not password:
        parser.error(f"set OPENROOTS_MQTT_DEVICE_PASSWORD in the environment or {args.env_file}")
    if "\r" in password or "\n" in password:
        parser.error("OPENROOTS_MQTT_DEVICE_PASSWORD must be a single-line value")

    api_url = args.api_url or os.getenv("OPENROOTS_API_URL") or env_file_values.get(
        "OPENROOTS_API_URL", "http://127.0.0.1:8080"
    )
    requested_project_id = (args.project_id or os.getenv("OPENROOTS_PROJECT_ID") or "").strip()
    if requested_project_id:
        try:
            project_id = str(UUID(requested_project_id))
        except ValueError:
            parser.error("--project-id must be a valid UUID")
    else:
        reference_device_id = os.getenv("MQTT_SMOKE_DEVICE_ID")
        if not reference_device_id:
            parser.error("pass --project-id or set MQTT_SMOKE_DEVICE_ID so the project can be found")
        project_id = project_for_device(api_url, reference_device_id)
    devices_path = f"/api/v1/projects/{project_id}/devices"
    print(f"Checking existing test devices in project {project_id}...")
    devices_response = api_json(api_url, devices_path)
    existing = {
        device["name"]: device
        for device in devices_response.get("data", [])
        if isinstance(device, dict) and isinstance(device.get("name"), str)
    }

    device_ids: list[str] = []
    for index in range(1, args.count + 1):
        name = f"{args.name_prefix}-{index:03d}"
        device = existing.get(name)
        if device is None:
            created = api_json(api_url, devices_path, "POST", {
                "name": name,
                "type": "mqtt-load-test",
                "sensors": [{"channelName": args.channel, "unit": args.unit}],
            })
            device = created.get("data", {})
            print(f"Registered {name} ({device.get('id', 'unknown ID')})")
        else:
            sensor_names = {sensor.get("channelName") for sensor in device.get("sensors", [])}
            if args.channel not in sensor_names:
                raise RuntimeError(
                    f"Existing device {name} does not have sensor '{args.channel}'. "
                    "Choose another --name-prefix or delete/recreate that test device."
                )
            print(f"Reusing {name} ({device['id']})")

        device_id = device.get("id")
        if not device_id:
            raise RuntimeError(f"OpenRoots API did not return an ID for {name}")
        device_ids.append(device_id)
        # Keep the manifest recoverable if a later broker-account command fails.
        save_device_file(args.output, device_ids)

    for index, device_id in enumerate(device_ids, start=1):
        provision_broker_client(device_id, password)
        if index == 1 or index == len(device_ids) or index % 10 == 0:
            print(f"Provisioned MQTT accounts: {index}/{len(device_ids)}")

    broker_diagnostics(device_ids[0])
    print(f"\nReady: {len(device_ids)} test devices.")
    print(f"Device UUIDs: {args.output}")
    print("The MQTT load test password comes from OPENROOTS_MQTT_DEVICE_PASSWORD.")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (RuntimeError, OSError, subprocess.SubprocessError) as error:
        print(f"Fleet setup failed: {error}", file=sys.stderr)
        raise SystemExit(1)
