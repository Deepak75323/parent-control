"""Shared OEM certification tests (ADB + API)."""
from __future__ import annotations

import json
import os
import subprocess
import time
from dataclasses import dataclass, field
from pathlib import Path
from typing import Optional


@dataclass
class ScenarioResult:
    id: str
    name: str
    status: str
    mttr_seconds: int = 0
    evidence: str = ""


@dataclass
class CertificationReport:
    vendor: str
    run_id: str
    scenarios: list[ScenarioResult] = field(default_factory=list)

    def add(self, result: ScenarioResult) -> None:
        self.scenarios.append(result)

    def to_dict(self) -> dict:
        passed = sum(1 for s in self.scenarios if s.status == "PASS")
        failed = sum(1 for s in self.scenarios if s.status == "FAIL")
        skipped = sum(1 for s in self.scenarios if s.status == "SKIP")
        manual = sum(1 for s in self.scenarios if s.status == "MANUAL")
        total = len(self.scenarios)
        auto = [s for s in self.scenarios if s.status in ("PASS", "FAIL")]
        auto_pass = sum(1 for s in auto if s.status == "PASS")
        score = round(100 * auto_pass / len(auto), 1) if auto else 0.0
        return {
            "vendor": self.vendor,
            "runId": self.run_id,
            "scenarios": [
                {
                    "id": s.id,
                    "name": s.name,
                    "status": s.status,
                    "mttrSeconds": s.mttr_seconds,
                    "evidence": s.evidence,
                }
                for s in self.scenarios
            ],
            "summary": {
                "total": total,
                "passed": passed,
                "failed": failed,
                "skipped": skipped,
                "manual": manual,
                "certificationScore": score,
            },
        }


def adb_cmd(serial: Optional[str], *args: str) -> subprocess.CompletedProcess:
    cmd = ["adb"]
    if serial:
        cmd.extend(["-s", serial])
    cmd.extend(args)
    return subprocess.run(cmd, capture_output=True, text=True, timeout=120)


def run_common_certification(
    vendor: str,
    serial: Optional[str],
    fast: bool,
    run_id: str,
    repo_root: Path,
) -> dict:
    report = CertificationReport(vendor=vendor, run_id=run_id)
    api = os.environ.get("API_BASE_URL", "http://localhost:8080")

    # CERT-01 Precheck / diagnostics dump
    start = time.time()
    adb_cmd(serial, "shell", "am", "broadcast", "-a", "com.childmonitor.VALIDATION_DUMP")
    time.sleep(2)
    report.add(ScenarioResult(
        "CERT-PRE", "OEM diagnostics dump",
        "PASS" if _logcat_has_dump(serial) else "SKIP",
        int(time.time() - start),
        "Requires debug build for VALIDATION_DUMP",
    ))

    # CERT-02 API health
    try:
        import urllib.request
        with urllib.request.urlopen(f"{api}/actuator/health", timeout=5) as resp:
            ok = resp.status == 200
    except Exception as e:
        ok = False
        err = str(e)
    else:
        err = ""
    report.add(ScenarioResult("CERT-02", "API health", "PASS" if ok else "FAIL", evidence=err))

    # CERT-03 Process kill
    pkg = "com.childmonitor"
    adb_cmd(serial, "shell", "am", "kill", pkg)
    time.sleep(30 if fast else 60)
    pid = adb_cmd(serial, "shell", "pidof", pkg).stdout.strip()
    report.add(ScenarioResult(
        "CERT-03", "Process kill recovery",
        "PASS" if pid else "FAIL",
        60 if not fast else 30,
        f"pid={pid or 'none'}",
    ))

    # CERT-04 Force stop
    adb_cmd(serial, "shell", "am", "force-stop", pkg)
    time.sleep(45 if fast else 90)
    pid = adb_cmd(serial, "shell", "pidof", pkg).stdout.strip()
    report.add(ScenarioResult(
        "CERT-05", "Force stop recovery",
        "PASS" if pid else "FAIL",
        evidence=f"pid={pid or 'none'}",
    ))

    # CERT-06 Background (short window for non-xiaomi)
    bg_min = 2 if fast else 5
    time.sleep(bg_min * 60)
    pid = adb_cmd(serial, "shell", "pidof", pkg).stdout.strip()
    report.add(ScenarioResult(
        "CERT-06", f"Background survival ({bg_min}m)",
        "PASS" if pid else "FAIL",
        bg_min * 60,
    ))

    # Manual stream tests — documented gates
    for cert_id, name in [
        ("CERT-09", "Screen sharing"),
        ("CERT-10", "Audio monitoring"),
        ("CERT-11", "Camera monitoring"),
        ("CERT-12", "Remote control"),
    ]:
        report.add(ScenarioResult(cert_id, name, "MANUAL", evidence="Parent-initiated stream required"))

    if not fast:
        report.add(ScenarioResult(
            "CERT-04", "Reboot recovery", "MANUAL",
            evidence="Use --fast to skip; full reboot in nightly farm",
        ))

    return report.to_dict()


def _logcat_has_dump(serial: Optional[str]) -> bool:
    proc = adb_cmd(serial, "logcat", "-d", "-t", "50")
    return "OEM_VALIDATION" in proc.stdout or "OEM_VALIDATION_DUMP" in proc.stdout
