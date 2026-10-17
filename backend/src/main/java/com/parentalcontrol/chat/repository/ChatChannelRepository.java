package com.parentalcontrol.chat.repository;

import com.parentalcontrol.chat.domain.ChatChannel;
import com.parentalcontrol.chat.domain.ChatChannelType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChatChannelRepository extends JpaRepository<ChatChannel, UUID> {

  @Query(
      "SELECT c FROM ChatChannel c WHERE c.roomId = :roomId AND c.deletedAt IS NULL "
          + "ORDER BY c.position ASC, c.createdAt ASC")
  List<ChatChannel> findActiveByRoomId(@Param("roomId") UUID roomId);

  @Query(
      "SELECT c FROM ChatChannel c WHERE c.roomId IN :roomIds AND c.deletedAt IS NULL "
          + "ORDER BY c.position ASC, c.createdAt ASC")
  List<ChatChannel> findActiveByRoomIdIn(@Param("roomIds") Collection<UUID> roomIds);

  @Query(
      "SELECT c FROM ChatChannel c WHERE c.id = :id AND c.roomId = :roomId AND c.deletedAt IS NULL")
  Optional<ChatChannel> findActiveByIdAndRoomId(
      @Param("id") UUID id, @Param("roomId") UUID roomId);

  @Query(
      "SELECT c FROM ChatChannel c WHERE c.roomId = :roomId AND c.channelType = :type "
          + "AND c.deletedAt IS NULL ORDER BY c.position ASC, c.createdAt ASC")
  List<ChatChannel> findActiveByRoomIdAndType(
      @Param("roomId") UUID roomId, @Param("type") ChatChannelType type);

  @Query(
      "SELECT COUNT(c) FROM ChatChannel c WHERE c.roomId = :roomId AND c.deletedAt IS NULL")
  long countActiveByRoomId(@Param("roomId") UUID roomId);

  @Query(
      "SELECT COUNT(c) > 0 FROM ChatChannel c WHERE c.roomId = :roomId "
          + "AND lower(c.name) = lower(:name) AND c.deletedAt IS NULL")
  boolean existsActiveNameIgnoreCase(
      @Param("roomId") UUID roomId, @Param("name") String name);
}
