package com.parentalcontrol.chat.repository;

import com.parentalcontrol.chat.domain.ChatRoomMember;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatRoomMemberRepository extends JpaRepository<ChatRoomMember, UUID> {

  Optional<ChatRoomMember> findByRoomIdAndUserId(UUID roomId, UUID userId);

  List<ChatRoomMember> findByRoomId(UUID roomId);

  boolean existsByRoomIdAndUserId(UUID roomId, UUID userId);
}
