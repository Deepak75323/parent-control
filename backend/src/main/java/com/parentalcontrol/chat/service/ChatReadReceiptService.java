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
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Collection;
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

  /**
   * Advances the member's WhatsApp-style read cursor to {@code lastMessageId} (never backwards)
   * and marks receipts through that message as read.
   *
   * @return message IDs that are now fully read by every recipient (candidates to purge)
   */
  @Transactional
  public List<UUID> markRead(UUID roomId, UUID recipientUserId, UUID lastMessageId) {
    ChatRoomMember member = requireMember(roomId, recipientUserId);

    ChatMessage lastMessage =
        messageRepository
            .findIncludingDeletedByIdAndRoomId(lastMessageId, roomId)
            .orElseThrow(
                () ->
                    new BusinessException(
                        ErrorCode.NOT_FOUND, MessageConstants.CHAT_MESSAGE_NOT_FOUND));

    advanceReadCursor(member, lastMessage);

    List<UUID> messageIds =
        messageRepository.findIdsThroughForRecipient(
            roomId, recipientUserId, lastMessage.getCreatedAt());
    if (messageIds.isEmpty()) {
      return List.of();
    }

    Instant now = Instant.now();
    int updated = receiptRepository.markRead(recipientUserId, messageIds, now);
    List<UUID> fullyRead = List.of();
    if (updated > 0) {
      notifySendersForMessages(messageIds);
      fullyRead =
          messageIds.stream().filter(this::isFullyReadByAllRecipients).toList();
    }
    return fullyRead;
  }

  @Transactional(readOnly = true)
  public boolean isFullyReadByAllRecipients(UUID messageId) {
    List<ChatMessageReceipt> receipts = receiptRepository.findByMessageId(messageId);
    if (receipts.isEmpty()) {
      return false;
    }
    return receipts.stream().allMatch(r -> r.getReadAt() != null);
  }

  @Transactional(readOnly = true)
  public boolean isReadyForAutoDelete(UUID messageId, java.time.Duration grace) {
    List<ChatMessageReceipt> receipts = receiptRepository.findByMessageId(messageId);
    if (receipts.isEmpty()) {
      return false;
    }
    Instant lastRead = null;
    for (ChatMessageReceipt receipt : receipts) {
      if (receipt.getReadAt() == null) {
        return false;
      }
      if (lastRead == null || receipt.getReadAt().isAfter(lastRead)) {
        lastRead = receipt.getReadAt();
      }
    }
    if (grace == null || grace.isZero() || grace.isNegative()) {
      return true;
    }
    return !lastRead.plus(grace).isAfter(Instant.now());
  }

  /**
   * Moves the read cursor to the message immediately before {@code anchorMessageId}, so the
   * anchor and everything after it show as unread for this viewer only.
   */
  @Transactional
  public void markUnread(UUID roomId, UUID recipientUserId, UUID anchorMessageId) {
    ChatRoomMember member = requireMember(roomId, recipientUserId);
    ChatMessage anchor =
        messageRepository
            .findById(anchorMessageId)
            .orElseThrow(
                () ->
                    new BusinessException(
                        ErrorCode.NOT_FOUND, MessageConstants.CHAT_MESSAGE_NOT_FOUND));
    if (!anchor.getRoomId().equals(roomId)) {
      throw new BusinessException(ErrorCode.NOT_FOUND, MessageConstants.CHAT_MESSAGE_NOT_FOUND);
    }

    UUID previousId =
        messageRepository.findActiveByRoomId(roomId).stream()
            .sorted(Comparator.comparing(ChatMessage::getCreatedAt))
            .filter(message -> message.getCreatedAt().isBefore(anchor.getCreatedAt()))
            .reduce((left, right) -> right)
            .map(ChatMessage::getId)
            .orElse(null);

    member.setLastReadMessageId(previousId);
    member.setUnreadFromAt(null);
    memberRepository.save(member);
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

    // WhatsApp-style mutual privacy: if the sender turned read receipts off, never
    // report READ on their own messages (cap at DELIVERED).
    ChatMessage message = messageRepository.findById(messageId).orElse(null);
    if (message != null && !isReadReceiptsEnabled(message.getSenderUserId())) {
      return ChatReceiptStatus.DELIVERED;
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

  @Transactional(readOnly = true)
  public Map<UUID, Instant> readAtForViewer(Collection<UUID> messageIds, UUID viewerUserId) {
    if (messageIds == null || messageIds.isEmpty()) {
      return Map.of();
    }
    Map<UUID, Instant> readAtByMessage = new HashMap<>();
    for (ChatMessageReceipt receipt : receiptRepository.findByMessageIdIn(messageIds)) {
      if (receipt.getRecipientUserId().equals(viewerUserId) && receipt.getReadAt() != null) {
        readAtByMessage.put(receipt.getMessageId(), receipt.getReadAt());
      }
    }
    return readAtByMessage;
  }

  /**
   * Unread = incoming messages after the member's read cursor ({@code lastReadMessageId}).
   * Falls back to receipt {@code readAt} / legacy {@code unreadFromAt} when cursor is unset.
   */
  @Transactional(readOnly = true)
  public Map<UUID, Boolean> unreadForViewer(
      Collection<ChatMessage> messages,
      UUID viewerUserId,
      UUID lastReadMessageId,
      Instant unreadFromAt) {
    if (messages == null || messages.isEmpty()) {
      return Map.of();
    }

    Instant cursorAt = null;
    if (lastReadMessageId != null) {
      cursorAt =
          messageRepository
              .findIncludingDeletedById(lastReadMessageId)
              .map(ChatMessage::getCreatedAt)
              .orElse(null);
    }

    List<UUID> messageIds = messages.stream().map(ChatMessage::getId).toList();
    Map<UUID, ChatMessageReceipt> receiptsByMessage =
        receiptRepository.findByMessageIdIn(messageIds).stream()
            .filter(receipt -> receipt.getRecipientUserId().equals(viewerUserId))
            .collect(
                java.util.stream.Collectors.toMap(
                    ChatMessageReceipt::getMessageId, receipt -> receipt, (left, right) -> left));

    Map<UUID, Boolean> unreadByMessage = new HashMap<>();
    for (ChatMessage message : messages) {
      if (message.getSenderUserId().equals(viewerUserId)) {
        continue;
      }
      boolean unread;
      if (cursorAt != null) {
        unread = message.getCreatedAt().isAfter(cursorAt);
      } else if (lastReadMessageId == null && unreadFromAt == null) {
        ChatMessageReceipt receipt = receiptsByMessage.get(message.getId());
        unread = receipt == null || receipt.getReadAt() == null;
      } else if (unreadFromAt != null) {
        unread = !message.getCreatedAt().isBefore(unreadFromAt);
      } else {
        unread = true;
      }
      unreadByMessage.put(message.getId(), unread);
    }
    return unreadByMessage;
  }

  @Transactional(readOnly = true)
  public Map<UUID, Boolean> unreadForViewer(Collection<UUID> messageIds, UUID viewerUserId) {
    if (messageIds == null || messageIds.isEmpty()) {
      return Map.of();
    }
    List<ChatMessage> messages = messageRepository.findAllById(messageIds);
    return unreadForViewer(messages, viewerUserId, null, null);
  }

  @Transactional(readOnly = true)
  public int countUnread(
      UUID roomId, UUID viewerUserId, UUID lastReadMessageId, Instant unreadFromAt) {
    List<ChatMessage> messages = new ArrayList<>(messageRepository.findActiveByRoomId(roomId));
    messages.addAll(messageRepository.findUnreadDeletedForRecipient(roomId, viewerUserId));
    Map<UUID, Boolean> unread =
        unreadForViewer(messages, viewerUserId, lastReadMessageId, unreadFromAt);
    return (int) unread.values().stream().filter(Boolean.TRUE::equals).count();
  }

  private ChatRoomMember requireMember(UUID roomId, UUID userId) {
    return memberRepository
        .findByRoomIdAndUserId(roomId, userId)
        .orElseThrow(
            () -> new BusinessException(ErrorCode.FORBIDDEN, MessageConstants.CHAT_NOT_A_MEMBER));
  }

  private void advanceReadCursor(ChatRoomMember member, ChatMessage lastMessage) {
    UUID currentId = member.getLastReadMessageId();
    if (currentId != null) {
      ChatMessage current =
          messageRepository.findIncludingDeletedById(currentId).orElse(null);
      if (current != null
          && current.getRoomId().equals(lastMessage.getRoomId())
          && lastMessage.getCreatedAt().isBefore(current.getCreatedAt())) {
        member.setUnreadFromAt(null);
        memberRepository.save(member);
        return;
      }
    }
    member.setLastReadMessageId(lastMessage.getId());
    member.setUnreadFromAt(null);
    memberRepository.save(member);
  }
}
