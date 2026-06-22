"""Phase 10: HTML report generation."""
from __future__ import annotations

import html
import json
from pathlib import Path
from typing import Any

from .pass_fail import TestRun
from .precheck import PrecheckReport


def _esc(s: Any) -> str:
    return html.escape(str(s) if s is not None else "")


def generate_html_report(
    repo_root: Path,
    precheck: PrecheckReport,
    test_run: TestRun,
    context: dict,
    manual_guides: list[dict],
    run_id: str,
) -> Path:
    reports_dir = repo_root / "reports"
    reports_dir.mkdir(parents=True, exist_ok=True)
    out = reports_dir / "xiaomi-validation.html"
    summary = test_run.summary()

    rows = []
    for r in test_run.results:
        cls = r.status.lower()
        rows.append(
            f"<tr class='{cls}'><td>{_esc(r.test_id)}</td><td>{_esc(r.name)}</td>"
            f"<td><strong>{_esc(r.status)}</strong></td><td>{r.duration_sec}s</td>"
            f"<td>{_esc(r.evidence)}</td><td>{_esc(r.notes)}</td></tr>"
        )

    pre_rows = []
    for item in precheck.items:
        pre_rows.append(
            f"<tr><td>{_esc(item.id)}</td><td>{_esc(item.name)}</td>"
            f"<td>{_esc(item.status)}</td><td>{_esc(item.detail)}</td></tr>"
        )

    manual_html = []
    for g in manual_guides:
        steps = "".join(
            f"<li><b>{_esc(s['action'])}</b> — {_esc(s['expected'])}</li>"
            for s in g.get("userActions", [])
        )
        checks = "".join(f"<li>{_esc(c)}</li>" for c in g.get("checklist", []))
        manual_html.append(
            f"<section><h3>{_esc(g['test_id'])} — {_esc(g['title'])}</h3>"
            f"<p>Log check: {_esc(g.get('automaticLogCheck'))} "
            f"({'PASS' if g.get('automaticLogPass') else 'PENDING'})</p>"
            f"<h4>User actions</h4><ol>{steps}</ol>"
            f"<h4>Checklist</h4><ul>{checks}</ul></section>"
        )

    doc = f"""<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="utf-8"/>
  <title>Xiaomi Validation Report — {run_id}</title>
  <style>
    body {{ font-family: system-ui, sans-serif; margin: 2rem; background: #0f1115; color: #e8eaed; }}
    h1, h2, h3 {{ color: #8ab4f8; }}
    table {{ border-collapse: collapse; width: 100%; margin: 1rem 0; }}
    th, td {{ border: 1px solid #333; padding: 0.5rem; text-align: left; }}
    th {{ background: #1a1d24; }}
    tr.pass td:nth-child(3) {{ color: #81c995; }}
    tr.fail td:nth-child(3) {{ color: #f28b82; }}
    tr.manual td:nth-child(3) {{ color: #fdd663; }}
    tr.skip td:nth-child(3) {{ color: #9aa0a6; }}
    .badge {{ display: inline-block; padding: 0.25rem 0.75rem; border-radius: 4px; }}
    .badge-pass {{ background: #1e3a2f; color: #81c995; }}
    .badge-fail {{ background: #3c1f1e; color: #f28b82; }}
    .badge-manual {{ background: #3d3422; color: #fdd663; }}
    section {{ margin: 1.5rem 0; padding: 1rem; background: #1a1d24; border-radius: 8px; }}
    pre {{ background: #0b0d10; padding: 1rem; overflow-x: auto; font-size: 0.85rem; }}
  </style>
</head>
<body>
  <h1>Xiaomi Validation Report</h1>
  <p>Run ID: <code>{_esc(run_id)}</code></p>
  <p>
    Device: {_esc(precheck.manufacturer)} {_esc(precheck.model)} ·
    Android {_esc(precheck.os_release)} (SDK {_esc(precheck.sdk_int)}) ·
    MIUI {_esc(precheck.miui_version) or 'n/a'} ·
    HyperOS {_esc(precheck.hyperos_version) or 'n/a'}
  </p>
  <p>
    Pre-check: <span class="badge badge-{'pass' if precheck.overall == 'PASS' else 'fail'}">{_esc(precheck.overall)}</span>
    · Automation pass rate: {_esc(summary.get('automationCoveragePct'))}%
    · Logs: <code>logs/xiaomi/{_esc(run_id)}/</code>
  </p>

  <h2>Pre-check (Phase 1)</h2>
  <table>
    <tr><th>ID</th><th>Check</th><th>Status</th><th>Detail</th></tr>
    {''.join(pre_rows)}
  </table>

  <h2>Test Results</h2>
  <table>
    <tr><th>ID</th><th>Name</th><th>Result</th><th>Duration</th><th>Evidence</th><th>Notes</th></tr>
    {''.join(rows)}
  </table>

  <h2>Summary</h2>
  <pre>{_esc(json.dumps(summary, indent=2))}</pre>

  <h2>Discovered Context (redacted)</h2>
  <pre>{_esc(json.dumps(context, indent=2))}</pre>

  <h2>Manual Assisted Tests (Phase 9)</h2>
  {''.join(manual_html)}

  <h2>Pass/Fail Thresholds (Phase 8)</h2>
  <ul>
    <li>Heartbeat PASS: lastSeenAt updated</li>
    <li>FCM PASS: wake ≤60s</li>
    <li>Background PASS: MonitoringForegroundService alive after 30 minutes</li>
    <li>Process Kill PASS: recovery ≤60s</li>
    <li>Reboot PASS: ONLINE ≤10 minutes</li>
  </ul>
</body>
</html>
"""
    out.write_text(doc, encoding="utf-8")
    return out
