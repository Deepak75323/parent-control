package com.parentalcontrol.chat.repository;

import com.parentalcontrol.chat.domain.ChatMessage;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, UUID> {

  Page<ChatMessage> findByRoomIdOrderByCreatedAtDesc(UUID roomId, Pageable pageable);

  List<ChatMessage> findByRoomId(UUID roomId);
}
