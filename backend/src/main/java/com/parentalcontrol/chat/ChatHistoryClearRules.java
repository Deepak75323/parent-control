package com.parentalcontrol.chat;

import java.time.Instant;

/** Pure rules for when chat history is hidden after clear / personal clear. */
public final class ChatHistoryClearRules {

  private ChatHistoryClearRules() {}

  /**
   * Effective floor for visible messages: the later of the member's personal clear and the
   * room-wide history clear. Tombstones from bulk clear must not appear — only messages created
   * after this instant are listed.
   */
  public static Instant effectiveClearedAfter(Instant memberChatClearedAt, Instant roomHistoryClearedAt) {
    Instant member = memberChatClearedAt != null ? memberChatClearedAt : Instant.EPOCH;
    Instant room = roomHistoryClearedAt != null ? roomHistoryClearedAt : Instant.EPOCH;
    return member.isAfter(room) ? member : room;
  }
}
