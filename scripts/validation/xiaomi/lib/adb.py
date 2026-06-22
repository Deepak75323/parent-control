"""ADB helpers for Xiaomi validation automation."""
from __future__ import annotations

import json
import re
import subprocess
import time
from dataclasses import dataclass
from typing import Optional


@dataclass
class AdbDevice:
    serial: str
    manufacturer: str = ""
    model: str = ""
    sdk: int = 0
    release: str = ""


class Adb:
    def __init__(self, serial: Optional[str] = None, adb_path: str = "adb"):
        self.adb_path = adb_path
        self.serial = serial

    def _base(self) -> list[str]:
        cmd = [self.adb_path]
        if self.serial:
            cmd.extend(["-s", self.serial])
        return cmd

    def run(self, *args: str, timeout: int = 120) -> str:
        cmd = self._base() + list(args)
        proc = subprocess.run(
            cmd,
            capture_output=True,
            text=True,
            encoding="utf-8",
            errors="replace",
            timeout=timeout,
            check=False,
        )
        combined = (proc.stdout or "") + (proc.stderr or "")
        if proc.returncode != 0 and not combined.strip():
            raise RuntimeError(f"adb failed: {' '.join(cmd)}\n{proc.stderr}")
        return combined

    def shell(self, command: str, timeout: int = 120) -> str:
        return self.run("shell", command, timeout=timeout)

    def wait_for_device(self, timeout: int = 180) -> None:
        deadline = time.time() + timeout
        while time.time() < deadline:
            try:
                out = self.run("get-state", timeout=10).strip()
                if out == "device":
                    return
            except Exception:
                pass
            time.sleep(3)
        raise TimeoutError(f"Device {self.serial or 'default'} not ready after {timeout}s")

    def get_android_id(self) -> str:
        return self.shell("settings get secure android_id").strip()

    def get_prop(self, name: str) -> str:
        return self.shell(f"getprop {name}").strip()

    def is_xiaomi_family(self) -> bool:
        m = self.get_prop("ro.product.manufacturer").lower()
        return any(x in m for x in ("xiaomi", "redmi", "poco"))

    def force_stop(self, package: str = "com.childmonitor") -> None:
        self.shell(f"am force-stop {package}")

    def reboot(self) -> None:
        self.run("reboot")

    def force_idle(self) -> None:
        self.shell("dumpsys deviceidle enable", timeout=30)
        self.shell("dumpsys battery unplug", timeout=30)
        self.shell("dumpsys deviceidle force-idle", timeout=30)

    def unforce_idle(self) -> None:
        self.shell("dumpsys deviceidle unforce", timeout=30)
        self.shell("dumpsys battery reset", timeout=30)

    def screen_off(self) -> None:
        self.shell("input keyevent KEYCODE_POWER")

    def airplane_on(self) -> None:
        self.shell("cmd connectivity airplane-mode enable")

    def airplane_off(self) -> None:
        self.shell("cmd connectivity airplane-mode disable")

    def wifi_off(self) -> None:
        self.shell("svc wifi disable")

    def wifi_on(self) -> None:
        self.shell("svc wifi enable")

    def logcat_clear(self) -> None:
        self.run("logcat", "-c")

    def logcat_dump(self, tags: str) -> str:
        return self.run("logcat", "-d", "-t", "500", "-s", tags, timeout=60)

    def logcat_grep(self, pattern: str, since_clear: bool = False) -> str:
        if since_clear:
            self.logcat_clear()
        out = self.run("logcat", "-d", "-t", "800", "-v", "time", timeout=90)
        return "\n".join(
            line for line in out.splitlines() if re.search(pattern, line, re.I)
        )

    def dumpsys_services(self, package: str = "com.childmonitor") -> str:
        return self.shell(f"dumpsys activity services {package} | head -120")

    def monitoring_service_running(self) -> bool:
        out = self.dumpsys_services()
        return (
            "MonitoringForegroundService" in out
            and ("isForeground=true" in out or "app=ProcessRecord" in out or "startForeground" in out)
        )

    def validation_dump(self) -> dict:
        self.shell("am broadcast -a com.childmonitor.VALIDATION_DUMP")
        time.sleep(2)
        out = self.logcat_grep("XIAOMI_VALIDATION")
        for line in reversed(out.splitlines()):
            idx = line.find('{"type"')
            if idx >= 0:
                try:
                    return json.loads(line[idx:])
                except json.JSONDecodeError:
                    continue
            idx = line.find('{"type":')
            if idx >= 0:
                try:
                    return json.loads(line[idx:])
                except json.JSONDecodeError:
                    continue
        return {}

    def battery_whitelisted(self, package: str = "com.childmonitor") -> bool:
        out = self.shell("dumpsys deviceidle whitelist")
        return package in out

    def accessibility_enabled(self, package: str = "com.childmonitor") -> bool:
        out = self.shell("settings get secure enabled_accessibility_services")
        return package in out

    def device_owner_active(self, package: str = "com.childmonitor") -> bool:
        try:
            out = self.shell("dpm list-owners")
            if "not authorized" in out.lower() or "SecurityException" in out:
                return False
            return package in out
        except Exception:
            return False

    def notification_granted(self) -> bool:
        dump = self.validation_dump()
        if dump:
            return bool(dump.get("notificationPermissionGranted"))
        sdk = int(self.get_prop("ro.build.version.sdk") or "0")
        if sdk < 33:
            return True
        out = self.shell("dumpsys package com.childmonitor")
        return "POST_NOTIFICATIONS: granted=true" in out

    def capture_log_stream(self, log_path: str, tags: str) -> subprocess.Popen:
        cmd = self._base() + [
            "logcat", "-v", "time", "-s", tags,
        ]
        fh = open(log_path, "w", encoding="utf-8")
        return subprocess.Popen(cmd, stdout=fh, stderr=subprocess.STDOUT)

    def screenshot(self, path: str) -> None:
        remote = "/sdcard/xiaomi-val-screenshot.png"
        self.shell(f"screencap -p {remote}")
        self.run("pull", remote, path)
