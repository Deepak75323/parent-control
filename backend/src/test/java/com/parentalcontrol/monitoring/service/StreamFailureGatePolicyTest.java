package com.parentalcontrol.monitoring.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.parentalcontrol.monitoring.domain.ScreenSessionStatus;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class StreamFailureGatePolicyTest {

  private final Instant t0 = Instant.parse("2026-08-15T16:00:00Z");

  @Test
  void ignoresPrematureWebRtcFailureWhilePendingInsideFairWindow() {
    Instant started = t0;
    Instant now = t0.plusSeconds(2);
    assertThat(
            StreamFailureGatePolicy.decide(
                now,
                started,
                ScreenSessionStatus.PENDING,
                "Error: Connection failed after maximum attempts",
                null))
        .isEqualTo(StreamFailureGatePolicy.Decision.IGNORE_PREMATURE);
  }

  @Test
  void acceptsPrematureLookingFailureAfterFairWindow() {
    Instant started = t0;
    Instant now = t0.plusSeconds(StreamFailureGatePolicy.FAIR_WINDOW_SECONDS + 1);
    assertThat(
            StreamFailureGatePolicy.decide(
                now,
                started,
                ScreenSessionStatus.PENDING,
                "Error: Connection failed after maximum attempts",
                null))
        .isEqualTo(StreamFailureGatePolicy.Decision.ACCEPT);
  }

  @Test
  void acceptsImmediateHardFailuresInsideFairWindow() {
    Instant now = t0.plusSeconds(1);
    assertThat(
            StreamFailureGatePolicy.decide(
                now, t0, ScreenSessionStatus.PENDING, "NO_SCREEN_CAPTURE_CONSENT", null))
        .isEqualTo(StreamFailureGatePolicy.Decision.ACCEPT);
  }

  @Test
  void acceptsCameraInUseFailureInsideFairWindow() {
    Instant now = t0.plusSeconds(1);
    assertThat(
            StreamFailureGatePolicy.decide(
                now,
                t0,
                ScreenSessionStatus.PENDING,
                "CAMERA_IN_USE_BY_OTHER_APP",
                null))
        .isEqualTo(StreamFailureGatePolicy.Decision.ACCEPT);
  }

  @Test
  void ignoresFailureWhenNewerLiveSessionExists() {
    Instant olderStart = t0;
    Instant newerStart = t0.plusSeconds(30);
    Instant now = t0.plusSeconds(40);
    assertThat(
            StreamFailureGatePolicy.decide(
                now, olderStart, ScreenSessionStatus.PENDING, "ICE_FAILED", newerStart))
        .isEqualTo(StreamFailureGatePolicy.Decision.IGNORE_STALE_SESSION);
  }

  @Test
  void acceptsFailureForActiveSessionInsideFairWindow() {
    // Offer already landed (ACTIVE) — fair-window gate applies only to PENDING.
    assertThat(
            StreamFailureGatePolicy.decide(
                t0.plusSeconds(1),
                t0,
                ScreenSessionStatus.ACTIVE,
                "Error: Connection failed after maximum attempts",
                null))
        .isEqualTo(StreamFailureGatePolicy.Decision.ACCEPT);
  }
}
