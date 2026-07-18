package com.parentalcontrol.device.domain;

public enum DeviceCommandType {
  ENABLE_KIOSK,
  DISABLE_KIOSK,
  REVOKE_MONITORING,
  /** Remote control — requires Accessibility with canPerformGestures. */
  TAP,
  SWIPE,
  GLOBAL_BACK,
  GLOBAL_HOME,
  TYPE_TEXT,
  LAUNCH_APP,
  /** Forces the child device to immediately re-upload its installed app inventory. */
  SYNC_INSTALLED_APPS,
  /** Forces the child device to immediately re-upload today's app usage totals. */
  SYNC_USAGE_STATS,
  /** Forces the child to pull the latest policy bundle (add/remove limits, blocks). */
  SYNC_POLICIES
}
