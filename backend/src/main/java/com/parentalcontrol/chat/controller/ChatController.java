package com.parentalcontrol.chat.controller;

import com.parentalcontrol.chat.dto.ChatInviteResponse;
import com.parentalcontrol.chat.dto.ChatMemberResponse;
import com.parentalcontrol.chat.dto.ChatMessageResponse;
import com.parentalcontrol.chat.dto.ChatRoomSummaryResponse;
import com.parentalcontrol.chat.dto.CreateChatInviteRequest;
import com.parentalcontrol.chat.dto.CreateChatRoomRequest;
import com.parentalcontrol.chat.dto.RespondChatInviteRequest;
import com.parentalcontrol.chat.dto.SendChatMessageRequest;
import com.parentalcontrol.chat.service.ChatService;
import com.parentalcontrol.common.api.ApiResponse;
import com.parentalcontrol.common.constant.ApiConstants;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ApiConstants.CHAT)
@RequiredArgsConstructor
@Tag(name = "Parent chat")
public class ChatController {

  private final ChatService chatService;

  @PostMapping("/rooms")
  @Operation(summary = "Create a parent chat room")
  public ApiResponse<ChatRoomSummaryResponse> createRoom(
      @Valid @RequestBody CreateChatRoomRequest request) {
    return ApiResponse.ok(chatService.createRoom(request));
  }

  @GetMapping("/rooms")
  @Operation(summary = "List chat rooms for the current parent")
  public ApiResponse<List<ChatRoomSummaryResponse>> listRooms() {
    return ApiResponse.ok(chatService.listRooms());
  }

  @DeleteMapping("/rooms/{roomId}")
  @Operation(summary = "Delete a chat room (owner only)")
  public ApiResponse<Void> deleteRoom(@PathVariable UUID roomId) {
    chatService.deleteRoom(roomId);
    return ApiResponse.ok(null);
  }

  @GetMapping("/rooms/{roomId}/members")
  @Operation(summary = "List members of a chat room")
  public ApiResponse<List<ChatMemberResponse>> listMembers(@PathVariable UUID roomId) {
    return ApiResponse.ok(chatService.listMembers(roomId));
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
  @Operation(summary = "List messages in a chat room")
  public ApiResponse<List<ChatMessageResponse>> listMessages(
      @PathVariable UUID roomId,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "50") int size) {
    return ApiResponse.ok(chatService.listMessages(roomId, page, size));
  }

  @PostMapping("/rooms/{roomId}/messages")
  @Operation(summary = "Send a message to a chat room")
  public ApiResponse<ChatMessageResponse> sendMessage(
      @PathVariable UUID roomId, @Valid @RequestBody SendChatMessageRequest request) {
    return ApiResponse.ok(chatService.sendMessage(roomId, request));
  }
}
