package com.parentalcontrol.chat.service;

import com.parentalcontrol.chat.domain.ChatMessage;
import com.parentalcontrol.chat.domain.ChatMessageReaction;
import com.parentalcontrol.chat.dto.ChatMessageReactionSummary;
import com.parentalcontrol.chat.repository.ChatMessageReactionRepository;
import com.parentalcontrol.chat.repository.ChatMessageRepository;
import com.parentalcontrol.common.constant.MessageConstants;
import com.parentalcontrol.common.exception.BusinessException;
import com.parentalcontrol.common.exception.ErrorCode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class ChatMessageReactionService {

  private final ChatMessageReactionRepository reactionRepository;
  private final ChatMessageRepository messageRepository;

  @Transactional(readOnly = true)
  public Map<UUID, List<ChatMessageReactionSummary>> summariesByMessageIds(
      Collection<UUID> messageIds, UUID viewerUserId, Function<UUID, String> labelForUser) {
    if (messageIds.isEmpty()) {
      return Map.of();
    }
    List<ChatMessageReaction> reactions =
        reactionRepository.findActiveByMessageIdIn(messageIds);
    Map<UUID, List<ChatMessageReaction>> grouped =
        reactions.stream().collect(Collectors.groupingBy(ChatMessageReaction::getMessageId));
    Map<UUID, List<ChatMessageReactionSummary>> result = new LinkedHashMap<>();
    for (UUID messageId : messageIds) {
      result.put(
          messageId,
          summarize(grouped.getOrDefault(messageId, List.of()), viewerUserId, labelForUser));
    }
    return result;
  }

  @Transactional
  public List<ChatMessageReactionSummary> toggleReaction(
      UUID roomId, UUID messageId, UUID userId, String rawEmoji, Function<UUID, String> labelForUser) {
    requireActiveMessage(roomId, messageId);
    String emoji = normalizeEmoji(rawEmoji);

    ChatMessageReaction existing =
        reactionRepository
            .findActiveByMessageIdAndUserId(messageId, userId)
            .orElse(null);

    if (existing != null && existing.getEmoji().equals(emoji)) {
      existing.setDeletedAt(Instant.now());
      reactionRepository.save(existing);
    } else if (existing != null) {
      existing.setEmoji(emoji);
      existing.setUpdatedAt(Instant.now());
      reactionRepository.save(existing);
    } else {
      reactionRepository.save(
          ChatMessageReaction.builder()
              .roomId(roomId)
              .messageId(messageId)
              .userId(userId)
              .emoji(emoji)
              .build());
    }

    return summarize(reactionRepository.findActiveByMessageId(messageId), userId, labelForUser);
  }

  @Transactional
  public void deleteReactionsForMessage(UUID messageId) {
    reactionRepository.softDeleteAllByMessageId(messageId, Instant.now());
  }

  private void requireActiveMessage(UUID roomId, UUID messageId) {
    if (!messageRepository.existsActiveInRoom(messageId, roomId)) {
      throw new BusinessException(ErrorCode.NOT_FOUND, MessageConstants.CHAT_MESSAGE_NOT_FOUND);
    }
  }

  static String normalizeEmoji(String rawEmoji) {
    if (!StringUtils.hasText(rawEmoji)) {
      throw new BusinessException(ErrorCode.VALIDATION_ERROR, MessageConstants.CHAT_REACTION_EMOJI_REQUIRED);
    }
    String emoji = rawEmoji.trim();
    if (emoji.length() > 32) {
      throw new BusinessException(ErrorCode.VALIDATION_ERROR, MessageConstants.CHAT_REACTION_EMOJI_INVALID);
    }
    return emoji;
  }

  private static List<ChatMessageReactionSummary> summarize(
      List<ChatMessageReaction> reactions,
      UUID viewerUserId,
      Function<UUID, String> labelForUser) {
    Map<String, List<ChatMessageReaction>> grouped = new LinkedHashMap<>();
    for (ChatMessageReaction reaction : reactions) {
      grouped.computeIfAbsent(reaction.getEmoji(), ignored -> new ArrayList<>()).add(reaction);
    }

    return grouped.entrySet().stream()
        .sorted(Comparator.comparingInt(entry -> -entry.getValue().size()))
        .map(
            entry -> {
              List<ChatMessageReaction> group = entry.getValue();
              boolean reactedByMe =
                  group.stream().anyMatch(reaction -> reaction.getUserId().equals(viewerUserId));
              List<String> reactorLabels =
                  group.stream()
                      .map(reaction -> labelForUser.apply(reaction.getUserId()))
                      .distinct()
                      .toList();
              return ChatMessageReactionSummary.builder()
                  .emoji(entry.getKey())
                  .count(group.size())
                  .reactedByMe(reactedByMe)
                  .reactorLabels(reactorLabels)
                  .build();
            })
        .toList();
  }
}
