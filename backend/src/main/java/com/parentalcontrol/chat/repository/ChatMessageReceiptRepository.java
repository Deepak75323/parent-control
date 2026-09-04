package com.parentalcontrol.chat.repository;

import com.parentalcontrol.chat.domain.ChatMessageReceipt;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChatMessageReceiptRepository extends JpaRepository<ChatMessageReceipt, UUID> {

  List<ChatMessageReceipt> findByMessageId(UUID messageId);

  List<ChatMessageReceipt> findByMessageIdIn(Collection<UUID> messageIds);

  Optional<ChatMessageReceipt> findByMessageIdAndRecipientUserId(
      UUID messageId, UUID recipientUserId);

  @Modifying
  @Query(
      """
      UPDATE ChatMessageReceipt r
      SET r.deliveredAt = :now, r.updatedAt = :now
      WHERE r.recipientUserId = :recipientUserId
        AND r.messageId IN :messageIds
        AND r.deliveredAt IS NULL
      """)
  int markDelivered(
      @Param("recipientUserId") UUID recipientUserId,
      @Param("messageIds") Collection<UUID> messageIds,
      @Param("now") Instant now);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      """
      UPDATE ChatMessageReceipt r
      SET r.readAt = :now,
          r.deliveredAt = COALESCE(r.deliveredAt, :now),
          r.updatedAt = :now
      WHERE r.recipientUserId = :recipientUserId
        AND r.messageId IN :messageIds
        AND r.readAt IS NULL
      """)
  int markRead(
      @Param("recipientUserId") UUID recipientUserId,
      @Param("messageIds") Collection<UUID> messageIds,
      @Param("now") Instant now);
}
