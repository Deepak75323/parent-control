package com.parentalcontrol.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class ChatHistoryClearRulesTest {

  @Test
  void prefersLaterOfMemberAndRoomClear() {
    Instant earlier = Instant.parse("2026-10-25T10:00:00Z");
    Instant later = Instant.parse("2026-10-25T12:00:00Z");
    assertEquals(later, ChatHistoryClearRules.effectiveClearedAfter(earlier, later));
    assertEquals(later, ChatHistoryClearRules.effectiveClearedAfter(later, earlier));
  }

  @Test
  void nullsBecomeEpoch() {
    assertEquals(Instant.EPOCH, ChatHistoryClearRules.effectiveClearedAfter(null, null));
    Instant t = Instant.parse("2026-10-25T12:00:00Z");
    assertEquals(t, ChatHistoryClearRules.effectiveClearedAfter(t, null));
    assertEquals(t, ChatHistoryClearRules.effectiveClearedAfter(null, t));
  }

  @Test
  void ownerDeletedViewIgnoresClearWatermarkSoClearHistoryNotesRemainReviewable() {
    Instant cleared = Instant.parse("2026-10-25T12:00:00Z");
    assertEquals(
        Instant.EPOCH,
        ChatHistoryClearRules.clearedAfterForListing(true, cleared, cleared));
    assertEquals(
        cleared,
        ChatHistoryClearRules.clearedAfterForListing(false, cleared, null));
  }
}
