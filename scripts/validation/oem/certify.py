#!/usr/bin/env python3
"""Common OEM certification runner for non-Xiaomi vendors (delegates shared tests)."""
from __future__ import annotations

import argparse
import json
import subprocess
import sys
import time
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(REPO_ROOT / "scripts" / "validation" / "oem" / "lib"))

from common_tests import run_common_certification  # noqa: E402


def main() -> int:
    parser = argparse.ArgumentParser(description="OEM device certification")
    parser.add_argument("--vendor", required=True)
    parser.add_argument("--serial", default=None)
    parser.add_argument("--fast", action="store_true")
    args = parser.parse_args()

    run_id = time.strftime("%Y%m%d-%H%M%S")
    report = run_common_certification(
        vendor=args.vendor,
        serial=args.serial,
        fast=args.fast,
        run_id=run_id,
        repo_root=REPO_ROOT,
    )

    reports_dir = REPO_ROOT / "reports"
    reports_dir.mkdir(parents=True, exist_ok=True)
    out = reports_dir / "oem-certification.json"
    out.write_text(json.dumps(report, indent=2))
    html = reports_dir / f"oem-certification-{args.vendor}.html"
    html.write_text(_html_report(report))

    print(f"Certification score: {report['summary']['certificationScore']}%")
    print(f"Report: {out}")
    return 0 if report["summary"]["failed"] == 0 else 1


def _html_report(report: dict) -> str:
    rows = "".join(
        f"<tr><td>{s['id']}</td><td>{s['name']}</td><td>{s['status']}</td>"
        f"<td>{s.get('evidence', '')}</td></tr>"
        for s in report.get("scenarios", [])
    )
    return f"""<!DOCTYPE html><html><head><title>OEM Certification — {report.get('vendor')}</title></head>
<body><h1>OEM Certification — {report.get('vendor')}</h1>
<p>Score: {report['summary']['certificationScore']}% |
Passed: {report['summary']['passed']}/{report['summary']['total']}</p>
<table border="1"><tr><th>ID</th><th>Name</th><th>Status</th><th>Evidence</th></tr>{rows}</table>
</body></html>"""


if __name__ == "__main__":
    raise SystemExit(main())
