package com.parentalcontrol.chat.repository;

import com.parentalcontrol.chat.domain.ChatRoomMember;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChatRoomMemberRepository extends JpaRepository<ChatRoomMember, UUID> {

  /**
   * Active members only. Derived queries alone can still surface soft-deleted rows in some paths;
   * keep the filter explicit (same pattern as {@code ChatRoomRepository.findAllForMember}).
   */
  @Query(
      """
      SELECT m FROM ChatRoomMember m
      WHERE m.roomId = :roomId AND m.userId = :userId AND m.deletedAt IS NULL
      """)
  Optional<ChatRoomMember> findByRoomIdAndUserId(
      @Param("roomId") UUID roomId, @Param("userId") UUID userId);

  @Query(
      """
      SELECT m FROM ChatRoomMember m
      WHERE m.roomId = :roomId AND m.deletedAt IS NULL
      """)
  List<ChatRoomMember> findByRoomId(@Param("roomId") UUID roomId);

  @Query(
      """
      SELECT CASE WHEN COUNT(m) > 0 THEN true ELSE false END FROM ChatRoomMember m
      WHERE m.roomId = :roomId AND m.userId = :userId AND m.deletedAt IS NULL
      """)
  boolean existsByRoomIdAndUserId(@Param("roomId") UUID roomId, @Param("userId") UUID userId);

  /** Includes soft-deleted rows (for leave → re-invite revive). */
  @Query(
      value =
          "SELECT * FROM chat_room_members WHERE room_id = :roomId AND user_id = :userId LIMIT 1",
      nativeQuery = true)
  Optional<ChatRoomMember> findAnyByRoomIdAndUserId(
      @Param("roomId") UUID roomId, @Param("userId") UUID userId);
}
