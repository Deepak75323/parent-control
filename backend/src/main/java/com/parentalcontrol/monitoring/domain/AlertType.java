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
  TIME_EXTENSION_REQUEST;

  public String code() {
    return name();
  }
}
