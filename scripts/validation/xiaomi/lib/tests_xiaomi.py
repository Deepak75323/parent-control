"""Phase 4–6: Xiaomi-specific, process kill, and Doze tests."""
from __future__ import annotations

import time

from .adb import Adb
from .api_client import ApiClient
from .discovery import ValidationContext
from .pass_fail import (
    TestResult,
    TestRun,
    wait_for_fcm_wake,
    wait_for_heartbeat_update,
    wait_for_recovery_after_kill,
)


def run_xiaomi_specific_tests(
    adb: Adb,
    api: ApiClient,
    ctx: ValidationContext,
    run: TestRun,
    *,
    fast: bool = False,
) -> TestRun:
    if not ctx.child_device_id:
        return run

    def add(test_id: str, name: str, ok: bool, evidence: str, notes: str = "") -> None:
        run.add(
            TestResult(
                test_id=test_id,
                name=name,
                status="PASS" if ok else "FAIL",
                evidence=evidence,
                notes=notes,
            )
        )

    dump = adb.validation_dump()
    battery = dump.get("batteryOptimizationDisabled", adb.battery_whitelisted())

    # Battery optimization disabled (expected good path)
    add(
        "XIA-BAT-OFF",
        "Battery optimization disabled",
        bool(battery),
        f"batteryExempt={battery}",
    )

    # Autostart — manual/override only
    run.add(
        TestResult(
            test_id="XIA-AUTOSTART",
            name="Autostart policy check",
            status="MANUAL",
            notes="Toggle autostart in MIUI Security; re-run with override or observe heartbeat drop",
        )
    )

    # Recents swipe simulation: force-stop other apps, return to child
    adb.logcat_clear()
    adb.shell("input keyevent KEYCODE_APP_SWITCH")
    time.sleep(2)
    adb.shell("input keyevent KEYCODE_HOME")
    time.sleep(5)
    svc = adb.monitoring_service_running()
    hb_ok, detail = wait_for_heartbeat_update(api, ctx.child_device_id, None, 60)
    add("XIA-RECENTS", "Recents swipe survival", svc and hb_ok, f"svc={svc} {detail}")

    # Screen off extended (fast: 2 min, full: 5 min sample — overnight is manual)
    off_min = 2 if fast else 5
    adb.screen_off()
    time.sleep(off_min * 60)
    svc2 = adb.monitoring_service_running()
    hb2, det2 = wait_for_heartbeat_update(api, ctx.child_device_id, None, 90)
    add(
        "XIA-SCREEN-OFF",
        f"Screen off {off_min}m",
        svc2 and hb2,
        f"svc={svc2} {det2}",
        notes="Overnight (8h) → manual assisted XIA-11 extended",
    )

    # Phase 5 — explicit process kill
    adb.force_stop()
    ok, ev = wait_for_recovery_after_kill(adb, api, ctx.child_device_id, 60)
    logs = adb.logcat_grep("MonitoringFg|Heartbeat|DeviceCommandProcessor")
    add("XIA-KILL", "Process kill recovery", ok, f"{ev} logs={len(logs.splitlines())} lines")

    # Phase 6 — Doze
    adb.logcat_clear()
    try:
        adb.force_idle()
        time.sleep(5)
        doze_errors = []
        for label, fn in (
            ("TAP", lambda: api.send_command(ctx.child_device_id, "TAP", {"x": 0.5, "y": 0.5})),
            ("SCREEN", lambda: api.start_stream(ctx.child_device_id, "SCREEN")),
            ("AUDIO", lambda: api.start_stream(ctx.child_device_id, "AUDIO")),
        ):
            try:
                fn()
            except Exception as e:
                doze_errors.append(f"{label}:{e}")
        time.sleep(20)
        logs = adb.logcat_grep("FcmWake|NativeStreamingCoord|DeviceCommandProcessor")
        fcm_ok, fcm_ev = wait_for_fcm_wake(logs, 60)
        coord_ok = "NativeStreamingCoord" in logs or "STREAM" in logs
        cmd_ok = "DeviceCommandProcessor" in logs or "TAP" in logs
        ok = fcm_ok and (coord_ok or cmd_ok) and not doze_errors
        add(
            "XIA-DOZE",
            "Doze force-idle commands/streams",
            ok,
            f"fcm={fcm_ev} coord={coord_ok} cmd={cmd_ok} errors={'; '.join(doze_errors) or 'none'}",
        )
    except Exception as e:
        add("XIA-DOZE", "Doze force-idle commands/streams", False, str(e))
    finally:
        try:
            adb.unforce_idle()
        except Exception:
            pass

    return run
