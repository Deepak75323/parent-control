package com.parentalcontrol.monitoring.service;

import java.time.Instant;
import java.util.Objects;

/**
 * Idempotent signaling: identical offer/answer/ICE payloads within a short window
 * must not fail the session with HTTP 400 (child retries / double-fire).
 */
public final class SignalingDuplicatePolicy {

  static final long WINDOW_SECONDS = 120;

  private SignalingDuplicatePolicy() {}

  public static boolean isRecentDuplicate(
      String requestType,
      String requestPayload,
      String storedType,
      String storedPayload,
      Instant storedAt,
      Instant now) {
    if (requestPayload == null || requestPayload.isBlank()) {
      return false;
    }
    if (!Objects.equals(requestType, storedType)) {
      return false;
    }
    if (!Objects.equals(requestPayload, storedPayload)) {
      return false;
    }
    if (storedAt == null || now == null) {
      return false;
    }
    return storedAt.isAfter(now.minusSeconds(WINDOW_SECONDS));
  }
}
