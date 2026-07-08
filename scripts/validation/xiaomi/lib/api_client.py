"""Backend API client for Xiaomi validation."""
from __future__ import annotations

import json
import urllib.error
import urllib.request
from typing import Any, Optional


class ApiClient:
    def __init__(self, base_url: str, timeout: int = 30):
        self.base_url = base_url.rstrip("/")
        self.timeout = timeout
        self.parent_token: Optional[str] = None

    def _request(
        self,
        method: str,
        path: str,
        body: Optional[dict] = None,
        token: Optional[str] = None,
    ) -> Any:
        url = f"{self.base_url}{path}"
        headers = {"Content-Type": "application/json", "Accept": "application/json"}
        if token:
            headers["Authorization"] = f"Bearer {token}"
        data = json.dumps(body).encode() if body is not None else None
        req = urllib.request.Request(url, data=data, headers=headers, method=method)
        try:
            with urllib.request.urlopen(req, timeout=self.timeout) as resp:
                raw = resp.read().decode()
                if not raw:
                    return None
                parsed = json.loads(raw)
                if isinstance(parsed, dict) and "data" in parsed:
                    return parsed["data"]
                return parsed
        except urllib.error.HTTPError as e:
            err = e.read().decode() if e.fp else str(e)
            raise RuntimeError(f"{method} {path} -> {e.code}: {err}") from e

    def login_parent(self, email: str, password: str) -> str:
        resp = self._request(
            "POST",
            "/api/v1/auth/login",
            {"email": email, "password": password},
        )
        token = (resp or {}).get("accessToken") or (resp or {}).get("token")
        if not token:
            raise RuntimeError("Parent login did not return accessToken")
        self.parent_token = token
        return token

    def list_devices(self, token: Optional[str] = None) -> list[dict]:
        resp = self._request("GET", "/api/v1/devices", token=token or self.parent_token)
        if isinstance(resp, list):
            return resp
        return []

    def get_device(self, device_id: str, token: Optional[str] = None) -> dict:
        resp = self._request(
            "GET", f"/api/v1/devices/{device_id}", token=token or self.parent_token
        )
        return resp or {}

    def pairing_code(self, token: Optional[str] = None, child_login_password: str = "TestChild1234") -> dict:
        return self._request(
            "POST",
            "/api/v1/devices/pairing-code",
            {"childLoginPassword": child_login_password},
            token=token or self.parent_token,
        ) or {}

    def send_command(
        self,
        device_id: str,
        command_type: str,
        payload: Optional[dict] = None,
        token: Optional[str] = None,
    ) -> dict:
        body: dict[str, Any] = {"commandType": command_type}
        if payload:
            body["payload"] = payload
        return self._request(
            "POST",
            f"/api/v1/devices/{device_id}/commands",
            body,
            token=token or self.parent_token,
        ) or {}

    def start_stream(
        self,
        device_id: str,
        stream_type: str = "SCREEN",
        token: Optional[str] = None,
    ) -> dict:
        return self._request(
            "POST",
            "/api/v1/monitoring/start-stream",
            {"deviceId": device_id, "streamType": stream_type},
            token=token or self.parent_token,
        ) or {}

    def health(self) -> bool:
        try:
            self._request("GET", "/actuator/health")
            return True
        except Exception:
            return False


def api_from_env() -> ApiClient:
    import os

    base = os.environ.get("API_BASE_URL", os.environ.get("API_BASE", "http://localhost:8080"))
    return ApiClient(base)
