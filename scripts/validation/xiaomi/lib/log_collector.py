"""Phase 7: log collection for Xiaomi validation."""
from __future__ import annotations

import time
from pathlib import Path

from .adb import Adb

LOG_TAGS = (
    "MonitoringFgSvc,"
    "NativeStreamingCoord,"
    "FcmWakeHandler,"
    "ScreenCaptureFgSvc,"
    "AudioStreamForegroundService,"
    "CameraStreamForegroundService,"
    "AccessibilityHealth,"
    "DeviceCommandProcessor,"
    "XIAOMI_VALIDATION"
)


def collect_logs(adb: Adb, repo_root: Path, run_id: str) -> Path:
    log_dir = repo_root / "logs" / "xiaomi" / run_id
    log_dir.mkdir(parents=True, exist_ok=True)

    combined = adb.logcat_dump(LOG_TAGS)
    (log_dir / "combined.log").write_text(combined, encoding="utf-8")

    patterns = {
        "MonitoringFgSvc.log": r"MonitoringFg|MonitoringForeground",
        "NativeStreamingCoord.log": r"NativeStreamingCoord",
        "FcmWakeHandler.log": r"FcmWakeHandler|FCM_WAKE",
        "ScreenCaptureFgSvc.log": r"ScreenCapture",
        "AudioStreamForegroundService.log": r"AudioStream",
        "CameraStreamForegroundService.log": r"CameraStream",
        "AccessibilityHealth.log": r"AccessibilityHealth|a11y",
        "DeviceCommandProcessor.log": r"DeviceCommandProcessor|command",
    }
    full = adb.run("logcat", "-d", "-v", "time", timeout=90)
    for fname, pat in patterns.items():
        import re
        lines = [ln for ln in full.splitlines() if re.search(pat, ln, re.I)]
        (log_dir / fname).write_text("\n".join(lines), encoding="utf-8")

    try:
        adb.screenshot(str(log_dir / "screenshot.png"))
    except Exception:
        pass

    services = adb.dumpsys_services()
    (log_dir / "dumpsys-services.txt").write_text(services, encoding="utf-8")

    return log_dir
