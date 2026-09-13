package com.parentalcontrol.chat.controller;

import com.parentalcontrol.chat.domain.ChatMessageDeleteScope;
import com.parentalcontrol.chat.dto.ChatInviteResponse;
import com.parentalcontrol.chat.dto.ChatMemberResponse;
import com.parentalcontrol.chat.dto.ChatMessageReactionSummary;
import com.parentalcontrol.chat.dto.ChatMessageResponse;
import com.parentalcontrol.chat.dto.ChatRoomSummaryResponse;
import com.parentalcontrol.chat.dto.CreateChatInviteRequest;
import com.parentalcontrol.chat.dto.CreateChatRoomRequest;
import com.parentalcontrol.chat.dto.MarkChatDeliveredRequest;
import com.parentalcontrol.chat.dto.MarkChatReadRequest;
import com.parentalcontrol.chat.dto.RespondChatInviteRequest;
import com.parentalcontrol.chat.dto.SendChatMessageRequest;
import com.parentalcontrol.chat.dto.SetChatMessageReactionRequest;
import com.parentalcontrol.chat.dto.ChatTypingRequest;
import com.parentalcontrol.chat.dto.ChatPartnerPresenceResponse;
import com.parentalcontrol.chat.dto.ChatCallResponse;
import com.parentalcontrol.chat.dto.ChatCallSignalRequest;
import com.parentalcontrol.chat.dto.ChatChannelResponse;
import com.parentalcontrol.chat.dto.ChatDecryptStatusResponse;
import com.parentalcontrol.chat.dto.ChatDecryptUnlockResponse;
import com.parentalcontrol.chat.dto.CreateChatChannelRequest;
import com.parentalcontrol.chat.dto.ResetChatDecryptPinRequest;
import com.parentalcontrol.chat.dto.SetChatDecryptPinRequest;
import com.parentalcontrol.chat.dto.StartChatCallRequest;
import com.parentalcontrol.chat.dto.TransferChatOwnershipRequest;
import com.parentalcontrol.chat.dto.UpdateChatMemberRequest;
import com.parentalcontrol.chat.dto.UnlockChatDecryptRequest;
import com.parentalcontrol.chat.service.ChatCallService;
import com.parentalcontrol.chat.service.ChatChannelService;
import com.parentalcontrol.chat.service.ChatDecryptUnlockService;
import com.parentalcontrol.chat.service.ChatService;
import com.parentalcontrol.chat.service.ParentPresenceFanoutService;
import com.parentalcontrol.chat.service.ChatService.AttachmentPayload;
import com.parentalcontrol.common.api.ApiResponse;
import com.parentalcontrol.common.constant.ApiConstants;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping(ApiConstants.CHAT)
@RequiredArgsConstructor
@Tag(name = "Parent chat")
public class ChatController {

  private final ChatService chatService;
  private final ChatDecryptUnlockService chatDecryptUnlockService;
  private final ChatCallService chatCallService;
  private final ChatChannelService chatChannelService;
  private final ParentPresenceFanoutService parentPresenceFanoutService;

  @PostMapping("/rooms")
  @Operation(summary = "Create a parent chat room (server)")
  public ApiResponse<ChatRoomSummaryResponse> createRoom(
      @Valid @RequestBody CreateChatRoomRequest request) {
    return ApiResponse.ok(chatService.createRoom(request));
  }

  @GetMapping("/rooms")
  @Operation(summary = "List chat rooms (servers) for the current parent")
  public ApiResponse<List<ChatRoomSummaryResponse>> listRooms() {
    return ApiResponse.ok(chatService.listRooms());
  }

  @GetMapping("/rooms/{roomId}/presence")
  @Operation(summary = "Partner Online / Last seen for a 2-person chat (hidden if they opted out)")
  public ApiResponse<ChatPartnerPresenceResponse> partnerPresence(@PathVariable UUID roomId) {
    return ApiResponse.ok(parentPresenceFanoutService.partnerPresenceForRoom(roomId));
  }

  @GetMapping("/rooms/{roomId}/channels")
  @Operation(summary = "List text and voice channels in a server")
  public ApiResponse<List<ChatChannelResponse>> listChannels(@PathVariable UUID roomId) {
    return ApiResponse.ok(chatChannelService.listChannels(roomId));
  }

  @PostMapping("/rooms/{roomId}/channels")
  @Operation(summary = "Create a text or voice channel (owner/admin)")
  public ApiResponse<ChatChannelResponse> createChannel(
      @PathVariable UUID roomId, @Valid @RequestBody CreateChatChannelRequest request) {
    return ApiResponse.ok(chatChannelService.createChannel(roomId, request));
  }

