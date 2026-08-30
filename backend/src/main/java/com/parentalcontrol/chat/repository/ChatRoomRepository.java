package com.parentalcontrol.chat.repository;

import com.parentalcontrol.chat.domain.ChatRoom;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ChatRoomRepository extends JpaRepository<ChatRoom, UUID> {

  @Query(
      """
      SELECT DISTINCT r FROM ChatRoom r
      INNER JOIN ChatRoomMember m ON m.roomId = r.id AND m.userId = :userId
      WHERE r.deletedAt IS NULL AND m.deletedAt IS NULL
      ORDER BY r.updatedAt DESC
      """)
  List<ChatRoom> findAllForMember(UUID userId);

  @Query(
      value = "SELECT COUNT(*) > 0 FROM chat_rooms WHERE id = :roomId AND deleted_at IS NOT NULL",
      nativeQuery = true)
  boolean isSoftDeleted(UUID roomId);
}
