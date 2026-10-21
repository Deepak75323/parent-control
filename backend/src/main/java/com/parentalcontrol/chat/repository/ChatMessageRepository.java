package com.parentalcontrol.chat.repository;

import com.parentalcontrol.chat.domain.ChatMessage;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, UUID> {

  /**
   * Live messages, plus delete-for-everyone tombstones still inside the read grace window. The
   * sender sees the notice for that window after deleting. A recipient sees it until they read
   * it, then for the same window. Native so soft-deleted rows are not dropped.
   */
  @Query(
      value =
          "SELECT m.* FROM chat_messages m WHERE m.room_id = :roomId AND m.channel_id = :channelId "
              + "AND m.created_at > :clearedAfter "
              + "AND ("
              + "m.deleted_at IS NULL "
              + "OR (m.sender_user_id = :userId AND m.deleted_at > :tombstoneVisibleAfter) "
              + "OR EXISTS ("
              + "SELECT 1 FROM chat_message_receipts r WHERE r.message_id = m.id "
              + "AND r.recipient_user_id = :userId AND r.deleted_at IS NULL "
              + "AND (r.read_at IS NULL OR r.read_at > :tombstoneVisibleAfter))"
              + ") "
              + "AND NOT EXISTS ("
              + "SELECT 1 FROM chat_message_hides h WHERE h.message_id = m.id "
              + "AND h.user_id = :userId AND h.deleted_at IS NULL) "
              + "ORDER BY m.created_at DESC",
      countQuery =
          "SELECT COUNT(*) FROM chat_messages m WHERE m.room_id = :roomId AND m.channel_id = :channelId "
              + "AND m.created_at > :clearedAfter "
              + "AND ("
              + "m.deleted_at IS NULL "
              + "OR (m.sender_user_id = :userId AND m.deleted_at > :tombstoneVisibleAfter) "
              + "OR EXISTS ("
              + "SELECT 1 FROM chat_message_receipts r WHERE r.message_id = m.id "
              + "AND r.recipient_user_id = :userId AND r.deleted_at IS NULL "
              + "AND (r.read_at IS NULL OR r.read_at > :tombstoneVisibleAfter))"
              + ") "
              + "AND NOT EXISTS ("
              + "SELECT 1 FROM chat_message_hides h WHERE h.message_id = m.id "
              + "AND h.user_id = :userId AND h.deleted_at IS NULL)",
      nativeQuery = true)
  Page<ChatMessage> findVisibleForMemberInChannelOrderByCreatedAtDesc(
      @Param("roomId") UUID roomId,
      @Param("channelId") UUID channelId,
      @Param("userId") UUID userId,
      @Param("clearedAfter") Instant clearedAfter,
      @Param("tombstoneVisibleAfter") Instant tombstoneVisibleAfter,
      Pageable pageable);

  @Query(
      value =
          "SELECT m FROM ChatMessage m WHERE m.roomId = :roomId AND m.deletedAt IS NULL "
              + "AND m.createdAt > :clearedAfter "
              + "AND NOT EXISTS ("
              + "SELECT 1 FROM ChatMessageHide h WHERE h.messageId = m.id "
              + "AND h.userId = :userId AND h.deletedAt IS NULL)",
      countQuery =
          "SELECT COUNT(m) FROM ChatMessage m WHERE m.roomId = :roomId AND m.deletedAt IS NULL "
              + "AND m.createdAt > :clearedAfter "
              + "AND NOT EXISTS ("
              + "SELECT 1 FROM ChatMessageHide h WHERE h.messageId = m.id "
              + "AND h.userId = :userId AND h.deletedAt IS NULL)")
  Page<ChatMessage> findVisibleForMemberOrderByCreatedAtDesc(
      @Param("roomId") UUID roomId,
      @Param("userId") UUID userId,
      @Param("clearedAfter") Instant clearedAfter,
      Pageable pageable);

  @Query("SELECT m FROM ChatMessage m WHERE m.roomId = :roomId AND m.deletedAt IS NULL")
  List<ChatMessage> findActiveByRoomId(@Param("roomId") UUID roomId);

  /**
   * Room-owner view: includes messages soft-deleted for everyone. Native so the deleted_at
   * restriction does not drop those rows. Per-user hides stay excluded.
   */
  @Query(
      value =
          "SELECT m.* FROM chat_messages m WHERE m.room_id = :roomId "
              + "AND m.channel_id = :channelId AND m.created_at > :clearedAfter "
              + "AND (m.deleted_at IS NULL OR m.deleted_at > :deletedVisibleAfter) "
              + "AND NOT EXISTS ("
              + "SELECT 1 FROM chat_message_hides h WHERE h.message_id = m.id "
              + "AND h.user_id = :userId AND h.deleted_at IS NULL) "
              + "ORDER BY m.created_at DESC",
      countQuery =
          "SELECT COUNT(*) FROM chat_messages m WHERE m.room_id = :roomId "
              + "AND m.channel_id = :channelId AND m.created_at > :clearedAfter "
              + "AND (m.deleted_at IS NULL OR m.deleted_at > :deletedVisibleAfter) "
              + "AND NOT EXISTS ("
              + "SELECT 1 FROM chat_message_hides h WHERE h.message_id = m.id "
              + "AND h.user_id = :userId AND h.deleted_at IS NULL)",
      nativeQuery = true)
  Page<ChatMessage> findForOwnerIncludingDeletedInChannel(
      @Param("roomId") UUID roomId,
      @Param("channelId") UUID channelId,
      @Param("userId") UUID userId,
      @Param("clearedAfter") Instant clearedAfter,
      @Param("deletedVisibleAfter") Instant deletedVisibleAfter,
      Pageable pageable);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      "UPDATE ChatMessage m SET m.deletedAt = :now, m.updatedAt = :now "
          + "WHERE m.roomId = :roomId AND m.deletedAt IS NULL")
  int softDeleteAllByRoomId(@Param("roomId") UUID roomId, @Param("now") Instant now);

  @Query(
      value =
          "SELECT COUNT(*) > 0 FROM chat_messages "
              + "WHERE id = :messageId AND room_id = :roomId AND deleted_at IS NULL",
      nativeQuery = true)
  boolean existsActiveInRoom(
      @Param("messageId") UUID messageId, @Param("roomId") UUID roomId);

  @Query(value = "SELECT * FROM chat_messages WHERE id = :id", nativeQuery = true)
  java.util.Optional<ChatMessage> findIncludingDeletedById(@Param("id") UUID id);

  @Query(
      value =
          "SELECT m.id FROM chat_messages m WHERE m.room_id = :roomId "
              + "AND m.created_at <= :createdAt AND m.sender_user_id <> :recipientUserId "
              + "AND ("
              + "m.deleted_at IS NULL "
              + "OR EXISTS ("
              + "SELECT 1 FROM chat_message_receipts r WHERE r.message_id = m.id "
              + "AND r.recipient_user_id = :recipientUserId AND r.read_at IS NULL "
              + "AND r.deleted_at IS NULL)"
              + ")",
      nativeQuery = true)
  List<UUID> findIdsThroughForRecipient(
      @Param("roomId") UUID roomId,
      @Param("recipientUserId") UUID recipientUserId,
      @Param("createdAt") Instant createdAt);

  @Query(
      value =
          "SELECT m.* FROM chat_messages m WHERE m.room_id = :roomId AND m.deleted_at IS NOT NULL "
              + "AND EXISTS ("
              + "SELECT 1 FROM chat_message_receipts r WHERE r.message_id = m.id "
              + "AND r.recipient_user_id = :userId AND r.read_at IS NULL AND r.deleted_at IS NULL)",
      nativeQuery = true)
  List<ChatMessage> findUnreadDeletedForRecipient(
      @Param("roomId") UUID roomId, @Param("userId") UUID userId);

  @Query(
      value = "SELECT * FROM chat_messages WHERE id = :messageId AND room_id = :roomId",
      nativeQuery = true)
  java.util.Optional<ChatMessage> findIncludingDeletedByIdAndRoomId(
      @Param("messageId") UUID messageId, @Param("roomId") UUID roomId);

  @Query(
      value =
          "SELECT m.id FROM chat_messages m "
              + "WHERE m.deleted_at IS NULL "
              + "AND EXISTS (SELECT 1 FROM chat_message_receipts r WHERE r.message_id = m.id) "
              + "AND NOT EXISTS ("
              + "  SELECT 1 FROM chat_message_receipts r "
              + "  WHERE r.message_id = m.id AND r.read_at IS NULL"
              + ") "
              + "AND ("
              + "  SELECT MAX(r.read_at) FROM chat_message_receipts r WHERE r.message_id = m.id"
              + ") <= :cutoff "
              + "LIMIT 200",
      nativeQuery = true)
  List<UUID> findIdsReadyForAutoDelete(@Param("cutoff") Instant cutoff);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      value =
          "UPDATE chat_messages SET body = '' "
              + "WHERE deleted_at IS NOT NULL AND deleted_at <= :cutoff AND body <> ''",
      nativeQuery = true)
  int blankDeletedBodiesBefore(@Param("cutoff") Instant cutoff);

  /**
   * Forgot deleted-messages PIN: expire reviewable deleted notes in rooms this parent owns so they
   * no longer appear in the owner deleted view.
   */
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      value =
          "UPDATE chat_messages SET body = '', deleted_at = TIMESTAMP '1970-01-01 00:00:00+00' "
              + "WHERE deleted_at IS NOT NULL AND room_id IN ("
              + "  SELECT m.room_id FROM chat_room_members m "
              + "  WHERE m.user_id = :userId AND m.role = 'OWNER' AND m.deleted_at IS NULL"
              + ")",
      nativeQuery = true)
  int expireDeletedMessagesInOwnedRooms(@Param("userId") UUID userId);
}
