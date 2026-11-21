"""Shared helpers for the MQTT smoke/load scripts: local .env loading, API sign-in, and
temporary device credentials.

The tests sign in as a test user who is OWNER or ADMIN of the test project and issue
short-lived device credentials for each run. The backend syncs each credential to the
broker, so no device password is stored in CI and nothing is set up by hand.
"""

from __future__ import annotations

import json
import os
import urllib.error
import urllib.request
from datetime import datetime, timedelta, timezone
from pathlib import Path
from typing import Any
from uuid import UUID

ENV_FILE = Path(__file__).resolve().parent / ".env"


def load_local_env(path: Path = ENV_FILE) -> None:
    """Load tests/support/mqtt/.env without overriding variables already set (e.g. by a workflow)."""
    if not path.is_file():
        return
    for raw_line in path.read_text(encoding="utf-8").splitlines():
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


class ApiError(RuntimeError):
    def __init__(self, status: int | None, message: str):
        super().__init__(message)
        self.status = status


class OpenRootsApi:
    """Minimal JSON client for the Spring API. Signs in lazily with a test account.

    Credentials come from OPENROOTS_API_TOKEN, or OPENROOTS_API_EMAIL and
    OPENROOTS_API_PASSWORD (exchanged for a token at /api/v1/auth/login). With an
    email and password, an expired token is replaced by signing in again.
    """

    def __init__(self, base_url: str, token: str | None = None,
                 email: str | None = None, password: str | None = None):
        self.base_url = base_url.rstrip("/")
        self.token = token or os.getenv("OPENROOTS_API_TOKEN") or None
        self.email = email or os.getenv("OPENROOTS_API_EMAIL")
        self.password = password or os.getenv("OPENROOTS_API_PASSWORD")

    def _send(self, path: str, method: str, body: dict[str, Any] | None,
              authenticated: bool) -> dict[str, Any]:
        headers = {"Content-Type": "application/json", "Accept": "application/json"}
        if authenticated:
            headers["Authorization"] = f"Bearer {self._access_token()}"
        data = json.dumps(body).encode("utf-8") if body is not None else None
        request = urllib.request.Request(f"{self.base_url}{path}", data=data, method=method, headers=headers)
        try:
            with urllib.request.urlopen(request, timeout=30) as response:
                raw = response.read()
        except urllib.error.HTTPError as error:
            details = error.read().decode("utf-8", errors="replace")
            raise ApiError(error.code, f"OpenRoots API {method} {path} returned HTTP {error.code}: {details}") from error
        except urllib.error.URLError as error:
            raise ApiError(None, f"Could not reach OpenRoots API at {self.base_url}: {error.reason}") from error
        return json.loads(raw) if raw else {}

    def _request(self, path: str, method: str, body: dict[str, Any] | None,
                 authenticated: bool) -> dict[str, Any]:
        try:
            return self._send(path, method, body, authenticated)
        except ApiError as error:
            # Access tokens are short-lived; sign in again once if we can.
            if not authenticated or error.status != 401 or not (self.email and self.password):
                raise
            self.token = None
            return self._send(path, method, body, authenticated)

    def _access_token(self) -> str:
        if self.token:
            return self.token
        if not self.email or not self.password:
            raise RuntimeError(
                "The OpenRoots API requires sign-in: set OPENROOTS_API_TOKEN, or "
                "OPENROOTS_API_EMAIL and OPENROOTS_API_PASSWORD for a test account."
            )
        response = self._send("/api/v1/auth/login", "POST",
                              {"email": self.email, "password": self.password}, authenticated=False)
        token = (response.get("data") or {}).get("accessToken")
        if not token:
            raise RuntimeError("OpenRoots API login did not return an access token")
        self.token = token
        return token

    def get(self, path: str) -> dict[str, Any]:
        return self._request(path, "GET", None, authenticated=True)

    def post(self, path: str, body: dict[str, Any]) -> dict[str, Any]:
        return self._request(path, "POST", body, authenticated=True)

    def delete(self, path: str) -> dict[str, Any]:
        return self._request(path, "DELETE", None, authenticated=True)

    def project_for_device(self, device_id: str) -> str:
        """Find the project that holds a device the test user can see."""
        try:
            wanted = str(UUID(device_id))
        except ValueError as error:
            raise RuntimeError("MQTT_SMOKE_DEVICE_ID must be a valid UUID to find the test project") from error
        for project in self.get("/api/v1/projects").get("data", []):
            project_id = project.get("id")
            if not project_id:
                continue
            devices = self.get(f"/api/v1/projects/{project_id}/devices").get("data", [])
            if any(device.get("id") == wanted for device in devices):
                return str(UUID(project_id))
        raise RuntimeError(
            f"Device {wanted} is not in any project the test user can see. Check "
            "MQTT_SMOKE_DEVICE_ID and that the test user is a member of its project."
        )

    def issue_credential(self, project_id: str, device_id: str, name: str,
                         valid_for: timedelta) -> tuple[str, str]:
        """Create a device credential that expires on its own; returns (credential id, secret).

        The backend sets the device's broker password to the new secret.
        """
        expires_at = (datetime.now(timezone.utc) + valid_for).replace(microsecond=0)
        result = self.post(
            f"/api/v1/projects/{project_id}/devices/{device_id}/credentials",
            {"name": name, "expiresAt": expires_at.isoformat().replace("+00:00", "Z")},
        )
        data = result.get("data") or {}
        if not data.get("id") or not data.get("secret"):
            raise RuntimeError(f"Credential response for device {device_id} had no id or secret")
        return data["id"], data["secret"]

    def revoke_credential(self, project_id: str, device_id: str, credential_id: str) -> None:
        """Revoke a credential; for the newest one the backend disables the broker login."""
        self.delete(f"/api/v1/projects/{project_id}/devices/{device_id}/credentials/{credential_id}")
