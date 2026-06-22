#!/usr/bin/env python3
"""
Xiaomi Validation Automation Framework — 10-phase runner.

Usage:
  ./scripts/validation/xiaomi/run-xiaomi-validation.sh
  python3 scripts/validation/xiaomi/xiaomi_validate.py --fast --serial <adb-serial>
"""
from __future__ import annotations

import argparse
import json
import sys
import time
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(REPO_ROOT / "scripts" / "validation" / "xiaomi"))

from lib.adb import Adb  # noqa: E402
from lib.api_client import api_from_env  # noqa: E402
from lib.discovery import discover  # noqa: E402
from lib.precheck import run_precheck  # noqa: E402
from lib.tests_automated import run_automated_tests  # noqa: E402
from lib.tests_xiaomi import run_xiaomi_specific_tests  # noqa: E402
from lib.tests_manual import append_manual_tests  # noqa: E402
from lib.report import generate_html_report  # noqa: E402


def main() -> int:
    parser = argparse.ArgumentParser(description="Xiaomi 16-test validation automation")
    parser.add_argument("--serial", help="ADB device serial")
    parser.add_argument("--api-base", default=None, help="API base URL")
    parser.add_argument("--fast", action="store_true", help="Short background/reboot skips")
    parser.add_argument("--skip-reboot", action="store_true")
    parser.add_argument("--skip-network", action="store_true")
    parser.add_argument("--skip-tests", action="store_true", help="Precheck + discovery only")
    parser.add_argument("--background-minutes", type=int, default=None)
    parser.add_argument("--allow-manual-precheck", action="store_true")
    parser.add_argument(
        "--relaxed-precheck",
        action="store_true",
        help="Continue when notifications/FCM/DO missing (dev/Android 16)",
    )
    args = parser.parse_args()

    run_id = time.strftime("%Y%m%d-%H%M%S")
    adb = Adb(serial=args.serial)
    api = api_from_env()
    if args.api_base:
        api.base_url = args.api_base.rstrip("/")

    print("[Phase 2] Discovering validation context…")
    ctx = discover(adb, api, REPO_ROOT)
    print(json.dumps(ctx.to_dict(), indent=2))

    print("[Phase 1] Xiaomi pre-check…")
    precheck = run_precheck(adb, api, ctx, REPO_ROOT)
    print(f"Pre-check overall: {precheck.overall}")

    if precheck.overall == "FAIL":
        if args.relaxed_precheck:
            print(
                "WARN: pre-check FAIL — continuing (--relaxed-precheck). "
                "See xiaomi-precheck-report.json"
            )
        else:
            print("FAIL FAST: pre-check requirements missing. See xiaomi-precheck-report.json")
            return 1
    if precheck.overall == "MANUAL_REQUIRED" and not args.allow_manual_precheck:
        print(
            "FAIL FAST: manual MIUI settings required. "
            "Set xiaomi-overrides.json or pass --allow-manual-precheck"
        )
        return 1

    from lib.pass_fail import TestRun

    test_run: TestRun | None = None
    manual_guides = []
    if not args.skip_tests:
        bg = args.background_minutes
        if bg is None:
            bg = 2 if args.fast else 30

        if not ctx.child_device_id:
            print("[Phase 2b] Re-discover after pairing prerequisites…")
            ctx = discover(adb, api, REPO_ROOT)

        test_run = TestRun()
        try:
            print("[Phase 3] Automated matrix tests…")
            test_run = run_automated_tests(
                adb,
                api,
                ctx,
                REPO_ROOT,
                run_id,
                background_minutes=bg,
                skip_reboot=args.skip_reboot or args.fast,
                skip_network=args.skip_network,
            )

            print("[Phase 4–6] Xiaomi-specific / kill / doze…")
            test_run = run_xiaomi_specific_tests(adb, api, ctx, test_run, fast=args.fast)

            print("[Phase 9] Manual-assisted guides…")
            manual_guides = append_manual_tests(adb, test_run)
        except Exception as e:
            print(f"ERROR during test execution: {e}")
            test_run.add(
                __import__("lib.pass_fail", fromlist=["TestResult"]).TestResult(
                    test_id="RUNNER",
                    name="Test runner",
                    status="FAIL",
                    evidence=str(e),
                )
            )

    if test_run and test_run.results:
        print("[Phase 10] Generating HTML report…")
        report_path = generate_html_report(
            REPO_ROOT,
            precheck,
            test_run,
            ctx.to_dict(),
            manual_guides,
            run_id,
        )
        summary = test_run.summary()
        print(f"Report: {report_path}")
        print(json.dumps(summary, indent=2))
        failed = summary.get("failed", 0)
        return 1 if failed else 0

    return 0 if precheck.overall == "PASS" else 0


if __name__ == "__main__":
    raise SystemExit(main())