  @DeleteMapping("/rooms/{roomId}")
  @Operation(summary = "Delete a chat room (owner only)")
  public ApiResponse<Void> deleteRoom(@PathVariable UUID roomId) {
    chatService.deleteRoom(roomId);
    return ApiResponse.ok(null);
  }

  @PostMapping("/rooms/{roomId}/clear-chat")
  @Operation(summary = "Clear chat for yourself only (other members still see messages)")
  public ApiResponse<Void> clearChat(@PathVariable UUID roomId) {
    chatService.clearChat(roomId);
    return ApiResponse.ok(null);
  }

  @PostMapping("/rooms/{roomId}/clear-history")
  @Operation(summary = "Clear all messages for everyone (owner or admin, room stays)")
  public ApiResponse<Void> clearChatHistory(@PathVariable UUID roomId) {
    chatService.clearChatHistory(roomId);
    return ApiResponse.ok(null);
  }

  @PostMapping("/rooms/{roomId}/leave")
  @Operation(summary = "Leave a chat room (members only)")
  public ApiResponse<Void> leaveRoom(@PathVariable UUID roomId) {
    chatService.leaveRoom(roomId);
    return ApiResponse.ok(null);
  }

  @GetMapping("/decrypt-status")
  @Operation(summary = "Whether a server decrypt passphrase is already configured")
  public ApiResponse<ChatDecryptStatusResponse> decryptStatus() {
    return ApiResponse.ok(chatDecryptUnlockService.status());
  }

  @PostMapping("/decrypt-pin")
  @Operation(summary = "Create the server decrypt passphrase (only when not yet configured)")
  public ApiResponse<ChatDecryptUnlockResponse> createDecryptPin(
      @Valid @RequestBody SetChatDecryptPinRequest request) {
    return ApiResponse.ok(chatDecryptUnlockService.createPin(request.getPin()));
  }

  @PostMapping("/decrypt-verify")
  @Operation(summary = "Verify decrypt passphrase (camera unlock fallback)")
  public ApiResponse<Void> verifyDecryptPin(@Valid @RequestBody UnlockChatDecryptRequest request) {
    chatDecryptUnlockService.verifyPin(request.getPin());
    return ApiResponse.ok(null);
  }

  @PostMapping("/rooms/{roomId}/decrypt-unlock")
  @Operation(summary = "Unlock decrypt view for this room (owner or granted member + passphrase)")
  public ApiResponse<ChatDecryptUnlockResponse> unlockDecrypt(
      @PathVariable UUID roomId, @Valid @RequestBody UnlockChatDecryptRequest request) {
    return ApiResponse.ok(chatDecryptUnlockService.unlock(roomId, request.getPin()));
  }

  @PostMapping("/rooms/{roomId}/decrypt-reset")
  @Operation(
      summary =
          "Forgot passphrase: wipe this room's messages for everyone and set a new decrypt code (owner only)")
  public ApiResponse<ChatDecryptUnlockResponse> resetDecryptPin(
      @PathVariable UUID roomId, @Valid @RequestBody ResetChatDecryptPinRequest request) {
    return ApiResponse.ok(
        chatDecryptUnlockService.resetPinAndClearRoom(roomId, request.getNewPin()));
  }

  @GetMapping("/rooms/{roomId}/members")
  @Operation(summary = "List members of a chat room")
  public ApiResponse<List<ChatMemberResponse>> listMembers(@PathVariable UUID roomId) {
    return ApiResponse.ok(chatService.listMembers(roomId));
  }

  @DeleteMapping("/rooms/{roomId}/members/{userId}")
  @Operation(summary = "Remove a member from the room (owner or admin)")
  public ApiResponse<Void> removeMember(@PathVariable UUID roomId, @PathVariable UUID userId) {
    chatService.removeMember(roomId, userId);
    return ApiResponse.ok(null);
  }

  @PatchMapping("/rooms/{roomId}/members/{userId}")
  @Operation(summary = "Update member role, mute, or decrypt grant (owner/admin)")
  public ApiResponse<ChatMemberResponse> updateMember(
      @PathVariable UUID roomId,
      @PathVariable UUID userId,
      @RequestBody UpdateChatMemberRequest request) {
    return ApiResponse.ok(chatService.updateMember(roomId, userId, request));
  }

  @PostMapping("/rooms/{roomId}/transfer-ownership")
  @Operation(summary = "Transfer room ownership to another member (owner only)")
  public ApiResponse<Void> transferOwnership(
      @PathVariable UUID roomId, @Valid @RequestBody TransferChatOwnershipRequest request) {
    chatService.transferOwnership(roomId, request.getUserId());
    return ApiResponse.ok(null);
  }

