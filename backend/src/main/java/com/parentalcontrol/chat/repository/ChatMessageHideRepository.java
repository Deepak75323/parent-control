package com.parentalcontrol.chat.repository;

import com.parentalcontrol.chat.domain.ChatMessageHide;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatMessageHideRepository extends JpaRepository<ChatMessageHide, UUID> {

  Optional<ChatMessageHide> findByUserIdAndMessageId(UUID userId, UUID messageId);

  boolean existsByUserIdAndMessageIdAndDeletedAtIsNull(UUID userId, UUID messageId);
}
