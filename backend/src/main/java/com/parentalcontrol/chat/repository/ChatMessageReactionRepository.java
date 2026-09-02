package com.parentalcontrol.chat.repository;

import com.parentalcontrol.chat.domain.ChatMessageReaction;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChatMessageReactionRepository extends JpaRepository<ChatMessageReaction, UUID> {

  @Query(
      "SELECT r FROM ChatMessageReaction r "
          + "WHERE r.messageId IN :messageIds AND r.deletedAt IS NULL "
          + "ORDER BY r.createdAt ASC")
  List<ChatMessageReaction> findActiveByMessageIdIn(
      @Param("messageIds") Collection<UUID> messageIds);

  @Query(
      "SELECT r FROM ChatMessageReaction r "
          + "WHERE r.messageId = :messageId AND r.deletedAt IS NULL "
          + "ORDER BY r.createdAt ASC")
  List<ChatMessageReaction> findActiveByMessageId(@Param("messageId") UUID messageId);

  @Query(
      "SELECT r FROM ChatMessageReaction r "
          + "WHERE r.messageId = :messageId AND r.userId = :userId AND r.deletedAt IS NULL")
  Optional<ChatMessageReaction> findActiveByMessageIdAndUserId(
      @Param("messageId") UUID messageId, @Param("userId") UUID userId);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      "UPDATE ChatMessageReaction r SET r.deletedAt = :now, r.updatedAt = :now "
          + "WHERE r.messageId = :messageId AND r.deletedAt IS NULL")
  int softDeleteAllByMessageId(@Param("messageId") UUID messageId, @Param("now") Instant now);
}
