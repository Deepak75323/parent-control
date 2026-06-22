"""Phase 8: PASS/FAIL engine with Xiaomi thresholds."""
from __future__ import annotations

import re
import time
from dataclasses import dataclass, field
from datetime import datetime, timezone
from typing import Callable, Optional

from .adb import Adb
from .api_client import ApiClient
from .discovery import ValidationContext


@dataclass
class TestResult:
    test_id: str
    name: str
    status: str  # PASS | FAIL | SKIP | MANUAL
    duration_sec: float = 0.0
    evidence: str = ""
    logs_path: str = ""
    screenshot_path: str = ""
    notes: str = ""


@dataclass
class TestRun:
    results: list[TestResult] = field(default_factory=list)

    def add(self, result: TestResult) -> None:
        self.results.append(result)

    def summary(self) -> dict:
        total = len(self.results)
        passed = sum(1 for r in self.results if r.status == "PASS")
        failed = sum(1 for r in self.results if r.status == "FAIL")
        manual = sum(1 for r in self.results if r.status == "MANUAL")
        skipped = sum(1 for r in self.results if r.status == "SKIP")
        automated = [r for r in self.results if r.status in ("PASS", "FAIL")]
        auto_total = len(automated)
        auto_pass = sum(1 for r in automated if r.status == "PASS")
        coverage = round(100 * auto_pass / auto_total, 1) if auto_total else 0.0
        return {
            "total": total,
            "passed": passed,
            "failed": failed,
            "manual": manual,
            "skipped": skipped,
            "automationCoveragePct": coverage,
        }


def parse_iso(ts: str) -> Optional[datetime]:
    if not ts:
        return None
    try:
        if ts.endswith("Z"):
            return datetime.fromisoformat(ts.replace("Z", "+00:00"))
        return datetime.fromisoformat(ts).replace(tzinfo=timezone.utc)
    except ValueError:
        return None


def wait_for_heartbeat_update(
    api: ApiClient,
    device_id: str,
    baseline: Optional[str],
    timeout_sec: int = 120,
) -> tuple[bool, str]:
    start = time.time()
    last_detail = ""
    while time.time() - start < timeout_sec:
        try:
            dev = api.get_device(device_id)
            last = dev.get("lastSeenAt") or dev.get("last_seen_at") or ""
            status = (dev.get("status") or "").upper()
            last_detail = f"status={status} lastSeenAt={last}"
            if last and last != baseline:
                return True, last_detail
            if status == "ONLINE" and last:
                bt = parse_iso(baseline or "")
                lt = parse_iso(last)
                if lt and (not bt or lt > bt):
                    return True, last_detail
        except Exception as e:
            last_detail = str(e)
        time.sleep(5)
    return False, last_detail


def wait_for_fcm_wake(log_text: str, timeout_sec: int = 60) -> tuple[bool, str]:
    patterns = [
        r"FcmWakeHandler",
        r"FCM_WAKE",
        r"onMessageReceived",
        r"STREAM_REQUEST",
    ]
    for pat in patterns:
        if re.search(pat, log_text, re.I):
            return True, f"matched {pat}"
    return False, "no FCM wake markers in log window"


def wait_for_service_alive(adb: Adb, minutes: int) -> tuple[bool, str]:
    deadline = time.time() + minutes * 60
    checks = 0
    while time.time() < deadline:
        alive = adb.monitoring_service_running()
        checks += 1
        if not alive:
            return False, f"MonitoringForegroundService not running at check {checks}"
        time.sleep(60)
    return True, f"alive across {checks} minute checks"


def wait_for_recovery_after_kill(
    adb: Adb,
    api: ApiClient,
    device_id: str,
    timeout_sec: int = 60,
) -> tuple[bool, str]:
    start = time.time()
    while time.time() - start < timeout_sec:
        svc = adb.monitoring_service_running()
        hb_ok, hb_detail = wait_for_heartbeat_update(api, device_id, None, timeout_sec=15)
        if svc and hb_ok:
            return True, f"service+heartbeat: {hb_detail}"
        time.sleep(5)
    return False, "recovery not within 60s"


def wait_for_online_after_reboot(
    api: ApiClient,
    device_id: str,
    timeout_sec: int = 600,
) -> tuple[bool, str]:
    start = time.time()
    detail = ""
    while time.time() - start < timeout_sec:
        try:
            dev = api.get_device(device_id)
            status = (dev.get("status") or "").upper()
            last = dev.get("lastSeenAt") or dev.get("last_seen_at") or ""
            detail = f"status={status} lastSeenAt={last}"
            if status == "ONLINE" and last:
                return True, detail
        except Exception as e:
            detail = str(e)
        time.sleep(15)
    return False, detail
