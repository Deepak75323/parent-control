package com.parentalcontrol.chat.service;

import java.time.Duration;
import java.time.Instant;

/** How long a deleted-for-everyone chat body stays available to the room owner. */
final class DeletedMessageRetention {

  static final Duration RETENTION = Duration.ofHours(24);

  private DeletedMessageRetention() {}

  static boolean bodyExpired(Instant deletedAt, Instant now) {
    if (deletedAt == null || now == null) {
      return false;
    }
    return !deletedAt.plus(RETENTION).isAfter(now);
  }
}
