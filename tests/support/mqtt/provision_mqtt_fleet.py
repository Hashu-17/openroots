#!/usr/bin/env python3
"""Create/reuse registered load-test devices and issue each one a temporary credential.

Signs in as the test user (OWNER or ADMIN of the project), registers or reuses devices
by name, and issues each one a credential that expires on its own. The backend syncs
each credential to the broker. Writes two files:

- the device file (``uuid secret`` per line) read by mqtt_load_test.py. It holds
  secrets: ``--revoke`` deletes it.
- the credential manifest (ids only, no secrets) that ``--revoke`` uses to revoke them.
"""

from __future__ import annotations

import argparse
import json
import os
import subprocess
import sys
from datetime import timedelta
from pathlib import Path
from uuid import UUID

sys.path.insert(0, str(Path(__file__).resolve().parent))
from openroots_api import OpenRootsApi, load_local_env  # noqa: E402

HERE = Path(__file__).resolve().parent
DEFAULT_ENV_FILE = HERE / ".env"
DEFAULT_DEVICE_FILE = HERE / "load-devices.txt"
DEFAULT_MANIFEST = HERE / "load-credentials.json"
# Margin past the planned run so setup, slow connects and the drain don't hit the expiry.
EXPIRY_MARGIN = timedelta(minutes=30)


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


def write_atomically(path: Path, text: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_suffix(path.suffix + ".tmp")
    temporary.write_text(text, encoding="utf-8")
    temporary.replace(path)


def save_manifest(path: Path, project_id: str, issued: list[dict[str, str]]) -> None:
    write_atomically(path, json.dumps({"projectId": project_id, "credentials": issued}, indent=2) + "\n")


def save_device_file(path: Path, secrets: list[tuple[str, str]]) -> None:
    write_atomically(path, "".join(f"{device_id} {secret}\n" for device_id, secret in secrets))


def revoke(api: OpenRootsApi, manifest_path: Path, device_file: Path) -> int:
    """Revoke every credential in the manifest, then delete the manifest and device file."""
    failures = 0
    if manifest_path.is_file():
        manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
        project_id = manifest["projectId"]
        credentials = manifest.get("credentials", [])
        for entry in credentials:
            try:
                api.revoke_credential(project_id, entry["deviceId"], entry["credentialId"])
            except RuntimeError as error:
                failures += 1
                print(f"Could not revoke credential for {entry['deviceId']}: {error}", file=sys.stderr)
        print(f"Revoked {len(credentials) - failures}/{len(credentials)} temporary credentials")
    else:
        print(f"No credential manifest at {manifest_path}; nothing to revoke")
    # The secrets file goes either way: unrevoked credentials still expire on their own.
    device_file.unlink(missing_ok=True)
    if not failures:
        manifest_path.unlink(missing_ok=True)
    return 1 if failures else 0


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--project-id", help="existing OpenRoots project UUID; inferred from the smoke device if omitted")
    parser.add_argument("--count", type=int, default=100, help="number of virtual devices to create/reuse")
    parser.add_argument("--api-url", help="OpenRoots backend URL (defaults to tests/support/mqtt/.env or localhost)")
    parser.add_argument("--env-file", type=Path, default=DEFAULT_ENV_FILE,
                        help="ignored local env file with the test account's email and password")
    parser.add_argument("--output", type=Path, default=DEFAULT_DEVICE_FILE,
                        help="device file consumed by mqtt_load_test.py (holds secrets)")
    parser.add_argument("--manifest", type=Path, default=DEFAULT_MANIFEST,
                        help="credential ids used by --revoke")
    parser.add_argument("--run-seconds", type=float, default=1800,
                        help="planned total load-test time; credentials expire 30 minutes after it")
    parser.add_argument("--revoke", action="store_true",
                        help="revoke the credentials in --manifest and delete --output, then exit")
    parser.add_argument("--name-prefix", default="openroots-load-test",
                        help="stable names make repeated setup runs reuse the same devices")
    parser.add_argument("--channel", default="temperature")
    parser.add_argument("--unit", default="C")
    args = parser.parse_args()

    load_local_env(args.env_file)
    api_url = args.api_url or os.getenv("OPENROOTS_API_URL", "http://127.0.0.1:8080")
    api = OpenRootsApi(api_url)

    if args.revoke:
        return revoke(api, args.manifest, args.output)

    if args.count < 1:
        parser.error("--count must be at least 1")
    if args.run_seconds <= 0:
        parser.error("--run-seconds must be positive")
    if not args.name_prefix.strip() or not args.channel.strip():
        parser.error("--name-prefix and --channel must not be blank")

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
        project_id = api.project_for_device(reference_device_id)

    devices_path = f"/api/v1/projects/{project_id}/devices"
    print(f"Checking existing test devices in project {project_id}...")
    existing = {
        device["name"]: device
        for device in api.get(devices_path).get("data", [])
        if isinstance(device, dict) and isinstance(device.get("name"), str)
    }

    device_ids: list[str] = []
    for index in range(1, args.count + 1):
        name = f"{args.name_prefix}-{index:03d}"
        device = existing.get(name)
        if device is None:
            device = api.post(devices_path, {
                "name": name,
                "type": "mqtt-load-test",
                "sensors": [{"channelName": args.channel, "unit": args.unit}],
            }).get("data", {})
            print(f"Registered {name} ({device.get('id', 'unknown ID')})")
        else:
            sensor_names = {sensor.get("channelName") for sensor in device.get("sensors", [])}
            if args.channel not in sensor_names:
                raise RuntimeError(
                    f"Existing device {name} does not have sensor '{args.channel}'. "
                    "Choose another --name-prefix or delete/recreate that test device."
                )
            print(f"Reusing {name} ({device['id']})")
        if not device.get("id"):
            raise RuntimeError(f"OpenRoots API did not return an ID for {name}")
        device_ids.append(device["id"])

    valid_for = timedelta(seconds=args.run_seconds) + EXPIRY_MARGIN
    issued: list[dict[str, str]] = []
    secrets: list[tuple[str, str]] = []
    for index, device_id in enumerate(device_ids, start=1):
        credential_id, secret = api.issue_credential(project_id, device_id, "load-test", valid_for)
        issued.append({"deviceId": device_id, "credentialId": credential_id})
        secrets.append((device_id, secret))
        # Save after each one so --revoke can clean up if a later call fails.
        save_manifest(args.manifest, project_id, issued)
        if index == 1 or index == len(device_ids) or index % 10 == 0:
            print(f"Issued temporary credentials: {index}/{len(device_ids)}")
    save_device_file(args.output, secrets)

    broker_diagnostics(device_ids[0])
    print(f"\nReady: {len(device_ids)} test devices, credentials valid for {valid_for}.")
    print(f"Device file (holds secrets, deleted by --revoke): {args.output}")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (RuntimeError, OSError, subprocess.SubprocessError, ValueError, KeyError) as error:
        print(f"Fleet setup failed: {error}", file=sys.stderr)
        raise SystemExit(1)
