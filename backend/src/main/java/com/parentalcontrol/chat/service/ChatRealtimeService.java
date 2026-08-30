package com.parentalcontrol.chat.service;

import com.parentalcontrol.chat.dto.ChatInviteResponse;
import com.parentalcontrol.chat.dto.ChatMessageResponse;
import com.parentalcontrol.messaging.ClusterStompMessagingService;
import java.util.Map;
import java.util.UUID;
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
}
