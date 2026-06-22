"""Auto-discover validation context from backend, ADB, and database."""
from __future__ import annotations

import json
import os
import re
import subprocess
from dataclasses import dataclass, asdict
from pathlib import Path
from typing import Optional

from .adb import Adb
from .api_client import ApiClient


@dataclass
class ValidationContext:
    device_id: str = ""
    child_device_id: str = ""
    parent_device_id: str = ""
    pairing_code: str = ""
    room_id: str = ""
    session_id: str = ""
    command_id: str = ""
    jwt: str = ""
    fcm_token: str = ""
    android_id: str = ""
    device_owner_state: str = ""
    parent_token: str = ""
    api_base_url: str = ""
    adb_serial: str = ""
    manufacturer: str = ""
    model: str = ""
    os_release: str = ""
    sdk_int: int = 0
    miui_version: str = ""
    hyperos_version: str = ""

    def to_dict(self) -> dict:
        d = asdict(self)
        if d.get("jwt"):
            d["jwt"] = d["jwt"][:24] + "…"
        if d.get("fcm_token"):
            d["fcm_token"] = d["fcm_token"][:24] + "…"
        if d.get("parent_token"):
            d["parent_token"] = d["parent_token"][:24] + "…"
        return d


def _psql_query(sql: str, repo_root: Path) -> str:
    psql = repo_root / "scripts" / "dev" / "psql.sh"
    if not psql.exists():
        return ""
    proc = subprocess.run(
        [str(psql), "-t", "-A", "-c", sql],
        capture_output=True,
        text=True,
        cwd=str(repo_root),
        timeout=30,
        check=False,
    )
    return (proc.stdout or "").strip()


def _read_fcm_from_adb(adb: Adb) -> str:
    dump = adb.validation_dump()
    if dump.get("fcmTokenPresent"):
        prefix = dump.get("fcmTokenPrefix") or ""
        if prefix:
            return prefix
    try:
        out = adb.shell(
            "run-as com.childmonitor cat "
            "/data/data/com.childmonitor/shared_prefs/child_monitor_prefs.xml"
        )
        m = re.search(r'name="fcm_token"[^>]*>([^<]+)<', out)
        if m:
            return m.group(1).strip()
    except Exception:
        pass
    return ""


def _read_jwt_from_adb(adb: Adb) -> str:
    try:
        out = adb.shell(
            "run-as com.childmonitor cat "
            "/data/data/com.childmonitor/shared_prefs/child_monitor_prefs.xml"
        )
        for key in ("device_jwt", "jwt", "access_token"):
            m = re.search(rf'name="{key}"[^>]*>([^<]+)<', out)
            if m and m.group(1).strip():
                return m.group(1).strip()
    except Exception:
        pass
    return ""


def discover(
    adb: Adb,
    api: ApiClient,
    repo_root: Path,
    parent_email: Optional[str] = None,
    parent_password: Optional[str] = None,
) -> ValidationContext:
    ctx = ValidationContext()
    ctx.adb_serial = adb.serial or ""
    ctx.api_base_url = api.base_url
    ctx.android_id = adb.get_android_id()
    ctx.manufacturer = adb.get_prop("ro.product.manufacturer")
    ctx.model = adb.get_prop("ro.product.model")
    ctx.os_release = adb.get_prop("ro.build.version.release")
    ctx.sdk_int = int(adb.get_prop("ro.build.version.sdk") or "0")
    ctx.miui_version = adb.get_prop("ro.miui.ui.version.name") or ""
    ctx.hyperos_version = adb.get_prop("ro.mi.os.version.name") or ""

    email = parent_email or os.environ.get("PARENT_EMAIL", "")
    password = parent_password or os.environ.get("PARENT_PASSWORD", "")
    if email and password:
        ctx.parent_token = api.login_parent(email, password)

    devices = api.list_devices() if ctx.parent_token else []
    if devices and ctx.android_id:
        for d in devices:
            dev_id = str(d.get("id") or "")
            if dev_id:
                ctx.child_device_id = dev_id
                ctx.device_id = dev_id
                break
    if not ctx.child_device_id and devices:
        ctx.child_device_id = str(devices[0].get("id") or "")
        ctx.device_id = ctx.child_device_id

    if not ctx.fcm_token:
        ctx.fcm_token = _read_fcm_from_adb(adb)

    ctx.jwt = _read_jwt_from_adb(adb)

    if ctx.android_id:
        sql = (
            "SELECT id::text, fcm_token, status::text, device_owner_active::text "
            f"FROM devices WHERE android_id = '{ctx.android_id}' AND deleted_at IS NULL LIMIT 1;"
        )
        row = _psql_query(sql, repo_root)
        if row and "|" in row:
            parts = row.split("|")
            if len(parts) >= 2:
                ctx.child_device_id = parts[0] or ctx.child_device_id
                ctx.device_id = ctx.child_device_id
                if not ctx.fcm_token and parts[1]:
                    ctx.fcm_token = parts[1]
                if len(parts) >= 4 and parts[3]:
                    ctx.device_owner_state = "ACTIVE" if parts[3] == "t" else "INACTIVE"

    if ctx.child_device_id:
        cmd_sql = (
            "SELECT id::text FROM device_commands "
            f"WHERE device_id = '{ctx.child_device_id}'::uuid "
            "ORDER BY created_at DESC LIMIT 1;"
        )
        ctx.command_id = _psql_query(cmd_sql, repo_root)

    if ctx.child_device_id and ctx.parent_token:
        try:
            dev = api.get_device(ctx.child_device_id)
            if dev.get("deviceOwnerActive"):
                ctx.device_owner_state = "ACTIVE"
        except Exception:
            pass
    if ctx.device_owner_state != "ACTIVE" and adb.device_owner_active():
        ctx.device_owner_state = "ACTIVE"
    elif not ctx.device_owner_state:
        ctx.device_owner_state = "INACTIVE"

    out_path = repo_root / "scripts" / "validation" / "xiaomi" / "xiaomi-context.json"
    out_path.write_text(json.dumps(ctx.to_dict(), indent=2), encoding="utf-8")
    return ctx
