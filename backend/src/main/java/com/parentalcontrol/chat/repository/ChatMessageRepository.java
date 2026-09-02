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

  @Query(
      value = "SELECT * FROM chat_messages WHERE id = :messageId AND room_id = :roomId",
      nativeQuery = true)
  java.util.Optional<ChatMessage> findIncludingDeletedByIdAndRoomId(
      @Param("messageId") UUID messageId, @Param("roomId") UUID roomId);
}