  @PostMapping("/rooms/{roomId}/calls")
  @Operation(summary = "Start a multi-parent video call in a voice channel (or return the active one)")
  public ApiResponse<ChatCallResponse> startCall(
      @PathVariable UUID roomId,
      @RequestBody(required = false) StartChatCallRequest request) {
    UUID channelId = request != null ? request.getChannelId() : null;
    return ApiResponse.ok(chatCallService.startOrGetActive(roomId, channelId));
  }

  @GetMapping("/rooms/{roomId}/calls/active")
  @Operation(summary = "Get the active video call for a room/voice channel, if any")
  public ApiResponse<ChatCallResponse> getActiveCall(
      @PathVariable UUID roomId, @RequestParam(required = false) UUID channelId) {
    return ApiResponse.ok(chatCallService.getActive(roomId, channelId));
  }

  @PostMapping("/rooms/{roomId}/calls/{callId}/join")
  @Operation(summary = "Join an active multi-parent video call")
  public ApiResponse<ChatCallResponse> joinCall(
      @PathVariable UUID roomId, @PathVariable UUID callId) {
    return ApiResponse.ok(chatCallService.join(roomId, callId));
  }

  @PostMapping("/rooms/{roomId}/calls/{callId}/leave")
  @Operation(summary = "Leave a video call")
  public ApiResponse<Void> leaveCall(@PathVariable UUID roomId, @PathVariable UUID callId) {
    chatCallService.leave(roomId, callId);
    return ApiResponse.ok(null);
  }

  @PostMapping("/rooms/{roomId}/calls/{callId}/end")
  @Operation(summary = "End a video call for everyone (starter, owner, or admin)")
  public ApiResponse<Void> endCall(@PathVariable UUID roomId, @PathVariable UUID callId) {
    chatCallService.end(roomId, callId);
    return ApiResponse.ok(null);
  }

  @PostMapping("/rooms/{roomId}/calls/{callId}/signal")
  @Operation(summary = "Relay WebRTC offer/answer/ICE to another call participant")
  public ApiResponse<Void> signalCall(
      @PathVariable UUID roomId,
      @PathVariable UUID callId,
      @Valid @RequestBody ChatCallSignalRequest request) {
    chatCallService.signal(roomId, callId, request);
    return ApiResponse.ok(null);
  }

  @PostMapping("/rooms/{roomId}/invites")
  @Operation(summary = "Invite another parent by family handle or email")
  public ApiResponse<ChatInviteResponse> createInvite(
      @PathVariable UUID roomId, @Valid @RequestBody CreateChatInviteRequest request) {
    return ApiResponse.ok(chatService.createInvite(roomId, request));
  }

  @GetMapping("/invites/pending")
  @Operation(summary = "List pending chat room invites")
  public ApiResponse<List<ChatInviteResponse>> listPendingInvites() {
    return ApiResponse.ok(chatService.listPendingInvites());
  }

  @PostMapping("/invites/{inviteId}/respond")
  @Operation(summary = "Accept or decline a chat room invite")
  public ApiResponse<ChatInviteResponse> respondToInvite(
      @PathVariable UUID inviteId, @Valid @RequestBody RespondChatInviteRequest request) {
    return ApiResponse.ok(chatService.respondToInvite(inviteId, request));
  }

