package com.parentalcontrol.monitoring.repository;

import com.parentalcontrol.monitoring.domain.SignalingMessage;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SignalingMessageRepository extends JpaRepository<SignalingMessage, UUID> {

  List<SignalingMessage> findTop50ByRoomIdOrderByCreatedAtDesc(String roomId);

  @Modifying
  @Query("DELETE FROM SignalingMessage m WHERE m.roomId = :roomId")
  void deleteByRoomId(@Param("roomId") String roomId);

  @Modifying
  @Query("DELETE FROM SignalingMessage m WHERE m.createdAt < :cutoff")
  int deleteByCreatedAtBefore(@Param("cutoff") Instant cutoff);
}
