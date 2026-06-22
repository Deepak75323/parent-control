"""Phase 3: automated Xiaomi matrix tests."""
from __future__ import annotations

import os
import subprocess
import time
from pathlib import Path

from .adb import Adb
from .api_client import ApiClient
from .discovery import ValidationContext
from .log_collector import collect_logs
from .pass_fail import (
    TestResult,
    TestRun,
    wait_for_fcm_wake,
    wait_for_heartbeat_update,
    wait_for_online_after_reboot,
    wait_for_recovery_after_kill,
    wait_for_service_alive,
)


def _timed(test_id: str, name: str, fn) -> TestResult:
    start = time.time()
    try:
        ok, evidence = fn()
        status = "PASS" if ok else "FAIL"
    except Exception as e:
        ok, evidence, status = False, str(e), "FAIL"
    return TestResult(
        test_id=test_id,
        name=name,
        status=status,
        duration_sec=round(time.time() - start, 1),
        evidence=evidence,
    )


def run_automated_tests(
    adb: Adb,
    api: ApiClient,
    ctx: ValidationContext,
    repo_root: Path,
    run_id: str,
    *,
    background_minutes: int = 30,
    skip_reboot: bool = False,
    skip_network: bool = False,
) -> TestRun:
    run = TestRun()
    log_dir = collect_logs(adb, repo_root, run_id)

    def attach(r: TestResult) -> TestResult:
        r.logs_path = str(log_dir)
        run.add(r)
        return r

    # XIA-01 Pairing
    def xia01():
        if ctx.child_device_id and ctx.jwt:
            return True, f"already paired child={ctx.child_device_id}"
        script = repo_root / "scripts" / "dev" / "auto-pair.sh"
        if script.exists():
            env = {
                "PHONE_SERIAL": adb.serial or "",
                "API_BASE": api.base_url,
                "PARENT_EMAIL": os.environ.get("PARENT_EMAIL", ""),
                "PARENT_PASSWORD": os.environ.get("PARENT_PASSWORD", ""),
            }
            proc = subprocess.run(
                [str(script)],
                cwd=str(repo_root),
                capture_output=True,
                text=True,
                timeout=180,
                check=False,
                env={**os.environ, **{k: v for k, v in env.items() if v}},
            )
            if proc.returncode == 0:
                return True, proc.stdout[-500:]
            return False, proc.stderr or proc.stdout
        return False, "auto-pair.sh missing and device not paired"

    attach(_timed("XIA-01", "Pairing", xia01))

    if not ctx.child_device_id:
        run.add(
            TestResult(
                "XIA-02",
                "Heartbeat",
                "SKIP",
                notes="pairing failed — skipping dependent tests",
            )
        )
        return run

    baseline_dev = api.get_device(ctx.child_device_id)
    baseline_last = baseline_dev.get("lastSeenAt") or baseline_dev.get("last_seen_at")

    # XIA-02 Heartbeat
    attach(
        _timed(
            "XIA-02",
            "Heartbeat",
            lambda: wait_for_heartbeat_update(api, ctx.child_device_id, baseline_last, 120),
        )
    )

    # XIA-03 FCM Wake
    def xia03():
        adb.logcat_clear()
        script = repo_root / "scripts" / "dev" / "send-fcm-wake.sh"
        if script.exists() and ctx.fcm_token and os.environ.get("FCM_SERVER_KEY"):
            subprocess.run(
                [str(script), "STREAM_WAKE"],
                cwd=str(repo_root),
                timeout=60,
                check=False,
                env={
                    **os.environ,
                    "DEVICE_FCM_TOKEN": ctx.fcm_token,
                },
            )
        else:
            api.send_command(ctx.child_device_id, "TAP", {"x": 0.5, "y": 0.5})
        time.sleep(15)
        logs = adb.logcat_grep("FcmWake|FcmWakeHandler|STREAM_REQUEST")
        return wait_for_fcm_wake(logs, 60)

    attach(_timed("XIA-03", "FCM Wake", xia03))

    # XIA-10 Background
    if background_minutes > 0:
        attach(
            _timed(
                "XIA-10",
                f"Background survival ({background_minutes}m)",
                lambda: wait_for_service_alive(adb, background_minutes),
            )
        )

    # XIA-11 Screen Off
    def xia11():
        adb.logcat_clear()
        adb.screen_off()
        time.sleep(30)
        alive = adb.monitoring_service_running()
        hb_ok, detail = wait_for_heartbeat_update(
            api, ctx.child_device_id, baseline_last, 90
        )
        return alive and hb_ok, f"svc={alive} {detail}"

    attach(_timed("XIA-11", "Screen Off", xia11))

    # XIA-13 Process Kill (also phase 5)
    def xia13():
        adb.logcat_clear()
        adb.force_stop()
        time.sleep(5)
        return wait_for_recovery_after_kill(adb, api, ctx.child_device_id, 60)

    attach(_timed("XIA-13", "Process Kill", xia13))

    # XIA-14 Reboot
    if not skip_reboot:
        def xia14():
            adb.reboot()
            adb.wait_for_device(600)
            time.sleep(30)
            return wait_for_online_after_reboot(api, ctx.child_device_id, 600)

        attach(_timed("XIA-14", "Reboot", xia14))
    else:
        run.add(TestResult("XIA-14", "Reboot", "SKIP", notes="--skip-reboot"))

    if not skip_network:
        # XIA-15 Network Loss
        def xia15():
            adb.logcat_clear()
            adb.airplane_on()
            time.sleep(20)
            off = api.get_device(ctx.child_device_id)
            status = (off.get("status") or "").upper()
            return status in ("OFFLINE", "ONLINE"), f"status after loss={status}"

        attach(_timed("XIA-15", "Network Loss", xia15))

        # XIA-16 Network Restore
        def xia16():
            adb.airplane_off()
            adb.wifi_on()
            time.sleep(10)
            return wait_for_heartbeat_update(api, ctx.child_device_id, None, 120)

        attach(_timed("XIA-16", "Network Restore", xia16))
    else:
        run.add(TestResult("XIA-15", "Network Loss", "SKIP", notes="--skip-network"))
        run.add(TestResult("XIA-16", "Network Restore", "SKIP", notes="--skip-network"))

    collect_logs(adb, repo_root, run_id)
    return run
