package com.parentalcontrol.device.domain;

/**
 * MediaProjection consent lifecycle states persisted on {@link
 * Device#getMediaProjectionConsentState()}. Wire format is the enum {@link #name()} (VARCHAR column
 * — no schema change).
 */
public enum MediaProjectionConsentState {
  NEVER_GRANTED,
  GRANTED_ACTIVE,
  LOST_AFTER_PROCESS_DEATH,
  REQUIRES_RECONSENT,
  RECOVERY_PENDING,
  /** Recovery deferred — accessibility not available; WorkManager retry scheduled. */
  RECOVERY_DEFERRED,
  RECOVERY_FAILED;

  public static MediaProjectionConsentState fromWire(String raw) {
    if (raw == null || raw.isBlank()) {
      return NEVER_GRANTED;
    }
    try {
      return MediaProjectionConsentState.valueOf(raw.trim());
    } catch (IllegalArgumentException ex) {
      return NEVER_GRANTED;
    }
  }

  public boolean equalsWire(String raw) {
    return name().equals(raw);
  }
}
