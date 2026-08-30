package com.parentalcontrol.chat.repository;

import com.parentalcontrol.chat.domain.ChatRoomInvite;
import com.parentalcontrol.chat.domain.ChatRoomInviteStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatRoomInviteRepository extends JpaRepository<ChatRoomInvite, UUID> {

  List<ChatRoomInvite> findByInviteeUserIdAndStatusOrderByCreatedAtDesc(
      UUID inviteeUserId, ChatRoomInviteStatus status);

  Optional<ChatRoomInvite> findByRoomIdAndInviteeUserIdAndStatus(
      UUID roomId, UUID inviteeUserId, ChatRoomInviteStatus status);

  List<ChatRoomInvite> findByRoomId(UUID roomId);
}
