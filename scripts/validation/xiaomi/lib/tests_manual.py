"""Phase 9: manual-assisted test guides with log verification hooks."""
from __future__ import annotations

from dataclasses import dataclass, asdict
from typing import List

from .adb import Adb
from .pass_fail import TestResult, TestRun


@dataclass
class ManualStep:
    action: str
    expected: str


@dataclass
class ManualTestGuide:
    test_id: str
    title: str
    user_actions: List[ManualStep]
    log_markers: List[str]
    checklist: List[str]


MANUAL_GUIDES = [
    ManualTestGuide(
        "XIA-04",
        "Screen Share",
        [
            ManualStep("Parent: open Live Monitoring → Start screen share", "Child shows ScreenCaptureFgSvc foreground notification"),
            ManualStep("Observe parent viewport for 60s", "Stable frames, no black screen >10s"),
        ],
        ["ScreenCaptureFgSvc", "NativeStreamingCoord", "ICE connected"],
        [
            "Screen share starts ≤15s",
            "Survives 5 min with screen off on child",
            "Reconnect works after network blip",
        ],
    ),
    ManualTestGuide(
        "XIA-05",
        "Audio Stream",
        [
            ManualStep("Parent: start audio monitoring", "AudioStreamForegroundService notification on child"),
            ManualStep("Play audio on child device", "Parent hears audio within 10s"),
        ],
        ["AudioStreamForegroundService", "NativeStreamingCoord"],
        ["Audio audible", "No permanent mute after screen off"],
    ),
    ManualTestGuide(
        "XIA-06",
        "Camera Stream",
        [
            ManualStep("Parent: start camera stream", "Camera permission + CameraStreamForegroundService"),
            ManualStep("Wave hand in front of camera", "Parent video updates"),
        ],
        ["CameraStreamForegroundService", "NativeStreamingCoord"],
        ["Camera starts ≤20s", "Survives 3 min background"],
    ),
    ManualTestGuide(
        "XIA-07",
        "Remote Control",
        [
            ManualStep("Parent: send TAP center, SWIPE up, BACK", "Child executes each gesture"),
            ManualStep("Rotate child to landscape, TAP corner", "Coords land on visible target"),
        ],
        ["DeviceCommandProcessor", "AccessibilityHealth", "TAP", "SWIPE"],
        ["All gestures execute", "No silent failures in logs"],
    ),
    ManualTestGuide(
        "XIA-08",
        "Accessibility",
        [
            ManualStep("Settings → Accessibility → Child Monitor enabled", "AccessibilityHealth reports enabled"),
            ManualStep("Disable and re-enable accessibility", "App prompts and recovers"),
        ],
        ["AccessibilityHealth"],
        ["Gestures blocked when disabled", "Recovery ≤60s after re-enable"],
    ),
    ManualTestGuide(
        "XIA-09",
        "Device Owner",
        [
            ManualStep("Confirm Device Owner via adb dpm list-owners", "com.childmonitor listed"),
            ManualStep("Parent: ENABLE_KIOSK then DISABLE_KIOSK", "Kiosk applies and releases"),
        ],
        ["DeviceOwner", "ENABLE_KIOSK", "DISABLE_KIOSK"],
        ["Kiosk locks launcher", "Disable restores launcher"],
    ),
    ManualTestGuide(
        "XIA-12",
        "Launcher Hidden",
        [
            ManualStep("Enable kiosk / hide launcher", "Child cannot open Settings from launcher"),
            ManualStep("Parent DISABLE_KIOSK", "Launcher returns"),
        ],
        ["ENABLE_KIOSK", "LAUNCH_APP"],
        ["Launcher hidden while kiosk on", "No escape without parent command"],
    ),
]


def verify_manual_logs(adb: Adb, guide: ManualTestGuide) -> tuple[bool, str]:
    logs = adb.logcat_grep("|".join(guide.log_markers))
    hits = [m for m in guide.log_markers if m.lower() in logs.lower()]
    return len(hits) > 0, f"markers found: {hits}"


def append_manual_tests(adb: Adb, run: TestRun) -> list[dict]:
    exported = []
    for guide in MANUAL_GUIDES:
        log_ok, log_ev = verify_manual_logs(adb, guide)
        run.add(
            TestResult(
                test_id=guide.test_id,
                name=guide.title,
                status="MANUAL",
                evidence=log_ev if log_ok else "awaiting user execution",
                notes="; ".join(guide.checklist),
            )
        )
        exported.append(
            {
                **{k: v for k, v in asdict(guide).items() if k != "user_actions"},
                "userActions": [asdict(s) for s in guide.user_actions],
                "automaticLogCheck": log_ev,
                "automaticLogPass": log_ok,
            }
        )
    return exported
