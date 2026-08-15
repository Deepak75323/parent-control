package com.parentalcontrol.monitoring.service;

import com.parentalcontrol.monitoring.domain.ScreenSessionStatus;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;

/**
 * Gates child {@code reportStreamFailure} so a premature / stale report cannot
 * poison a live session (Oracle: FAILED ~200ms after REQUEST with "max attempts").
 */
public final class StreamFailureGatePolicy {

  /** Minimum age before WebRTC-style failures may mark a PENDING session FAILED. */
  public static final long FAIR_WINDOW_SECONDS = 8;

  public enum Decision {
    ACCEPT,
    IGNORE_PREMATURE,
    IGNORE_STALE_SESSION
  }

  private StreamFailureGatePolicy() {}

  /**
   * @param newerLiveSessionStartedAt startedAt of a newer PENDING/ACTIVE session on the same
   *     device, or null if none
   */
  public static Decision decide(
      Instant now,
      Instant sessionStartedAt,
      ScreenSessionStatus status,
      String reason,
      Instant newerLiveSessionStartedAt) {
    if (sessionStartedAt != null
        && newerLiveSessionStartedAt != null
        && newerLiveSessionStartedAt.isAfter(sessionStartedAt)) {
      return Decision.IGNORE_STALE_SESSION;
    }
    if (status == ScreenSessionStatus.PENDING
        && sessionStartedAt != null
        && now != null
        && Duration.between(sessionStartedAt, now).getSeconds() < FAIR_WINDOW_SECONDS
        && !isImmediateHardFailure(reason)) {
      return Decision.IGNORE_PREMATURE;
    }
    return Decision.ACCEPT;
  }

  /**
   * Permission / FGS / consent failures should fail fast; WebRTC negotiation failures need a
   * fair window while PENDING (before child offer).
   */
  public static boolean isImmediateHardFailure(String reason) {
    if (reason == null || reason.isBlank()) {
      return false;
    }
    String r = reason.toUpperCase(Locale.ROOT);
    return r.contains("ACCESSIBILITY_DISABLED")
        || r.contains("NO_SCREEN_CAPTURE_CONSENT")
        || r.contains("PROJECTION_")
        || r.contains("FGS_")
        || r.contains("INVALID_STREAM_REQUEST")
        || r.contains("CAPTURE_BUSY")
        || r.contains("AUDIO_FOCUS_DENIED")
        || r.contains("NO_CAPTURE_FRAMES");
  }
}
