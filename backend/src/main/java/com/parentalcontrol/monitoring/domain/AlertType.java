package com.parentalcontrol.monitoring.domain;

/** Alert type codes persisted / published to parents. */
public enum AlertType {
  DEVICE_TRUST_DEGRADED,
  SCREEN_CAPTURE_LOST,
  ACCESSIBILITY_SERVICE_DISCONNECTED,
  DEVICE_ADMIN_DISABLE_ATTEMPT,
  DEVICE_ADMIN_DISABLED,
  CHILD_LOGIN_RECOVERY,
  LOW_BATTERY,
  TIME_EXTENSION_REQUEST,
  /** Android 15+ dataSync FGS quota / standby degradation (P1-08). */
  MONITORING_DEGRADED;

  public String code() {
    return name();
  }
}
