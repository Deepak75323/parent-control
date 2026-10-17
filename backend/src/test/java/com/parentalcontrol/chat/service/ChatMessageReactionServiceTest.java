package com.parentalcontrol.chat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.parentalcontrol.chat.domain.ChatMessage;
import com.parentalcontrol.chat.domain.ChatMessageReaction;
import com.parentalcontrol.chat.domain.ChatMessageType;
import com.parentalcontrol.chat.dto.ChatMessageReactionSummary;
import com.parentalcontrol.chat.repository.ChatMessageReactionRepository;
import com.parentalcontrol.chat.repository.ChatMessageRepository;
import com.parentalcontrol.common.exception.BusinessException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatMessageReactionServiceTest {

  @Mock private ChatMessageReactionRepository reactionRepository;
  @Mock private ChatMessageRepository messageRepository;

  @InjectMocks private ChatMessageReactionService reactionService;

  @Test
  void toggleReactionAddsNewEmoji() {
    UUID roomId = UUID.randomUUID();
    UUID messageId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();

    when(messageRepository.existsActiveInRoom(messageId, roomId)).thenReturn(true);
    when(reactionRepository.findByMessageIdAndUserId(messageId, userId))
        .thenReturn(java.util.Optional.empty());
    when(reactionRepository.save(any(ChatMessageReaction.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(reactionRepository.findActiveByMessageId(messageId))
        .thenReturn(
            List.of(
                ChatMessageReaction.builder()
                    .roomId(roomId)
                    .messageId(messageId)
                    .userId(userId)
                    .emoji("👍")
                    .build()));

    List<ChatMessageReactionSummary> summaries =
        reactionService.toggleReaction(roomId, messageId, userId, "👍", id -> "Parent");

    assertThat(summaries).hasSize(1);
    assertThat(summaries.get(0).getEmoji()).isEqualTo("👍");
    assertThat(summaries.get(0).isReactedByMe()).isTrue();
    verify(reactionRepository).save(any(ChatMessageReaction.class));
  }

  @Test
  void toggleReactionRemovesSameEmoji() {
    UUID roomId = UUID.randomUUID();
    UUID messageId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    ChatMessageReaction existing =
        ChatMessageReaction.builder()
            .roomId(roomId)
            .messageId(messageId)
            .userId(userId)
            .emoji("❤️")
            .build();

    when(messageRepository.existsActiveInRoom(messageId, roomId)).thenReturn(true);
    when(reactionRepository.findByMessageIdAndUserId(messageId, userId))
        .thenReturn(java.util.Optional.of(existing));
    when(reactionRepository.findActiveByMessageId(messageId)).thenReturn(List.of());

    List<ChatMessageReactionSummary> summaries =
        reactionService.toggleReaction(roomId, messageId, userId, "❤️", id -> "Parent");

    assertThat(summaries).isEmpty();
    assertThat(existing.getDeletedAt()).isNotNull();
  }

  @Test
  void toggleReactionRevivesSoftDeletedReaction() {
    UUID roomId = UUID.randomUUID();
    UUID messageId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    ChatMessageReaction existing =
        ChatMessageReaction.builder()
            .roomId(roomId)
            .messageId(messageId)
            .userId(userId)
            .emoji("👍")
            .build();
    existing.setDeletedAt(java.time.Instant.now());

    when(messageRepository.existsActiveInRoom(messageId, roomId)).thenReturn(true);
    when(reactionRepository.findByMessageIdAndUserId(messageId, userId))
        .thenReturn(java.util.Optional.of(existing));
    when(reactionRepository.save(any(ChatMessageReaction.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(reactionRepository.findActiveByMessageId(messageId)).thenReturn(List.of(existing));

    List<ChatMessageReactionSummary> summaries =
        reactionService.toggleReaction(roomId, messageId, userId, "😂", id -> "Parent");

    assertThat(existing.getDeletedAt()).isNull();
    assertThat(existing.getEmoji()).isEqualTo("😂");
    assertThat(summaries).hasSize(1);
    assertThat(summaries.get(0).getEmoji()).isEqualTo("😂");
  }

  @Test
  void rejectsBlankEmoji() {
    UUID roomId = UUID.randomUUID();
    UUID messageId = UUID.randomUUID();

    when(messageRepository.existsActiveInRoom(messageId, roomId)).thenReturn(true);

    assertThatThrownBy(
            () ->
                reactionService.toggleReaction(
                    roomId, messageId, UUID.randomUUID(), "  ", id -> "Parent"))
        .isInstanceOf(BusinessException.class);
  }

  @Test
  void groupsReactionsByEmoji() {
    UUID messageId = UUID.randomUUID();
    UUID viewerId = UUID.randomUUID();
    ChatMessageReaction first =
        ChatMessageReaction.builder()
            .messageId(messageId)
            .userId(viewerId)
            .emoji("👍")
            .build();
    ChatMessageReaction second =
        ChatMessageReaction.builder()
            .messageId(messageId)
            .userId(UUID.randomUUID())
            .emoji("👍")
            .build();

    when(reactionRepository.findActiveByMessageIdIn(List.of(messageId)))
        .thenReturn(List.of(first, second));

    Map<UUID, List<ChatMessageReactionSummary>> grouped =
        reactionService.summariesByMessageIds(List.of(messageId), viewerId, id -> "Parent");

    assertThat(grouped.get(messageId)).hasSize(1);
    assertThat(grouped.get(messageId).get(0).getCount()).isEqualTo(2);
    assertThat(grouped.get(messageId).get(0).isReactedByMe()).isTrue();
  }
}
