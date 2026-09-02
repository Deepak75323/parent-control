package com.parentalcontrol.chat.service;

import com.parentalcontrol.chat.domain.ChatMessage;
import com.parentalcontrol.chat.domain.ChatMessageReceipt;
import com.parentalcontrol.chat.domain.ChatReceiptStatus;
import com.parentalcontrol.chat.domain.ChatRoomMember;
import com.parentalcontrol.chat.repository.ChatMessageReceiptRepository;
import com.parentalcontrol.chat.repository.ChatMessageRepository;
import com.parentalcontrol.chat.repository.ChatRoomMemberRepository;
import com.parentalcontrol.common.constant.MessageConstants;
import com.parentalcontrol.common.exception.BusinessException;
import com.parentalcontrol.common.exception.ErrorCode;
import com.parentalcontrol.user.dao.ParentProfileDao;
import com.parentalcontrol.user.domain.ParentProfile;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ChatReadReceiptService {

  private final ChatMessageReceiptRepository receiptRepository;
  private final ChatMessageRepository messageRepository;
  private final ChatRoomMemberRepository memberRepository;
  private final ChatRealtimeService realtimeService;
  private final ParentProfileDao parentProfileDao;

  @Transactional
  public void createReceiptsForMessage(ChatMessage message) {
    List<ChatRoomMember> members = memberRepository.findByRoomId(message.getRoomId());
    for (ChatRoomMember member : members) {
      if (member.getUserId().equals(message.getSenderUserId())) {
        continue;
      }
      receiptRepository.save(
          ChatMessageReceipt.builder()
              .messageId(message.getId())
              .recipientUserId(member.getUserId())
              .build());
    }
  }

  @Transactional
  public void markDelivered(UUID roomId, UUID recipientUserId, List<UUID> messageIds) {
    if (messageIds == null || messageIds.isEmpty()) {
      return;
    }
    requireMember(roomId, recipientUserId);
    Instant now = Instant.now();
    int updated =
        receiptRepository.markDelivered(recipientUserId, new HashSet<>(messageIds), now);
    if (updated > 0) {
      notifySendersForMessages(messageIds);
    }
  }

  @Transactional
  public void markRead(UUID roomId, UUID recipientUserId, UUID lastMessageId) {
    requireMember(roomId, recipientUserId);
    if (!isReadReceiptsEnabled(recipientUserId)) {
      return;
    }

    ChatMessage lastMessage =
        messageRepository
            .findById(lastMessageId)
            .orElseThrow(
                () ->
                    new BusinessException(
                        ErrorCode.NOT_FOUND, MessageConstants.CHAT_MESSAGE_NOT_FOUND));
    if (!lastMessage.getRoomId().equals(roomId)) {
      throw new BusinessException(ErrorCode.NOT_FOUND, MessageConstants.CHAT_MESSAGE_NOT_FOUND);
    }

    List<UUID> messageIds =
        messageRepository.findActiveByRoomId(roomId).stream()
            .filter(m -> !m.getCreatedAt().isAfter(lastMessage.getCreatedAt()))
            .filter(m -> !m.getSenderUserId().equals(recipientUserId))
            .map(ChatMessage::getId)
            .toList();
    if (messageIds.isEmpty()) {
      return;
    }

    Instant now = Instant.now();
    int updated = receiptRepository.markRead(recipientUserId, messageIds, now);
    if (updated > 0) {
      notifySendersForMessages(messageIds);
    }
  }

  @Transactional(readOnly = true)
  public ChatReceiptStatus statusForMessage(UUID messageId) {
    List<ChatMessageReceipt> receipts = receiptRepository.findByMessageId(messageId);
    if (receipts.isEmpty()) {
      return ChatReceiptStatus.SENT;
    }

    boolean allDelivered =
        receipts.stream().allMatch(r -> r.getDeliveredAt() != null || r.getReadAt() != null);
    if (!allDelivered) {
      return ChatReceiptStatus.SENT;
    }

    List<ChatMessageReceipt> eligible = new ArrayList<>();
    for (ChatMessageReceipt receipt : receipts) {
      if (isReadReceiptsEnabled(receipt.getRecipientUserId())) {
        eligible.add(receipt);
      }
    }
    if (eligible.isEmpty()) {
      return ChatReceiptStatus.DELIVERED;
    }
    boolean allRead = eligible.stream().allMatch(r -> r.getReadAt() != null);
    return allRead ? ChatReceiptStatus.READ : ChatReceiptStatus.DELIVERED;
  }

  @Transactional(readOnly = true)
  public boolean isReadReceiptsEnabled(UUID userId) {
    return parentProfileDao
        .findByUser_Id(userId)
        .map(ParentProfile::isReadReceiptsEnabled)
        .orElse(true);
  }

  private void notifySendersForMessages(List<UUID> messageIds) {
    Set<UUID> uniqueIds = new HashSet<>(messageIds);
    for (UUID messageId : uniqueIds) {
      messageRepository
          .findById(messageId)
          .ifPresent(
              message -> {
                ChatReceiptStatus status = statusForMessage(messageId);
                realtimeService.publishReceiptUpdated(
                    message.getSenderUserId(),
                    message.getRoomId(),
                    messageId,
                    status);
              });
    }
  }

  private void requireMember(UUID roomId, UUID userId) {
    if (!memberRepository.existsByRoomIdAndUserId(roomId, userId)) {
      throw new BusinessException(ErrorCode.FORBIDDEN, MessageConstants.CHAT_NOT_A_MEMBER);
    }
  }
}
