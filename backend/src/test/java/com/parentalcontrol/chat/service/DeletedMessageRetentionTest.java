package com.parentalcontrol.chat.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class DeletedMessageRetentionTest {

  private final Instant now = Instant.parse("2026-09-26T16:00:00Z");

  @Test
  void keepsADeletedBodyUntilTwentyFourHoursHavePassed() {
    assertFalse(DeletedMessageRetention.bodyExpired(null, now));
    assertFalse(DeletedMessageRetention.bodyExpired(now.minusSeconds(23 * 3600), now));
  }

  @Test
  void expiresADeletedBodyAfterTwentyFourHours() {
    assertTrue(DeletedMessageRetention.bodyExpired(now.minusSeconds(24 * 3600), now));
    assertTrue(DeletedMessageRetention.bodyExpired(now.minusSeconds(25 * 3600), now));
  }
}
