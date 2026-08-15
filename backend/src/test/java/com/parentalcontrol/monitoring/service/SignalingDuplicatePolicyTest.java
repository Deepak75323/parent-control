package com.parentalcontrol.monitoring.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class SignalingDuplicatePolicyTest {

  @Test
  void matchesIdenticalPayloadInsideWindow() {
    Instant now = Instant.parse("2026-08-15T15:00:00Z");
    Instant stored = now.minusSeconds(30);
    assertThat(
            SignalingDuplicatePolicy.isRecentDuplicate(
                "offer", "{\"type\":\"offer\"}", "offer", "{\"type\":\"offer\"}", stored, now))
        .isTrue();
  }

  @Test
  void doesNotMatchOutsideWindow() {
    Instant now = Instant.parse("2026-08-15T15:00:00Z");
    Instant stored = now.minusSeconds(SignalingDuplicatePolicy.WINDOW_SECONDS + 1);
    assertThat(
            SignalingDuplicatePolicy.isRecentDuplicate(
                "offer", "same", "offer", "same", stored, now))
        .isFalse();
  }

  @Test
  void doesNotMatchDifferentPayload() {
    Instant now = Instant.parse("2026-08-15T15:00:00Z");
    assertThat(
            SignalingDuplicatePolicy.isRecentDuplicate(
                "offer", "sdp-a", "offer", "sdp-b", now.minusSeconds(1), now))
        .isFalse();
  }
}
