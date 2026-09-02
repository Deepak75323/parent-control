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
      "SELECT m FROM ChatMessage m WHERE m.roomId = :roomId AND m.deletedAt IS NULL "
          + "ORDER BY m.createdAt DESC")
  Page<ChatMessage> findActiveByRoomIdOrderByCreatedAtDesc(
      @Param("roomId") UUID roomId, Pageable pageable);

  @Query("SELECT m FROM ChatMessage m WHERE m.roomId = :roomId AND m.deletedAt IS NULL")
  List<ChatMessage> findActiveByRoomId(@Param("roomId") UUID roomId);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      "UPDATE ChatMessage m SET m.deletedAt = :now, m.updatedAt = :now "
          + "WHERE m.roomId = :roomId AND m.deletedAt IS NULL")
  int softDeleteAllByRoomId(@Param("roomId") UUID roomId, @Param("now") Instant now);
}
