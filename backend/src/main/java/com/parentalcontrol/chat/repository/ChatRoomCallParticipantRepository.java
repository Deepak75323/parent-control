package com.parentalcontrol.chat.repository;

import com.parentalcontrol.chat.domain.ChatRoomCallParticipant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChatRoomCallParticipantRepository
    extends JpaRepository<ChatRoomCallParticipant, UUID> {

  @Query(
      """
      SELECT p FROM ChatRoomCallParticipant p
      WHERE p.callId = :callId AND p.leftAt IS NULL
      """)
  List<ChatRoomCallParticipant> findActiveByCallId(@Param("callId") UUID callId);

  Optional<ChatRoomCallParticipant> findByCallIdAndUserId(UUID callId, UUID userId);

  @Query(
      """
      SELECT COUNT(p) FROM ChatRoomCallParticipant p
      WHERE p.callId = :callId AND p.leftAt IS NULL
      """)
  long countActiveByCallId(@Param("callId") UUID callId);
}
