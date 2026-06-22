"""Phase 1: Xiaomi pre-check with fail-fast."""
from __future__ import annotations

import json
import time
from dataclasses import dataclass, asdict
from pathlib import Path
from typing import Optional

from .adb import Adb
from .api_client import ApiClient
from .discovery import ValidationContext


@dataclass
class PrecheckItem:
    id: str
    name: str
    required: bool
    status: str  # PASS | FAIL | MANUAL | SKIP
    detail: str = ""
    evidence: str = ""


@dataclass
class PrecheckReport:
    timestamp: str
    device_serial: str
    manufacturer: str
    model: str
    os_release: str
    sdk_int: int
    miui_version: str
    hyperos_version: str
    overall: str
    items: list[PrecheckItem]
    fail_fast: bool = True

    def to_dict(self) -> dict:
        return {
            "timestamp": self.timestamp,
            "device": {
                "serial": self.device_serial,
                "manufacturer": self.manufacturer,
                "model": self.model,
                "osRelease": self.os_release,
                "sdkInt": self.sdk_int,
                "miuiVersion": self.miui_version,
                "hyperosVersion": self.hyperos_version,
            },
            "overall": self.overall,
            "failFast": self.fail_fast,
            "items": [asdict(i) for i in self.items],
        }


def _load_overrides(path: Path) -> dict:
    if path.exists():
        return json.loads(path.read_text(encoding="utf-8"))
    return {}


def run_precheck(
    adb: Adb,
    api: ApiClient,
    ctx: ValidationContext,
    repo_root: Path,
    overrides_path: Optional[Path] = None,
) -> PrecheckReport:
    overrides = _load_overrides(overrides_path or (repo_root / "scripts/validation/xiaomi/xiaomi-overrides.json"))
    dump = adb.validation_dump()
    items: list[PrecheckItem] = []

    autostart = overrides.get("autostartEnabled")
    if autostart is True:
        items.append(PrecheckItem("autostart", "Autostart enabled", True, "PASS", "override"))
    elif autostart is False:
        items.append(PrecheckItem("autostart", "Autostart enabled", True, "FAIL", "override=false"))
    else:
        items.append(
            PrecheckItem(
                "autostart",
                "Autostart enabled",
                True,
                "MANUAL",
                "MIUI has no public API — enable Security → Autostart, then set xiaomi-overrides.json",
            )
        )

    battery_ok = dump.get("batteryOptimizationDisabled")
    if battery_ok is None:
        battery_ok = adb.battery_whitelisted()
    items.append(
        PrecheckItem(
            "battery",
            "Battery optimization disabled",
            True,
            "PASS" if battery_ok else "FAIL",
            evidence=str(battery_ok),
        )
    )

    recents = overrides.get("recentsLocked")
    if recents is True:
        items.append(PrecheckItem("recents", "App locked in recents", True, "PASS", "override"))
    elif recents is False:
        items.append(PrecheckItem("recents", "App locked in recents", True, "FAIL", "override=false"))
    else:
        items.append(
            PrecheckItem(
                "recents",
                "App locked in recents",
                True,
                "MANUAL",
                "Lock child app in MIUI recents; confirm via xiaomi-overrides.json",
            )
        )

    notif = dump.get("notificationPermissionGranted")
    if notif is None:
        notif = adb.notification_granted()
    items.append(
        PrecheckItem(
            "notifications",
            "Notification permission granted",
            True,
            "PASS" if notif else "FAIL",
        )
    )

    a11y = dump.get("accessibilityEnabled")
    if a11y is None:
        a11y = adb.accessibility_enabled()
    items.append(
        PrecheckItem(
            "accessibility",
            "Accessibility enabled",
            True,
            "PASS" if a11y else "FAIL",
        )
    )

    do_active = dump.get("deviceOwnerActive")
    if do_active is None and ctx.child_device_id and api.parent_token:
        try:
            dev = api.get_device(ctx.child_device_id)
            do_active = dev.get("deviceOwnerActive")
        except Exception:
            do_active = None
    if do_active is None:
        do_active = adb.device_owner_active()
    items.append(
        PrecheckItem(
            "device_owner",
            "Device Owner status",
            True,
            "PASS" if do_active else "FAIL",
            detail=ctx.device_owner_state or ("ACTIVE" if do_active else "INACTIVE"),
        )
    )

    fcm_ok = dump.get("fcmTokenPresent") or bool(ctx.fcm_token)
    items.append(
        PrecheckItem(
            "fcm_token",
            "FCM token registered",
            True,
            "PASS" if fcm_ok else "FAIL",
            evidence=(ctx.fcm_token[:16] + "…") if ctx.fcm_token else "",
        )
    )

    heartbeat_ok = False
    heartbeat_detail = ""
    if ctx.child_device_id and api.parent_token:
        try:
            dev = api.get_device(ctx.child_device_id)
            last = dev.get("lastSeenAt") or dev.get("last_seen_at")
            status = (dev.get("status") or "").upper()
            heartbeat_detail = f"status={status} lastSeenAt={last}"
            if last:
                heartbeat_ok = status in ("ONLINE", "OFFLINE")
        except Exception as e:
            heartbeat_detail = str(e)
    else:
        heartbeat_detail = "No child device id or parent token — pair first"
    items.append(
        PrecheckItem(
            "heartbeat",
            "Heartbeat active",
            True,
            "PASS" if heartbeat_ok else "FAIL",
            detail=heartbeat_detail,
        )
    )

    blocking = [i for i in items if i.required and i.status == "FAIL"]
    manual_blocking = [i for i in items if i.required and i.status == "MANUAL"]
    if blocking:
        overall = "FAIL"
    elif manual_blocking:
        overall = "MANUAL_REQUIRED"
    else:
        overall = "PASS"

    report = PrecheckReport(
        timestamp=time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
        device_serial=adb.serial or "",
        manufacturer=ctx.manufacturer,
        model=ctx.model,
        os_release=ctx.os_release,
        sdk_int=ctx.sdk_int,
        miui_version=ctx.miui_version or dump.get("miuiVersion") or "",
        hyperos_version=ctx.hyperos_version or dump.get("hyperOsVersion") or "",
        overall=overall,
        items=items,
    )

    out = repo_root / "scripts" / "validation" / "xiaomi" / "xiaomi-precheck-report.json"
    out.write_text(json.dumps(report.to_dict(), indent=2), encoding="utf-8")
    return report
