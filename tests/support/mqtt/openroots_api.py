"""Minimal OpenRoots API client for the MQTT smoke and load tests.

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
from typing import Any
from uuid import UUID


class ApiError(RuntimeError):
    def __init__(self, status: int | None, message: str):
        super().__init__(message)
        self.status = status


def user_credentials_from_env() -> tuple[str, str]:
    email = os.getenv("MQTT_SMOKE_USER_EMAIL")
    password = os.getenv("MQTT_SMOKE_USER_PASSWORD")
    if not email or not password:
        raise ValueError(
            "set MQTT_SMOKE_USER_EMAIL and MQTT_SMOKE_USER_PASSWORD to a user who is "
            "OWNER or ADMIN of the test project"
        )
    return email, password


class OpenRootsApi:
    def __init__(self, base_url: str, email: str, password: str, timeout: float = 30):
        self.base_url = base_url.rstrip("/")
        self.email = email
        self.password = password
        self.timeout = timeout
        self.token: str | None = None

    def login(self) -> str:
        result = self._send("/api/v1/auth/login", "POST", {"email": self.email, "password": self.password}, auth=False)
        try:
            self.token = result["data"]["accessToken"]
        except (KeyError, TypeError) as error:
            raise ApiError(None, "login response did not contain an access token") from error
        return self.token

    def request(self, path: str, method: str = "GET", body: dict[str, Any] | None = None) -> dict[str, Any]:
        """Call the API as the test user; signs in again once if the token has expired."""
        if self.token is None:
            self.login()
        try:
            return self._send(path, method, body)
        except ApiError as error:
            if error.status != 401:
                raise
            self.login()
            return self._send(path, method, body)

    def _send(self, path: str, method: str, body: dict[str, Any] | None, auth: bool = True) -> dict[str, Any]:
        headers = {"Content-Type": "application/json"}
        if auth and self.token:
            headers["Authorization"] = f"Bearer {self.token}"
        request = urllib.request.Request(
            f"{self.base_url}{path}",
            data=json.dumps(body).encode("utf-8") if body is not None else None,
            method=method,
            headers=headers,
        )
        try:
            with urllib.request.urlopen(request, timeout=self.timeout) as response:
                raw = response.read()
        except urllib.error.HTTPError as error:
            details = error.read().decode("utf-8", errors="replace")
            raise ApiError(error.code, f"{method} {path} returned HTTP {error.code}: {details}") from error
        except urllib.error.URLError as error:
            raise ApiError(None, f"could not reach OpenRoots API at {self.base_url}: {error.reason}") from error
        return json.loads(raw) if raw else {}

    def project_for_device(self, device_id: str) -> str:
        wanted = str(UUID(device_id))
        for project in self.request("/api/v1/projects").get("data", []):
            project_id = project.get("id")
            if not project_id:
                continue
            devices = self.request(f"/api/v1/projects/{project_id}/devices").get("data", [])
            if any(device.get("id") == wanted for device in devices):
                return str(UUID(project_id))
        raise ApiError(
            None,
            f"device {wanted} is not in any project the test user can see. "
            "Check MQTT_SMOKE_DEVICE_ID and that the test user is a member of its project.",
        )

    def issue_credential(self, project_id: str, device_id: str, name: str, valid_for: timedelta) -> tuple[str, str]:
        """Create a credential that expires on its own; returns (credential id, secret)."""
        expires_at = (datetime.now(timezone.utc) + valid_for).replace(microsecond=0)
        result = self.request(
            f"/api/v1/projects/{project_id}/devices/{device_id}/credentials",
            "POST",
            {"name": name, "expiresAt": expires_at.isoformat().replace("+00:00", "Z")},
        )
        data = result.get("data") or {}
        if not data.get("id") or not data.get("secret"):
            raise ApiError(None, f"credential response for device {device_id} had no id or secret")
        return data["id"], data["secret"]

    def revoke_credential(self, project_id: str, device_id: str, credential_id: str) -> None:
        self.request(f"/api/v1/projects/{project_id}/devices/{device_id}/credentials/{credential_id}", "DELETE")
