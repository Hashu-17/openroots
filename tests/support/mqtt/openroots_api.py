"""Shared helpers for the MQTT smoke/load scripts: local .env loading and API sign-in."""

from __future__ import annotations

import json
import os
import urllib.error
import urllib.request
from pathlib import Path
from typing import Any

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


class OpenRootsApi:
    """Minimal JSON client for the Spring API. Signs in lazily with a test account.

    Credentials come from OPENROOTS_API_TOKEN, or OPENROOTS_API_EMAIL and
    OPENROOTS_API_PASSWORD (exchanged for a token at /api/v1/auth/login).
    """

    def __init__(self, base_url: str, token: str | None = None,
                 email: str | None = None, password: str | None = None):
        self.base_url = base_url.rstrip("/")
        self.token = token or os.getenv("OPENROOTS_API_TOKEN") or None
        self.email = email or os.getenv("OPENROOTS_API_EMAIL")
        self.password = password or os.getenv("OPENROOTS_API_PASSWORD")

    def _request(self, path: str, method: str, body: dict[str, Any] | None,
                 authenticated: bool) -> dict[str, Any]:
        headers = {"Content-Type": "application/json", "Accept": "application/json"}
        if authenticated:
            headers["Authorization"] = f"Bearer {self._access_token()}"
        data = json.dumps(body).encode("utf-8") if body is not None else None
        request = urllib.request.Request(f"{self.base_url}{path}", data=data, method=method, headers=headers)
        try:
            with urllib.request.urlopen(request, timeout=30) as response:
                return json.load(response)
        except urllib.error.HTTPError as error:
            details = error.read().decode("utf-8", errors="replace")
            raise RuntimeError(f"OpenRoots API {method} {path} returned HTTP {error.code}: {details}") from error
        except urllib.error.URLError as error:
            raise RuntimeError(f"Could not reach OpenRoots API at {self.base_url}: {error.reason}") from error

    def _access_token(self) -> str:
        if self.token:
            return self.token
        if not self.email or not self.password:
            raise RuntimeError(
                "The OpenRoots API requires sign-in: set OPENROOTS_API_TOKEN, or "
                "OPENROOTS_API_EMAIL and OPENROOTS_API_PASSWORD for a test account."
            )
        response = self._request("/api/v1/auth/login", "POST",
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
