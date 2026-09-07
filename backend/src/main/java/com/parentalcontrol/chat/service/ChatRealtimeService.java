package com.parentalcontrol.chat.service;

import com.parentalcontrol.chat.domain.ChatReceiptStatus;
import com.parentalcontrol.chat.dto.ChatInviteResponse;
import com.parentalcontrol.chat.dto.ChatMessageReactionSummary;
import com.parentalcontrol.chat.dto.ChatMessageResponse;
import com.parentalcontrol.messaging.ClusterStompMessagingService;
import java.util.Map;
import java.util.UUID;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ChatRealtimeService {

  private final ClusterStompMessagingService messagingTemplate;

  public void publishInvite(UUID inviteeUserId, ChatInviteResponse invite) {
    messagingTemplate.convertAndSendToUser(
        inviteeUserId.toString(), "/queue/chat-invites", invite);
  }

  public void publishInviteResponded(UUID inviterUserId, ChatInviteResponse invite) {
    messagingTemplate.convertAndSendToUser(
        inviterUserId.toString(), "/queue/chat-invites", invite);
  }

  public void publishMessage(UUID memberUserId, ChatMessageResponse message) {
    messagingTemplate.convertAndSendToUser(
        memberUserId.toString(), "/queue/chat-messages", message);
  }

  public void publishMessageDeleted(UUID memberUserId, UUID roomId, UUID messageId) {
    messagingTemplate.convertAndSendToUser(
        memberUserId.toString(),
        "/queue/chat-messages",
        Map.of(
            "eventType", "MESSAGE_DELETED",
            "roomId", roomId.toString(),
            "messageId", messageId.toString()));
  }

  public void publishChatCleared(UUID memberUserId, UUID roomId) {
    messagingTemplate.convertAndSendToUser(
        memberUserId.toString(),
        "/queue/chat-messages",
        Map.of("eventType", "CHAT_CLEARED", "roomId", roomId.toString()));
  }

  public void publishMemberLeft(UUID memberUserId, UUID roomId) {
    messagingTemplate.convertAndSendToUser(
        memberUserId.toString(),
        "/queue/chat-rooms",
        Map.of("eventType", "MEMBER_LEFT", "roomId", roomId.toString()));
  }

  public void publishMemberRemoved(UUID memberUserId, UUID roomId) {
    messagingTemplate.convertAndSendToUser(
        memberUserId.toString(),
        "/queue/chat-rooms",
        Map.of("eventType", "MEMBER_REMOVED", "roomId", roomId.toString()));
  }

  public void publishRoomDeleted(UUID memberUserId, UUID roomId) {
    messagingTemplate.convertAndSendToUser(
        memberUserId.toString(),
        "/queue/chat-rooms",
        Map.of("eventType", "ROOM_DELETED", "roomId", roomId.toString()));
  }

  public void publishRoomListBump(UUID memberUserId) {
    messagingTemplate.convertAndSendToUser(
        memberUserId.toString(),
        "/queue/chat-rooms",
        Map.of("eventType", "ROOMS_CHANGED"));
  }

  public void publishReceiptUpdated(
      UUID senderUserId, UUID roomId, UUID messageId, ChatReceiptStatus status) {
    messagingTemplate.convertAndSendToUser(
        senderUserId.toString(),
        "/queue/chat-messages",
        Map.of(
            "eventType", "MESSAGE_RECEIPT_UPDATED",
            "roomId", roomId.toString(),
            "messageId", messageId.toString(),
            "receiptStatus", status.name()));
  }

  public void publishMessageReactionsUpdated(
      UUID memberUserId,
      UUID roomId,
      UUID messageId,
      java.util.List<ChatMessageReactionSummary> reactions) {
    messagingTemplate.convertAndSendToUser(
        memberUserId.toString(),
        "/queue/chat-messages",
        Map.of(
            "eventType", "MESSAGE_REACTIONS_UPDATED",
            "roomId", roomId.toString(),
            "messageId", messageId.toString(),
            "reactions", reactions));
  }

  public void publishTyping(
      UUID memberUserId,
      UUID roomId,
      UUID typingUserId,
      String nickname,
      String label,
      boolean typing) {
    messagingTemplate.convertAndSendToUser(
        memberUserId.toString(),
        "/queue/chat-messages",
        Map.of(
            "eventType", "TYPING",
            "roomId", roomId.toString(),
            "userId", typingUserId.toString(),
            "nickname", nickname == null ? "" : nickname,
            "label", label == null ? "" : label,
            "typing", typing));
  }

  public void publishCallEvent(UUID memberUserId, Map<String, Object> event) {
    messagingTemplate.convertAndSendToUser(
        memberUserId.toString(), "/queue/chat-messages", event);
  }
}
