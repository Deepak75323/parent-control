package com.parentalcontrol.chat.repository;

import com.parentalcontrol.chat.domain.ChatRoomCall;
import com.parentalcontrol.chat.domain.ChatRoomCallStatus;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatRoomCallRepository extends JpaRepository<ChatRoomCall, UUID> {

  Optional<ChatRoomCall> findFirstByRoomIdAndStatusOrderByCreatedAtDesc(
      UUID roomId, ChatRoomCallStatus status);

  Optional<ChatRoomCall> findFirstByChannelIdAndStatusOrderByCreatedAtDesc(
      UUID channelId, ChatRoomCallStatus status);
}
