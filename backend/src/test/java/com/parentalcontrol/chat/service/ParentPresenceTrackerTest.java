package com.parentalcontrol.chat.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ParentPresenceTrackerTest {

  private ParentPresenceTracker tracker;

  @BeforeEach
  void setUp() {
    tracker = new ParentPresenceTracker(null);
    tracker.clearLocalForTests();
  }

  @Test
  void firstSessionMakesUserOnline() {
    UUID userId = UUID.randomUUID();
    ParentPresenceTracker.Transition t = tracker.onSessionConnected(userId, "s1");
    assertTrue(t.onlineChanged());
    assertTrue(t.nowOnline());
    assertTrue(tracker.snapshot(userId).online());
  }

  @Test
  void secondSessionDoesNotFlipOnlineAgain() {
    UUID userId = UUID.randomUUID();
    tracker.onSessionConnected(userId, "s1");
    ParentPresenceTracker.Transition t = tracker.onSessionConnected(userId, "s2");
    assertFalse(t.onlineChanged());
    assertTrue(t.nowOnline());
  }

  @Test
  void lastSessionDisconnectGoesOffline() {
    UUID userId = UUID.randomUUID();
    tracker.onSessionConnected(userId, "s1");
    tracker.onSessionConnected(userId, "s2");
    ParentPresenceTracker.Transition mid = tracker.onSessionDisconnected(userId, "s1");
    assertFalse(mid.onlineChanged());
    assertTrue(mid.nowOnline());

    ParentPresenceTracker.Transition last = tracker.onSessionDisconnected(userId, "s2");
    assertTrue(last.onlineChanged());
    assertFalse(last.nowOnline());
    assertFalse(tracker.snapshot(userId).online());
    assertEquals(last.lastSeenAt(), tracker.snapshot(userId).lastSeenAt());
  }

  @Test
  void disconnectBySessionIdAloneClearsGhostWhenPrincipalMissing() {
    UUID userId = UUID.randomUUID();
    tracker.onSessionConnected(userId, "ghost-session");
    assertTrue(tracker.snapshot(userId).online());

    ParentPresenceTracker.Transition t = tracker.onSessionDisconnectedBySessionId("ghost-session");
    assertTrue(t.onlineChanged());
    assertFalse(t.nowOnline());
    assertFalse(tracker.snapshot(userId).online());
  }

  @Test
  void forceOfflineClearsTrackedSessions() {
    UUID userId = UUID.randomUUID();
    tracker.onSessionConnected(userId, "s1");
    ParentPresenceTracker.Transition t = tracker.forceOffline(userId);
    assertTrue(t.onlineChanged());
    assertFalse(tracker.snapshot(userId).online());
  }
}
