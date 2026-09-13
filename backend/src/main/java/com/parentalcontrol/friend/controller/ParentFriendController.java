package com.parentalcontrol.friend.controller;

import com.parentalcontrol.common.api.ApiResponse;
import com.parentalcontrol.common.constant.ApiConstants;
import com.parentalcontrol.friend.dto.CreateFriendRequest;
import com.parentalcontrol.friend.dto.FriendRequestResponse;
import com.parentalcontrol.friend.dto.FriendSummaryResponse;
import com.parentalcontrol.friend.dto.RespondFriendRequest;
import com.parentalcontrol.friend.service.ParentFriendService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
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
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ApiConstants.PARENT_FRIENDS)
@RequiredArgsConstructor
@Tag(name = "Parent friends", description = "Friend requests and friends list")
@SecurityRequirement(name = "bearerAuth")
public class ParentFriendController {

  private final ParentFriendService parentFriendService;

  @PostMapping("/requests")
  @Operation(summary = "Send a friend request by @handle or email")
  public ApiResponse<FriendRequestResponse> sendRequest(
      @Valid @RequestBody CreateFriendRequest request) {
    return ApiResponse.ok(parentFriendService.sendRequest(request));
  }

  @GetMapping("/requests/pending")
  @Operation(summary = "Incoming pending friend requests")
  public ApiResponse<List<FriendRequestResponse>> pendingIncoming() {
    return ApiResponse.ok(parentFriendService.listIncomingPending());
  }

  @GetMapping("/requests/outgoing")
  @Operation(summary = "Outgoing pending friend requests")
  public ApiResponse<List<FriendRequestResponse>> pendingOutgoing() {
    return ApiResponse.ok(parentFriendService.listOutgoingPending());
  }

  @PostMapping("/requests/{requestId}/respond")
  @Operation(summary = "Accept or decline a friend request")
  public ApiResponse<FriendRequestResponse> respond(
      @PathVariable UUID requestId, @Valid @RequestBody RespondFriendRequest request) {
    return ApiResponse.ok(parentFriendService.respond(requestId, request));
  }

  @GetMapping
  @Operation(summary = "List accepted friends")
  public ApiResponse<List<FriendSummaryResponse>> listFriends() {
    return ApiResponse.ok(parentFriendService.listFriends());
  }

  @DeleteMapping("/{friendUserId}")
  @Operation(summary = "Remove a friend")
  public ApiResponse<Void> removeFriend(@PathVariable UUID friendUserId) {
    parentFriendService.removeFriend(friendUserId);
    return ApiResponse.ok(null);
  }
}
