package com.parentalcontrol.chat;

import java.time.Instant;

/** Pure rules for when chat history is hidden after clear / personal clear. */
public final class ChatHistoryClearRules {

  private ChatHistoryClearRules() {}

  /**
   * Effective floor for the normal chat timeline: the later of the member's personal clear and
   * the room-wide history clear. Keeps bulk-cleared notes out of everyday view (no tombstones).
   */
  public static Instant effectiveClearedAfter(Instant memberChatClearedAt, Instant roomHistoryClearedAt) {
    Instant member = memberChatClearedAt != null ? memberChatClearedAt : Instant.EPOCH;
    Instant room = roomHistoryClearedAt != null ? roomHistoryClearedAt : Instant.EPOCH;
    return member.isAfter(room) ? member : room;
  }

  /**
   * Listing floor. Owner "show deleted messages" ignores the clear watermark so clear-history
   * soft-deletes stay reviewable (same retention as manual deletes). Everyone else's timeline
   * still uses {@link #effectiveClearedAfter}.
   */
  public static Instant clearedAfterForListing(
      boolean ownerDeletedView, Instant memberChatClearedAt, Instant roomHistoryClearedAt) {
    if (ownerDeletedView) {
      return Instant.EPOCH;
    }
    return effectiveClearedAfter(memberChatClearedAt, roomHistoryClearedAt);
  }
}