  @GetMapping("/rooms/{roomId}/messages")
  @Operation(summary = "List messages in a text channel")
  public ApiResponse<List<ChatMessageResponse>> listMessages(
      @PathVariable UUID roomId,
      @RequestParam(required = false) UUID channelId,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "50") int size) {
    return ApiResponse.ok(chatService.listMessages(roomId, channelId, page, size));
  }

  @PostMapping("/rooms/{roomId}/messages")
  @Operation(summary = "Send a message to a chat room")
  public ApiResponse<ChatMessageResponse> sendMessage(
      @PathVariable UUID roomId, @Valid @RequestBody SendChatMessageRequest request) {
    return ApiResponse.ok(chatService.sendMessage(roomId, request));
  }

  @DeleteMapping("/rooms/{roomId}/messages/{messageId}")
  @Operation(summary = "Delete a chat message for yourself or for everyone (sender only)")
  public ApiResponse<Void> deleteMessage(
      @PathVariable UUID roomId,
      @PathVariable UUID messageId,
      @RequestParam(defaultValue = "FOR_ME") ChatMessageDeleteScope scope) {
    chatService.deleteMessage(roomId, messageId, scope);
    return ApiResponse.ok(null);
  }

  @PatchMapping("/rooms/{roomId}/messages/{messageId}")
  @Operation(summary = "Edit a chat message (sender only)")
  public ApiResponse<ChatMessageResponse> editMessage(
      @PathVariable UUID roomId,
      @PathVariable UUID messageId,
      @Valid @RequestBody SendChatMessageRequest request) {
    return ApiResponse.ok(chatService.editMessage(roomId, messageId, request));
  }

  @PutMapping("/rooms/{roomId}/messages/{messageId}/reactions")
  @Operation(summary = "Add, change, or remove your emoji reaction on a message")
  public ApiResponse<List<ChatMessageReactionSummary>> setMessageReaction(
      @PathVariable UUID roomId,
      @PathVariable UUID messageId,
      @Valid @RequestBody SetChatMessageReactionRequest request) {
    return ApiResponse.ok(chatService.setMessageReaction(roomId, messageId, request.getEmoji()));
  }

  @PostMapping(
      value = "/rooms/{roomId}/messages/media",
      consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @Operation(summary = "Send an image or video message with an optional caption")
  public ApiResponse<ChatMessageResponse> sendMediaMessage(
      @PathVariable UUID roomId,
      @RequestPart("file") MultipartFile file,
      // Browser FormData fields are often application/octet-stream; @RequestPart rejects that.
      @RequestParam(value = "caption", required = false) String caption,
      @RequestParam(value = "replyToMessageId", required = false) UUID replyToMessageId,
      @RequestParam(value = "channelId", required = false) UUID channelId) {
    return ApiResponse.ok(
        chatService.sendMediaMessage(roomId, file, caption, replyToMessageId, channelId));
  }

  @GetMapping("/rooms/{roomId}/messages/{messageId}/attachment")
  @Operation(summary = "Download a chat message attachment (room members only)")
  public ResponseEntity<Resource> downloadAttachment(
      @PathVariable UUID roomId, @PathVariable UUID messageId) {
    AttachmentPayload payload = chatService.loadAttachment(roomId, messageId);
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(payload.mediaType());
    if (payload.fileName() != null && !payload.fileName().isBlank()) {
      headers.setContentDisposition(
          ContentDisposition.inline().filename(payload.fileName()).build());
    }
    return ResponseEntity.ok().headers(headers).body(payload.resource());
  }

  @PostMapping("/rooms/{roomId}/messages/{messageId}/attachment/view")
  @Operation(summary = "Open a view-once attachment (deleted after first view)")
  public ResponseEntity<Resource> viewOnceAttachment(
      @PathVariable UUID roomId, @PathVariable UUID messageId) {
    AttachmentPayload payload = chatService.viewOnceAttachment(roomId, messageId);
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(payload.mediaType());
    if (payload.fileName() != null && !payload.fileName().isBlank()) {
      headers.setContentDisposition(
          ContentDisposition.inline().filename(payload.fileName()).build());
    }
    return ResponseEntity.ok().headers(headers).body(payload.resource());
  }

  @PostMapping("/rooms/{roomId}/delivered")
  @Operation(summary = "Mark chat messages as delivered to this device")
  public ApiResponse<Void> markDelivered(
      @PathVariable UUID roomId, @Valid @RequestBody MarkChatDeliveredRequest request) {
    chatService.markMessagesDelivered(roomId, request);
    return ApiResponse.ok(null);
  }

  @PostMapping("/rooms/{roomId}/read")
  @Operation(summary = "Mark messages in a room as read up to the given message")
  public ApiResponse<Void> markRead(
      @PathVariable UUID roomId, @Valid @RequestBody MarkChatReadRequest request) {
    chatService.markRoomRead(roomId, request);
    return ApiResponse.ok(null);
  }

  @PostMapping("/rooms/{roomId}/typing")
  @Operation(summary = "Notify other members that you started or stopped typing")
  public ApiResponse<Void> notifyTyping(
      @PathVariable UUID roomId, @Valid @RequestBody ChatTypingRequest request) {
    chatService.notifyTyping(roomId, Boolean.TRUE.equals(request.getTyping()));
    return ApiResponse.ok(null);
  }

  @PostMapping("/rooms/{roomId}/messages/{messageId}/mark-unread")
  @Operation(summary = "Mark this message and everything below it as unread for the current user")
  public ApiResponse<Void> markUnread(
      @PathVariable UUID roomId, @PathVariable UUID messageId) {
    chatService.markMessageUnread(roomId, messageId);
    return ApiResponse.ok(null);
  }
}
