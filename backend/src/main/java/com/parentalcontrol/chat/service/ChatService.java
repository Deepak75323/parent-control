package com.parentalcontrol.chat.service;

import com.parentalcontrol.chat.ChatHistoryClearRules;
import com.parentalcontrol.chat.domain.ChatMessage;
import com.parentalcontrol.chat.domain.ChatMessageDeleteScope;
import com.parentalcontrol.chat.domain.ChatMessageHide;
import com.parentalcontrol.chat.domain.ChatMessageType;
import com.parentalcontrol.chat.domain.ChatReceiptStatus;
import com.parentalcontrol.chat.domain.ChatRoom;
import com.parentalcontrol.chat.domain.ChatRoomInvite;
import com.parentalcontrol.chat.domain.ChatRoomInviteStatus;
import com.parentalcontrol.chat.domain.ChatRoomMember;
import com.parentalcontrol.chat.domain.ChatRoomMemberRole;
import com.parentalcontrol.chat.dto.ChatInviteResponse;
import com.parentalcontrol.chat.dto.ChatMemberResponse;
import com.parentalcontrol.chat.dto.ChatMessageReactionSummary;
import com.parentalcontrol.chat.dto.ChatMessageReplyPreview;
import com.parentalcontrol.chat.dto.ChatMessageResponse;
import com.parentalcontrol.chat.dto.ChatRoomSummaryResponse;
import com.parentalcontrol.chat.dto.CreateChatInviteRequest;
import com.parentalcontrol.chat.dto.CreateChatRoomRequest;
import com.parentalcontrol.chat.dto.MarkChatDeliveredRequest;
import com.parentalcontrol.chat.dto.MarkChatReadRequest;
import com.parentalcontrol.chat.dto.RespondChatInviteRequest;
import com.parentalcontrol.chat.dto.SendChatMessageRequest;
import com.parentalcontrol.chat.repository.ChatMessageHideRepository;
import com.parentalcontrol.chat.repository.ChatMessageRepository;
import com.parentalcontrol.chat.repository.ChatRoomInviteRepository;
import com.parentalcontrol.chat.repository.ChatRoomMemberRepository;
import com.parentalcontrol.chat.repository.ChatRoomRepository;
import com.parentalcontrol.common.constant.ApiConstants;
import com.parentalcontrol.common.constant.MessageConstants;
import com.parentalcontrol.common.constant.ValidationConstants;
import com.parentalcontrol.common.domain.Role;
import com.parentalcontrol.common.exception.BusinessException;
import com.parentalcontrol.common.exception.ErrorCode;
import com.parentalcontrol.common.security.SecurityUtils;
import com.parentalcontrol.common.util.ChatNicknameGenerator;
import com.parentalcontrol.config.ChatAutoDeleteProperties;
import com.parentalcontrol.user.dao.ParentProfileDao;
import com.parentalcontrol.user.dao.UserDao;
import com.parentalcontrol.user.domain.ParentProfile;
import com.parentalcontrol.user.domain.User;
import com.parentalcontrol.user.service.DeletedMessagesPinService;
import com.parentalcontrol.user.service.FamilyHandleService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatService {

  private static final Pattern EMAIL_PATTERN =
      Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

  private final ChatRoomRepository roomRepository;
  private final ChatRoomMemberRepository memberRepository;
  private final ChatRoomInviteRepository inviteRepository;
  private final ChatMessageRepository messageRepository;
  private final ChatMessageHideRepository messageHideRepository;
  private final ChatRealtimeService realtimeService;
  private final ChatAttachmentStorageService attachmentStorageService;
  private final ChatReadReceiptService readReceiptService;
  private final ChatMessageReactionService reactionService;
  private final ChatAutoDeleteProperties autoDeleteProperties;
  private final FamilyHandleService familyHandleService;
  private final ParentProfileDao parentProfileDao;
  private final UserDao userDao;
  private final ChatChannelService channelService;
  private final DeletedMessagesPinService deletedMessagesPinService;

  @Transactional
  public ChatRoomSummaryResponse createRoom(CreateChatRoomRequest request) {
    UUID userId = SecurityUtils.requireUserId();
    String name = request.getName().trim();

    ChatRoom room =
        roomRepository.save(
            ChatRoom.builder().ownerUserId(userId).name(name).build());

    memberRepository.save(
        ChatRoomMember.builder()
            .roomId(room.getId())
            .userId(userId)
            .role(ChatRoomMemberRole.OWNER)
            .joinedAt(Instant.now())
            .build());

    channelService.ensureDefaultChannels(room.getId());
    return toRoomSummary(room, userId);
  }

  /**
   * Find or create a 2-person <em>direct message</em> room for two parents (used after friend
   * accept / friend click). Does not reuse ordinary servers that happen to have 2 members (e.g.
   * Notes). Owner is {@code userA}.
   */
  @Transactional
  public UUID ensureDirectRoomBetween(UUID userA, UUID userB, String roomName) {
    if (userA == null || userB == null || userA.equals(userB)) {
      throw new BusinessException(
          ErrorCode.VALIDATION_ERROR, MessageConstants.FRIEND_CANNOT_ADD_YOURSELF);
    }
    for (ChatRoom room : roomRepository.findAllForMember(userA)) {
      if (!room.isDirectMessage()) {
        continue;
      }
      List<ChatRoomMember> members = memberRepository.findByRoomId(room.getId());
      if (members.size() != 2) {
        continue;
      }
      boolean hasB = members.stream().anyMatch(m -> m.getUserId().equals(userB));
      if (hasB) {
        return room.getId();
      }
    }

    String name;
    if (StringUtils.hasText(roomName)) {
      String trimmed = roomName.trim();
      name = trimmed.length() <= 64 ? trimmed : trimmed.substring(0, 64);
    } else {
      name = "Direct message";
    }
    ChatRoom room =
        roomRepository.save(
            ChatRoom.builder().ownerUserId(userA).name(name).directMessage(true).build());
    Instant now = Instant.now();
    memberRepository.save(
        ChatRoomMember.builder()
            .roomId(room.getId())
            .userId(userA)
            .role(ChatRoomMemberRole.OWNER)
            .joinedAt(now)
            .build());
    memberRepository.save(
        ChatRoomMember.builder()
            .roomId(room.getId())
            .userId(userB)
            .role(ChatRoomMemberRole.MEMBER)
            .joinedAt(now)
            .build());
    channelService.ensureDefaultChannels(room.getId());
    realtimeService.publishRoomListBump(userA);
    realtimeService.publishRoomListBump(userB);
    return room.getId();
  }

  @Transactional(readOnly = true)
  public List<ChatRoomSummaryResponse> listRooms() {
    UUID userId = SecurityUtils.requireUserId();
    List<ChatRoom> rooms = roomRepository.findAllForMember(userId);
    Map<UUID, List<com.parentalcontrol.chat.dto.ChatChannelResponse>> channelsByRoom =
        channelService.channelsByRoomIds(rooms.stream().map(ChatRoom::getId).toList());
    return rooms.stream()
        .map(
            room ->
                toRoomSummary(
                    room, userId, channelsByRoom.getOrDefault(room.getId(), List.of())))
        .toList();
  }

  @Transactional(readOnly = true)
  public List<ChatMemberResponse> listMembers(UUID roomId) {
    UUID userId = SecurityUtils.requireUserId();
    requireActiveMember(roomId, userId);
    return memberRepository.findByRoomId(roomId).stream()
        .map(this::toMemberResponse)
        .toList();
  }

  @Transactional
  public void removeMember(UUID roomId, UUID targetUserId) {
    UUID actorId = SecurityUtils.requireUserId();
    ChatRoomMember actor = requireActiveMember(roomId, actorId);
    ChatRoomMember target = requireActiveMember(roomId, targetUserId);
    ChatRoomModerationRules.requireCanRemove(actor, target);

    target.setDeletedAt(Instant.now());
    memberRepository.save(target);

    ChatRoom room = requireRoom(roomId);
    room.setUpdatedAt(Instant.now());
    roomRepository.save(room);

    realtimeService.publishMemberRemoved(targetUserId, roomId);
    notifyRoomListBump(roomId);
  }

  @Transactional
  public ChatMemberResponse updateMember(
      UUID roomId, UUID targetUserId, com.parentalcontrol.chat.dto.UpdateChatMemberRequest request) {
    UUID actorId = SecurityUtils.requireUserId();
    ChatRoomMember actor = requireActiveMember(roomId, actorId);
    ChatRoomMember target = requireActiveMember(roomId, targetUserId);

    boolean changed = false;
    if (request.getRole() != null) {
      ChatRoomMemberRole newRole = request.getRole();
      if (newRole != ChatRoomMemberRole.ADMIN && newRole != ChatRoomMemberRole.MEMBER) {
        throw new BusinessException(
            ErrorCode.VALIDATION_ERROR, MessageConstants.CHAT_INVALID_MEMBER_ROLE);
      }
      ChatRoomModerationRules.requireCanChangeRole(actor, target);
      target.setRole(newRole);
      changed = true;
    }
    if (request.getMuted() != null) {
      ChatRoomModerationRules.requireCanMute(actor, target);
      target.setMuted(request.getMuted());
      changed = true;
    }
    if (request.getCanDecrypt() != null) {
      ChatRoomModerationRules.requireCanGrantDecrypt(actor, target);
      target.setCanDecrypt(request.getCanDecrypt());
      changed = true;
    }
    if (!changed) {
      return toMemberResponse(target);
    }

    memberRepository.save(target);
    notifyRoomListBump(roomId);
    return toMemberResponse(target);
  }

  @Transactional
  public void transferOwnership(UUID roomId, UUID newOwnerUserId) {
    UUID actorId = SecurityUtils.requireUserId();
    ChatRoom room = requireRoom(roomId);
    if (!room.getOwnerUserId().equals(actorId)) {
      throw new BusinessException(
          ErrorCode.FORBIDDEN, MessageConstants.CHAT_ONLY_OWNER_CAN_TRANSFER);
    }
    if (actorId.equals(newOwnerUserId)) {
      throw new BusinessException(
          ErrorCode.VALIDATION_ERROR, MessageConstants.CHAT_TRANSFER_MUST_BE_MEMBER);
    }

    ChatRoomMember currentOwner = requireActiveMember(roomId, actorId);
    ChatRoomMember newOwner = requireActiveMember(roomId, newOwnerUserId);

    currentOwner.setRole(ChatRoomMemberRole.ADMIN);
    newOwner.setRole(ChatRoomMemberRole.OWNER);
    memberRepository.save(currentOwner);
    memberRepository.save(newOwner);

    room.setOwnerUserId(newOwnerUserId);
    room.setUpdatedAt(Instant.now());
    roomRepository.save(room);

    notifyRoomListBump(roomId);
  }

  @Transactional
  public ChatRoomSummaryResponse setMembersPanelShared(UUID roomId, boolean shared) {
    UUID actorId = SecurityUtils.requireUserId();
    ChatRoom room = requireRoom(roomId);
    if (!room.getOwnerUserId().equals(actorId)) {
      throw new BusinessException(
          ErrorCode.FORBIDDEN, MessageConstants.CHAT_ONLY_OWNER_CAN_SHARE_MEMBERS_PANEL);
    }
    requireActiveMember(roomId, actorId);
    room.setMembersPanelShared(shared);
    room.setUpdatedAt(Instant.now());
    roomRepository.save(room);
    notifyRoomListBump(roomId);
    return toRoomSummary(room, actorId);
  }

  @Transactional
  public void deleteRoom(UUID roomId) {
    UUID userId = SecurityUtils.requireUserId();
    if (roomRepository.isSoftDeleted(roomId)) {
      return;
    }
    ChatRoom room = requireRoom(roomId);
    if (!room.getOwnerUserId().equals(userId)) {
      throw new BusinessException(ErrorCode.FORBIDDEN, MessageConstants.CHAT_ONLY_OWNER_CAN_DELETE);
    }

    Instant now = Instant.now();
    List<UUID> memberUserIds =
        memberRepository.findByRoomId(roomId).stream().map(ChatRoomMember::getUserId).toList();

    // Soft-delete via fields — repository.delete() can hard-delete and hit FK constraints.
    for (ChatRoomMember member : memberRepository.findByRoomId(roomId)) {
      member.setDeletedAt(now);
      memberRepository.save(member);
    }
    for (ChatRoomInvite invite : inviteRepository.findByRoomId(roomId)) {
      invite.setDeletedAt(now);
      inviteRepository.save(invite);
    }
    softDeleteAllMessages(roomId, now);
    room.setDeletedAt(now);
    roomRepository.save(room);

    for (UUID memberUserId : memberUserIds) {
      realtimeService.publishRoomDeleted(memberUserId, roomId);
    }
  }

  /** Hides all messages for the current member only; other members still see history. */
  @Transactional
  public void clearChat(UUID roomId) {
    UUID userId = SecurityUtils.requireUserId();
    ChatRoomMember member = requireActiveMember(roomId, userId);
    member.setChatClearedAt(Instant.now());
    memberRepository.save(member);
  }

  /** Removes all messages for everyone (owner or admin). */
  @Transactional
  public void clearChatHistory(UUID roomId) {
    UUID userId = SecurityUtils.requireUserId();
    ChatRoomMember actor = requireActiveMember(roomId, userId);
    if (!ChatRoomModerationRules.canClearHistory(actor)) {
      throw new BusinessException(ErrorCode.FORBIDDEN, MessageConstants.CHAT_ONLY_OWNER_CAN_CLEAR);
    }
    applyHistoryClear(roomId);
  }

  /** Used by decrypt reset — owner already authorized by caller. */
  @Transactional
  public void clearChatHistoryAsSystem(UUID roomId) {
    applyHistoryClear(roomId);
  }

  /**
   * Soft-deletes messages and advances the room history watermark so cleared notes disappear for
   * everyone — no "this message was deleted" tombstones (those are only for manual delete).
   */
  private void applyHistoryClear(UUID roomId) {
    ChatRoom room = requireRoom(roomId);
    Instant now = Instant.now();
    softDeleteAllMessages(roomId, now);
    room.setHistoryClearedAt(now);
    room.setUpdatedAt(now);
    roomRepository.save(room);

    for (ChatRoomMember member : memberRepository.findByRoomId(roomId)) {
      member.setChatClearedAt(now);
      memberRepository.save(member);
      realtimeService.publishChatCleared(member.getUserId(), roomId);
    }
  }

  /** Member exits the room; the room stays for everyone else. Owners must delete the room. */
  @Transactional
  public void leaveRoom(UUID roomId) {
    UUID userId = SecurityUtils.requireUserId();
    ChatRoom room = requireRoom(roomId);
    if (room.getOwnerUserId().equals(userId)) {
      throw new BusinessException(ErrorCode.FORBIDDEN, MessageConstants.CHAT_OWNER_CANNOT_LEAVE);
    }

    ChatRoomMember member = requireActiveMember(roomId, userId);
    member.setDeletedAt(Instant.now());
    memberRepository.save(member);

    room.setUpdatedAt(Instant.now());
    roomRepository.save(room);

    realtimeService.publishMemberLeft(userId, roomId);
    for (ChatRoomMember other : memberRepository.findByRoomId(roomId)) {
      realtimeService.publishRoomListBump(other.getUserId());
    }
  }

  @Transactional
  public void deleteMessage(UUID roomId, UUID messageId, ChatMessageDeleteScope scope) {
    if (scope == null) {
      scope = ChatMessageDeleteScope.FOR_ME;
    }
    if (scope == ChatMessageDeleteScope.FOR_ME) {
      hideMessageForUser(roomId, messageId);
      return;
    }
    deleteMessageForEveryone(roomId, messageId);
  }

  private void hideMessageForUser(UUID roomId, UUID messageId) {
    UUID userId = SecurityUtils.requireUserId();
    requireActiveMember(roomId, userId);
    requireRoom(roomId);

    ChatMessage message =
        messageRepository
            .findById(messageId)
            .orElseThrow(
                () ->
                    new BusinessException(
                        ErrorCode.NOT_FOUND, MessageConstants.CHAT_MESSAGE_NOT_FOUND));

    if (!message.getRoomId().equals(roomId) || message.getDeletedAt() != null) {
      throw new BusinessException(ErrorCode.NOT_FOUND, MessageConstants.CHAT_MESSAGE_NOT_FOUND);
    }

    if (messageHideRepository.existsByUserIdAndMessageIdAndDeletedAtIsNull(userId, messageId)) {
      return;
    }

    messageHideRepository.save(
        ChatMessageHide.builder().roomId(roomId).userId(userId).messageId(messageId).build());
  }

  private void deleteMessageForEveryone(UUID roomId, UUID messageId) {
    UUID userId = SecurityUtils.requireUserId();
    requireActiveMember(roomId, userId);
    requireRoom(roomId);

    ChatMessage message =
        messageRepository
            .findById(messageId)
            .orElseThrow(
                () ->
                    new BusinessException(
                        ErrorCode.NOT_FOUND, MessageConstants.CHAT_MESSAGE_NOT_FOUND));

    if (!message.getRoomId().equals(roomId)) {
      throw new BusinessException(ErrorCode.NOT_FOUND, MessageConstants.CHAT_MESSAGE_NOT_FOUND);
    }

    if (!message.getSenderUserId().equals(userId)) {
      throw new BusinessException(
          ErrorCode.FORBIDDEN, MessageConstants.CHAT_CANNOT_DELETE_FOR_EVERYONE);
    }

    purgeMessageForEveryone(roomId, message);
  }

  /**
   * Soft-deletes a message for all members, removes attachment bytes, and notifies via WebSocket.
   * Used by explicit delete-for-everyone and by auto-delete-when-everyone-read.
   */
  private void purgeMessageForEveryone(UUID roomId, ChatMessage message) {
    if (message.getDeletedAt() != null) {
      return;
    }
    UUID messageId = message.getId();
    attachmentStorageService.deleteQuietly(message.getAttachmentStorageKey());
    reactionService.deleteReactionsForMessage(messageId);
    message.setDeletedAt(Instant.now());
    message.setAttachmentStorageKey(null);
    messageRepository.save(message);

    ChatRoom room = requireRoom(roomId);
    room.setUpdatedAt(Instant.now());
    roomRepository.save(room);

    for (ChatRoomMember member : memberRepository.findByRoomId(roomId)) {
      realtimeService.publishMessageDeleted(member.getUserId(), roomId, messageId);
    }
  }

  @Transactional
  public void autoDeleteMessagesReadByEveryone(UUID roomId, List<UUID> messageIds) {
    if (!autoDeleteProperties.isEnabled() || messageIds == null || messageIds.isEmpty()) {
      return;
    }
    for (UUID messageId : messageIds) {
      messageRepository
          .findById(messageId)
          .filter(message -> message.getRoomId().equals(roomId))
          .filter(message -> message.getDeletedAt() == null)
          .filter(
              message ->
                  readReceiptService.isReadyForAutoDelete(
                      messageId, autoDeleteProperties.getGraceAfterRead()))
          .ifPresent(message -> purgeMessageForEveryone(roomId, message));
    }
  }

  /** Called by the scheduler: purge messages whose full-read grace period has elapsed. */
  @Transactional
  public int sweepAutoDeleteReadyMessages() {
    if (!autoDeleteProperties.isEnabled()) {
      return 0;
    }
    java.time.Duration grace = autoDeleteProperties.getGraceAfterRead();
    Instant cutoff =
        Instant.now().minus(grace == null || grace.isNegative() ? java.time.Duration.ZERO : grace);
    List<UUID> ready = messageRepository.findIdsReadyForAutoDelete(cutoff);
    int purged = 0;
    for (UUID messageId : ready) {
      ChatMessage message = messageRepository.findById(messageId).orElse(null);
      if (message == null || message.getDeletedAt() != null) {
        continue;
      }
      if (!readReceiptService.isReadyForAutoDelete(messageId, grace)) {
        continue;
      }
      purgeMessageForEveryone(message.getRoomId(), message);
      purged++;
    }
    return purged;
  }

  /** Erase deleted-for-everyone bodies once the room owner’s 24-hour window has closed. */
  @Transactional
  public int wipeExpiredDeletedMessageBodies() {
    Instant cutoff = Instant.now().minus(DeletedMessageRetention.RETENTION);
    return messageRepository.blankDeletedBodiesBefore(cutoff);
  }

  @Transactional
  public List<ChatMessageReactionSummary> setMessageReaction(
      UUID roomId, UUID messageId, String emoji) {
    UUID userId = SecurityUtils.requireUserId();
    requireActiveMember(roomId, userId);

    List<ChatMessageReactionSummary> reactions =
        reactionService.toggleReaction(roomId, messageId, userId, emoji, this::displayLabel);

    for (ChatRoomMember member : memberRepository.findByRoomId(roomId)) {
      realtimeService.publishMessageReactionsUpdated(
          member.getUserId(), roomId, messageId, reactions);
    }

    return reactions;
  }

  @Transactional
  public ChatMessageResponse editMessage(UUID roomId, UUID messageId, SendChatMessageRequest request) {
    UUID userId = SecurityUtils.requireUserId();
    requireActiveMember(roomId, userId);
    requireRoom(roomId);

    ChatMessage message =
        messageRepository
            .findById(messageId)
            .orElseThrow(
                () ->
                    new BusinessException(
                        ErrorCode.NOT_FOUND, MessageConstants.CHAT_MESSAGE_NOT_FOUND));

    if (!message.getRoomId().equals(roomId)) {
      throw new BusinessException(ErrorCode.NOT_FOUND, MessageConstants.CHAT_MESSAGE_NOT_FOUND);
    }

    if (!message.getSenderUserId().equals(userId)) {
      throw new BusinessException(ErrorCode.FORBIDDEN, MessageConstants.CHAT_CANNOT_EDIT_MESSAGE);
    }

    String body = request.getBody().trim();
    if (message.getMessageType() == ChatMessageType.TEXT && body.isEmpty()) {
      throw new BusinessException(ErrorCode.VALIDATION_ERROR, MessageConstants.CHAT_MESSAGE_BODY_REQUIRED);
    }
    if (body.length() > ValidationConstants.CHAT_MESSAGE_BODY_MAX_LENGTH) {
      throw new BusinessException(
          ErrorCode.VALIDATION_ERROR, MessageConstants.CHAT_CAPTION_TOO_LONG);
    }

    if (body.equals(message.getBody())) {
      return toMessageResponse(message, userId);
    }

    message.setBody(body);
    message.setEditedAt(Instant.now());
    message = messageRepository.save(message);

    ChatRoom room = requireRoom(roomId);
    room.setUpdatedAt(Instant.now());
    roomRepository.save(room);

    ChatMessageResponse response = toMessageResponse(message, userId);
    publishMessageUpdateToRoom(roomId, message);
    return response;
  }

  @Transactional
  public ChatInviteResponse createInvite(UUID roomId, CreateChatInviteRequest request) {
    UUID inviterUserId = SecurityUtils.requireUserId();
    requireActiveMember(roomId, inviterUserId);
    ChatRoom room = requireRoom(roomId);

    User invitee = resolveParentUser(request.getIdentifier().trim());
    if (invitee.getId().equals(inviterUserId)) {
      throw new BusinessException(
          ErrorCode.VALIDATION_ERROR, MessageConstants.CHAT_CANNOT_INVITE_YOURSELF);
    }
    if (memberRepository.existsByRoomIdAndUserId(roomId, invitee.getId())) {
      throw new BusinessException(
          ErrorCode.CONFLICT, MessageConstants.CHAT_USER_ALREADY_IN_ROOM);
    }

    inviteRepository
        .findByRoomIdAndInviteeUserIdAndStatus(roomId, invitee.getId(), ChatRoomInviteStatus.PENDING)
        .ifPresent(
            existing -> {
              throw new BusinessException(
                  ErrorCode.CONFLICT, MessageConstants.CHAT_INVITE_ALREADY_PENDING);
            });

    ChatRoomInvite invite =
        inviteRepository.save(
            ChatRoomInvite.builder()
                .roomId(roomId)
                .inviterUserId(inviterUserId)
                .inviteeUserId(invitee.getId())
                .inviteeIdentifier(request.getIdentifier().trim())
                .status(ChatRoomInviteStatus.PENDING)
                .build());

    ChatInviteResponse response = toInviteResponse(invite, room.getName());
    realtimeService.publishInvite(invitee.getId(), response);
    return response;
  }

  @Transactional(readOnly = true)
  public List<ChatInviteResponse> listPendingInvites() {
    UUID userId = SecurityUtils.requireUserId();
    List<ChatInviteResponse> responses = new ArrayList<>();
    for (ChatRoomInvite invite :
        inviteRepository.findByInviteeUserIdAndStatusOrderByCreatedAtDesc(
            userId, ChatRoomInviteStatus.PENDING)) {
      String roomName =
          roomRepository.findById(invite.getRoomId()).map(ChatRoom::getName).orElse("Chat room");
      responses.add(toInviteResponse(invite, roomName));
    }
    return responses;
  }

  @Transactional
  public ChatInviteResponse respondToInvite(UUID inviteId, RespondChatInviteRequest request) {
    UUID userId = SecurityUtils.requireUserId();
    ChatRoomInvite invite =
        inviteRepository
            .findById(inviteId)
            .orElseThrow(
                () ->
                    new BusinessException(ErrorCode.NOT_FOUND, MessageConstants.CHAT_INVITE_NOT_FOUND));

    if (!invite.getInviteeUserId().equals(userId)) {
      throw new BusinessException(ErrorCode.FORBIDDEN, MessageConstants.CHAT_NOT_A_MEMBER);
    }
    if (invite.getStatus() != ChatRoomInviteStatus.PENDING) {
      throw new BusinessException(
          ErrorCode.CONFLICT, MessageConstants.CHAT_INVITE_ALREADY_RESPONDED);
    }

    ChatRoom room = requireRoom(invite.getRoomId());
    invite.setRespondedAt(Instant.now());

    if (Boolean.TRUE.equals(request.getAccept())) {
      invite.setStatus(ChatRoomInviteStatus.ACCEPTED);
      Optional<ChatRoomMember> existingMembership =
          memberRepository.findAnyByRoomIdAndUserId(room.getId(), userId);
      if (existingMembership.isPresent()) {
        ChatRoomMember membership = existingMembership.get();
        if (membership.getDeletedAt() != null) {
          membership.setDeletedAt(null);
          membership.setJoinedAt(Instant.now());
          membership.setRole(ChatRoomMemberRole.MEMBER);
          memberRepository.save(membership);
        }
      } else {
        memberRepository.save(
            ChatRoomMember.builder()
                .roomId(room.getId())
                .userId(userId)
                .role(ChatRoomMemberRole.MEMBER)
                .joinedAt(Instant.now())
                .build());
      }
      room.setUpdatedAt(Instant.now());
      roomRepository.save(room);
      notifyRoomListBump(room.getId());
    } else {
      invite.setStatus(ChatRoomInviteStatus.DECLINED);
    }

    inviteRepository.save(invite);
    ChatInviteResponse response = toInviteResponse(invite, room.getName());
    realtimeService.publishInviteResponded(invite.getInviterUserId(), response);
    if (Boolean.TRUE.equals(request.getAccept())) {
      realtimeService.publishRoomListBump(userId);
    }
    return response;
  }

  @Transactional(readOnly = true)
  public List<ChatMessageResponse> listMessages(
      UUID roomId, UUID channelId, int page, int size, boolean includeDeleted) {
    UUID userId = SecurityUtils.requireUserId();
    ChatRoomMember member = requireActiveMember(roomId, userId);
    UUID resolvedChannelId = channelService.resolveTextChannel(roomId, channelId).getId();

    ChatRoom room = requireRoom(roomId);
    Instant clearedAfter =
        ChatHistoryClearRules.effectiveClearedAfter(
            member.getChatClearedAt(), room.getHistoryClearedAt());

    boolean ownerDeletedView =
        ChatRoomModerationRules.mayIncludeDeletedMessages(includeDeleted, member);
    if (ownerDeletedView) {
      deletedMessagesPinService.requireUnlockedForDeletedView();
    }
    Page<ChatMessage> messages =
        ownerDeletedView
            ? messageRepository.findForOwnerIncludingDeletedInChannel(
                roomId,
                resolvedChannelId,
                userId,
                clearedAfter,
                Instant.now().minus(DeletedMessageRetention.RETENTION),
                PageRequest.of(page, size))
            : messageRepository.findVisibleForMemberInChannelOrderByCreatedAtDesc(
                roomId,
                resolvedChannelId,
                userId,
                clearedAfter,
                tombstoneVisibleAfter(),
                PageRequest.of(page, size));

    List<ChatMessage> content = messages.getContent();
    List<UUID> messageIds = content.stream().map(ChatMessage::getId).toList();
    Map<UUID, List<ChatMessageReactionSummary>> reactionsByMessage =
        reactionService.summariesByMessageIds(messageIds, userId, this::displayLabel);
    Map<UUID, Boolean> unreadByMessage =
        readReceiptService.unreadForViewer(
            content, userId, member.getLastReadMessageId(), member.getUnreadFromAt());
    Map<UUID, Instant> readAtByMessage = readReceiptService.readAtForViewer(messageIds, userId);

    List<ChatMessageResponse> responses = new ArrayList<>();
    for (ChatMessage message : content) {
      responses.add(
          toMessageResponse(
              message,
              userId,
              reactionsByMessage.getOrDefault(message.getId(), List.of()),
              unreadByMessage,
              ownerDeletedView,
              readAtByMessage));
    }
    Collections.reverse(responses);
    return responses;
  }

  @Transactional
  public ChatMessageResponse sendMessage(UUID roomId, SendChatMessageRequest request) {
    UUID userId = SecurityUtils.requireUserId();
    requireNotMuted(requireActiveMember(roomId, userId));
    ChatRoom room = requireRoom(roomId);
    UUID channelId = channelService.resolveTextChannel(roomId, request.getChannelId()).getId();

    String body = request.getBody().trim();
    UUID replyToMessageId = resolveReplyTarget(roomId, request.getReplyToMessageId());
    ChatMessage message =
        messageRepository.save(
            ChatMessage.builder()
                .roomId(roomId)
                .channelId(channelId)
                .senderUserId(userId)
                .body(body)
                .messageType(ChatMessageType.TEXT)
                .replyToMessageId(replyToMessageId)
                .build());

    room.setUpdatedAt(Instant.now());
    roomRepository.save(room);

    ChatMessageResponse response = toMessageResponse(message, userId);
    readReceiptService.createReceiptsForMessage(message);
    // Sending catches you up (WhatsApp): advance read cursor through this message.
    readReceiptService.markRead(roomId, userId, message.getId());
    publishMessageToRoom(roomId, message);
    notifyRoomListBump(roomId);
    return response;
  }

  @Transactional
  public void markMessagesDelivered(UUID roomId, MarkChatDeliveredRequest request) {
    UUID userId = SecurityUtils.requireUserId();
    readReceiptService.markDelivered(roomId, userId, request.getMessageIds());
  }

  @Transactional
  public void markRoomRead(UUID roomId, MarkChatReadRequest request) {
    UUID userId = SecurityUtils.requireUserId();
    List<UUID> fullyRead =
        readReceiptService.markRead(roomId, userId, request.getLastMessageId());
    autoDeleteMessagesReadByEveryone(roomId, fullyRead);
    notifyRoomListBump(roomId);
  }

  @Transactional
  public void markMessageUnread(UUID roomId, UUID messageId) {
    UUID userId = SecurityUtils.requireUserId();
    requireActiveMember(roomId, userId);
    readReceiptService.markUnread(roomId, userId, messageId);
    notifyRoomListBump(roomId);
  }

  /** Ephemeral typing signal — not persisted. Fan-out to other room members only. */
  @Transactional(readOnly = true)
  public void notifyTyping(UUID roomId, boolean typing) {
    UUID userId = SecurityUtils.requireUserId();
    requireNotMuted(requireActiveMember(roomId, userId));
    requireRoom(roomId);
    String nickname = chatNicknameForUser(userId);
    String label = displayLabel(userId);
    for (ChatRoomMember member : memberRepository.findByRoomId(roomId)) {
      if (member.getUserId().equals(userId)) {
        continue;
      }
      try {
        realtimeService.publishTyping(
            member.getUserId(), roomId, userId, nickname, label, typing);
      } catch (RuntimeException e) {
        log.warn(
            "Failed to publish typing roomId={} typingUserId={} recipientUserId={}",
            roomId,
            userId,
            member.getUserId(),
            e);
      }
    }
  }

  @Transactional
  public ChatMessageResponse sendMediaMessage(
      UUID roomId,
      MultipartFile file,
      String caption,
      UUID replyToMessageId,
      UUID channelId) {
    UUID userId = SecurityUtils.requireUserId();
    requireNotMuted(requireActiveMember(roomId, userId));
    ChatRoom room = requireRoom(roomId);
    UUID resolvedChannelId = channelService.resolveTextChannel(roomId, channelId).getId();

    String body = caption != null ? caption.trim() : "";
    if (body.length() > ValidationConstants.CHAT_MESSAGE_BODY_MAX_LENGTH) {
      throw new BusinessException(
          ErrorCode.VALIDATION_ERROR, MessageConstants.CHAT_CAPTION_TOO_LONG);
    }

    UUID resolvedReplyTo = resolveReplyTarget(roomId, replyToMessageId);
    ChatAttachmentStorageService.ValidatedAttachment validated =
        attachmentStorageService.validate(file);

    ChatMessage message =
        messageRepository.save(
            ChatMessage.builder()
                .roomId(roomId)
                .channelId(resolvedChannelId)
                .senderUserId(userId)
                .body(body)
                .messageType(ChatMessageType.TEXT)
                .replyToMessageId(resolvedReplyTo)
                .build());

    try {
      ChatAttachmentStorageService.StoredAttachment stored =
          attachmentStorageService.store(roomId, message.getId(), file, validated);

      message.setMessageType(stored.messageType());
      message.setAttachmentStorageKey(stored.storageKey());
      message.setAttachmentMimeType(stored.mimeType());
      message.setAttachmentSizeBytes(stored.sizeBytes());
      message.setAttachmentFileName(stored.originalFileName());
      message.setAttachmentViewOnce(true);
      message = messageRepository.save(message);
    } catch (RuntimeException e) {
      messageRepository.delete(message);
      throw e;
    }

    room.setUpdatedAt(Instant.now());
    roomRepository.save(room);

    ChatMessageResponse response = toMessageResponse(message, userId);
    readReceiptService.createReceiptsForMessage(message);
    readReceiptService.markRead(roomId, userId, message.getId());
    publishMessageToRoom(roomId, message);
    notifyRoomListBump(roomId);
    return response;
  }

  @Transactional(readOnly = true)
  public AttachmentPayload loadAttachment(UUID roomId, UUID messageId) {
    UUID userId = SecurityUtils.requireUserId();
    requireActiveMember(roomId, userId);
    ChatMessage message = requireMediaMessage(roomId, messageId);

    if (message.isAttachmentViewOnce()) {
      throw new BusinessException(
          ErrorCode.FORBIDDEN, MessageConstants.CHAT_ATTACHMENT_USE_VIEW_ENDPOINT);
    }
    if (message.getAttachmentConsumedAt() != null
        || !StringUtils.hasText(message.getAttachmentStorageKey())) {
      throw new BusinessException(
          ErrorCode.NOT_FOUND, MessageConstants.CHAT_ATTACHMENT_NOT_FOUND);
    }

    Resource resource = attachmentStorageService.loadAsResource(message.getAttachmentStorageKey());
    MediaType mediaType = attachmentStorageService.mediaTypeFor(message.getAttachmentMimeType());
    return new AttachmentPayload(resource, mediaType, message.getAttachmentFileName());
  }

  /** Opens a view-once attachment for the first time, then deletes it from storage. */
  @Transactional
  public AttachmentPayload viewOnceAttachment(UUID roomId, UUID messageId) {
    UUID userId = SecurityUtils.requireUserId();
    requireActiveMember(roomId, userId);
    ChatMessage message = requireMediaMessage(roomId, messageId);

    if (!message.isAttachmentViewOnce()) {
      return loadAttachment(roomId, messageId);
    }
    if (message.getAttachmentConsumedAt() != null
        || !StringUtils.hasText(message.getAttachmentStorageKey())) {
      throw new BusinessException(
          ErrorCode.NOT_FOUND, MessageConstants.CHAT_ATTACHMENT_ALREADY_VIEWED);
    }
    if (message.getSenderUserId().equals(userId)) {
      throw new BusinessException(
          ErrorCode.FORBIDDEN, MessageConstants.CHAT_SENDER_CANNOT_VIEW_ONCE);
    }

    String storageKey = message.getAttachmentStorageKey();
    byte[] bytes = attachmentStorageService.readAndDelete(storageKey);
    message.setAttachmentConsumedAt(Instant.now());
    message.setAttachmentConsumedByUserId(userId);
    message.setAttachmentStorageKey(null);
    message = messageRepository.save(message);

    publishMessageUpdateToRoom(roomId, message);

    MediaType mediaType = attachmentStorageService.mediaTypeFor(message.getAttachmentMimeType());
    Resource resource = new ByteArrayResource(bytes);
    return new AttachmentPayload(resource, mediaType, message.getAttachmentFileName());
  }

  private ChatMessage requireMediaMessage(UUID roomId, UUID messageId) {
    ChatMessage message =
        messageRepository
            .findById(messageId)
            .orElseThrow(
                () ->
                    new BusinessException(
                        ErrorCode.NOT_FOUND, MessageConstants.CHAT_ATTACHMENT_NOT_FOUND));

    if (!message.getRoomId().equals(roomId)
        || message.getMessageType() == ChatMessageType.TEXT) {
      throw new BusinessException(
          ErrorCode.NOT_FOUND, MessageConstants.CHAT_ATTACHMENT_NOT_FOUND);
    }
    return message;
  }

  public record AttachmentPayload(Resource resource, MediaType mediaType, String fileName) {}

  private void softDeleteAllMessages(UUID roomId, Instant now) {
    for (ChatMessage message : messageRepository.findActiveByRoomId(roomId)) {
      attachmentStorageService.deleteQuietly(message.getAttachmentStorageKey());
    }
    messageRepository.softDeleteAllByRoomId(roomId, now);
  }

  private User resolveParentUser(String identifier) {
    if (looksLikeEmail(identifier)) {
      User user =
          userDao
              .findByEmailIgnoreCase(identifier.toLowerCase(Locale.ROOT))
              .orElseThrow(
                  () ->
                      new BusinessException(
                          ErrorCode.NOT_FOUND, MessageConstants.PARENT_ACCOUNT_NOT_FOUND));
      requireParentRole(user);
      return user;
    }

    ParentProfile profile =
        familyHandleService
            .findByHandle(identifier)
            .orElseThrow(
                () ->
                    new BusinessException(
                        ErrorCode.NOT_FOUND, MessageConstants.PARENT_ACCOUNT_NOT_FOUND));
    requireParentRole(profile.getUser());
    return profile.getUser();
  }

  private static boolean looksLikeEmail(String identifier) {
    return EMAIL_PATTERN.matcher(identifier).matches();
  }

  private static void requireParentRole(User user) {
    if (user.getRole() != Role.ROLE_PARENT) {
      throw new BusinessException(
          ErrorCode.NOT_FOUND, MessageConstants.PARENT_ACCOUNT_NOT_FOUND);
    }
  }

  private ChatRoom requireRoom(UUID roomId) {
    return roomRepository
        .findById(roomId)
        .orElseThrow(
            () -> new BusinessException(ErrorCode.NOT_FOUND, MessageConstants.CHAT_ROOM_NOT_FOUND));
  }

  private ChatRoomMember requireActiveMember(UUID roomId, UUID userId) {
    return memberRepository
        .findByRoomIdAndUserId(roomId, userId)
        .orElseThrow(
            () -> new BusinessException(ErrorCode.FORBIDDEN, MessageConstants.CHAT_NOT_A_MEMBER));
  }

  private void notifyRoomListBump(UUID roomId) {
    for (ChatRoomMember member : memberRepository.findByRoomId(roomId)) {
      realtimeService.publishRoomListBump(member.getUserId());
    }
  }

  private ChatRoomSummaryResponse toRoomSummary(ChatRoom room, UUID viewerUserId) {
    return toRoomSummary(
        room,
        viewerUserId,
        channelService
            .channelsByRoomIds(List.of(room.getId()))
            .getOrDefault(room.getId(), List.of()));
  }

  private ChatRoomSummaryResponse toRoomSummary(
      ChatRoom room,
      UUID viewerUserId,
      List<com.parentalcontrol.chat.dto.ChatChannelResponse> channels) {
    int memberCount = memberRepository.findByRoomId(room.getId()).size();
    boolean owner = room.getOwnerUserId().equals(viewerUserId);
    ChatRoomMember member =
        memberRepository.findByRoomIdAndUserId(room.getId(), viewerUserId).orElse(null);
    Instant chatClearedAt = member != null ? member.getChatClearedAt() : null;
    UUID lastReadMessageId = member != null ? member.getLastReadMessageId() : null;
    Instant unreadFromAt = member != null ? member.getUnreadFromAt() : null;
    int unreadCount =
        readReceiptService.countUnread(
            room.getId(), viewerUserId, lastReadMessageId, unreadFromAt);
    ChatRoomMemberRole myRole = member != null ? member.getRole() : ChatRoomMemberRole.MEMBER;
    boolean muted = member != null && member.isMuted();
    boolean canDecrypt =
        member != null && ChatRoomModerationRules.canDecrypt(member);
    return ChatRoomSummaryResponse.builder()
        .id(room.getId())
        .name(room.getName())
        .ownerUserId(room.getOwnerUserId())
        .owner(owner)
        .myRole(myRole)
        .muted(muted)
        .canDecrypt(canDecrypt)
        .memberCount(memberCount)
        .directMessage(room.isDirectMessage())
        .membersPanelShared(room.isMembersPanelShared())
        .createdAt(room.getCreatedAt())
        .updatedAt(room.getUpdatedAt())
        .chatClearedAt(chatClearedAt)
        .lastReadMessageId(lastReadMessageId)
        .unreadCount(unreadCount)
        .channels(channels)
        .build();
  }

  private ChatMemberResponse toMemberResponse(ChatRoomMember member) {
    boolean decryptEffective = ChatRoomModerationRules.canDecrypt(member);
    return ChatMemberResponse.builder()
        .userId(member.getUserId())
        .label(displayLabel(member.getUserId()))
        .nickname(chatNicknameForUser(member.getUserId()))
        .role(member.getRole())
        .joinedAt(member.getJoinedAt())
        .muted(member.isMuted())
        .canDecrypt(decryptEffective)
        .build();
  }

  private void requireNotMuted(ChatRoomMember member) {
    if (member.isMuted()) {
      throw new BusinessException(ErrorCode.FORBIDDEN, MessageConstants.CHAT_MEMBER_MUTED);
    }
  }

  private ChatInviteResponse toInviteResponse(ChatRoomInvite invite, String roomName) {
    return ChatInviteResponse.builder()
        .id(invite.getId())
        .roomId(invite.getRoomId())
        .roomName(roomName)
        .inviterUserId(invite.getInviterUserId())
        .inviterLabel(displayLabel(invite.getInviterUserId()))
        .inviterNickname(chatNicknameForUser(invite.getInviterUserId()))
        .inviteeIdentifier(invite.getInviteeIdentifier())
        .status(invite.getStatus())
        .createdAt(invite.getCreatedAt())
        .build();
  }

  private void publishMessageToRoom(UUID roomId, ChatMessage message) {
    for (ChatRoomMember member : memberRepository.findByRoomId(roomId)) {
      try {
        realtimeService.publishMessage(
            member.getUserId(), toMessageResponse(message, member.getUserId()));
      } catch (RuntimeException e) {
        log.warn(
            "Failed to publish chat message roomId={} messageId={} recipientUserId={}",
            roomId,
            message.getId(),
            member.getUserId(),
            e);
      }
    }
  }

  private void publishMessageUpdateToRoom(UUID roomId, ChatMessage message) {
    for (ChatRoomMember member : memberRepository.findByRoomId(roomId)) {
      realtimeService.publishMessage(
          member.getUserId(), toMessageResponse(message, member.getUserId()));
    }
  }

  private ChatMessageResponse toMessageResponse(ChatMessage message, UUID viewerUserId) {
    List<ChatMessageReactionSummary> reactions =
        reactionService
            .summariesByMessageIds(List.of(message.getId()), viewerUserId, this::displayLabel)
            .getOrDefault(message.getId(), List.of());
    ChatRoomMember member =
        memberRepository
            .findByRoomIdAndUserId(message.getRoomId(), viewerUserId)
            .orElse(null);
    Map<UUID, Boolean> unreadByMessage =
        member == null
            ? Map.of()
            : readReceiptService.unreadForViewer(
                List.of(message),
                viewerUserId,
                member.getLastReadMessageId(),
                member.getUnreadFromAt());
    return toMessageResponse(message, viewerUserId, reactions, unreadByMessage, false, null);
  }

  private ChatMessageResponse toMessageResponse(
      ChatMessage message,
      UUID viewerUserId,
      List<ChatMessageReactionSummary> reactions) {
    return toMessageResponse(message, viewerUserId, reactions, null, false, null);
  }

  private ChatMessageResponse toMessageResponse(
      ChatMessage message,
      UUID viewerUserId,
      List<ChatMessageReactionSummary> reactions,
      Map<UUID, Boolean> unreadByMessage,
      boolean revealDeletedBody,
      Map<UUID, Instant> readAtByMessage) {
    boolean ownMessage = message.getSenderUserId().equals(viewerUserId);
    boolean consumed =
        message.getAttachmentConsumedAt() != null
            || !StringUtils.hasText(message.getAttachmentStorageKey());
    boolean isMedia = message.getMessageType() != ChatMessageType.TEXT;
    boolean canViewAttachment =
        isMedia
            && message.isAttachmentViewOnce()
            && !consumed
            && !ownMessage;

    String attachmentUrl = null;
    if (isMedia && !message.isAttachmentViewOnce() && !consumed) {
      attachmentUrl =
          ApiConstants.CHAT
              + "/rooms/"
              + message.getRoomId()
              + "/messages/"
              + message.getId()
              + "/attachment";
    }

    boolean deleted = message.getDeletedAt() != null;
    boolean redactDeleted = deleted && !revealDeletedBody;
    if (redactDeleted) {
      attachmentUrl = null;
      canViewAttachment = false;
    }

    boolean unread = false;
    if (!ownMessage) {
      if (unreadByMessage != null) {
        unread = Boolean.TRUE.equals(unreadByMessage.get(message.getId()));
      } else {
        unread =
            Boolean.TRUE.equals(
                readReceiptService
                    .unreadForViewer(List.of(message.getId()), viewerUserId)
                    .get(message.getId()));
      }
    }

    return ChatMessageResponse.builder()
        .id(message.getId())
        .roomId(message.getRoomId())
        .channelId(message.getChannelId())
        .senderUserId(message.getSenderUserId())
        .senderLabel(displayLabel(message.getSenderUserId()))
        .senderNickname(chatNicknameForUser(message.getSenderUserId()))
        .messageType(message.getMessageType())
        .body(redactDeleted ? "" : message.getBody())
        .attachmentUrl(attachmentUrl)
        .attachmentMimeType(message.getAttachmentMimeType())
        .attachmentFileName(message.getAttachmentFileName())
        .attachmentSizeBytes(message.getAttachmentSizeBytes())
        .attachmentViewOnce(message.isAttachmentViewOnce())
        .attachmentConsumed(consumed)
        .ownMessage(ownMessage)
        .canViewAttachment(canViewAttachment)
        .createdAt(message.getCreatedAt())
        .editedAt(message.getEditedAt())
        .receiptStatus(ownMessage ? readReceiptService.statusForMessage(message.getId()) : null)
        .unread(unread)
        .deleted(deleted)
        .deletedVisibleUntil(
            deletedNoticeUntil(message, ownMessage, revealDeletedBody, readAtByMessage))
        .replyTo(buildReplyPreview(message))
        .reactions(reactions)
        .build();
  }

  private Instant tombstoneVisibleAfter() {
    java.time.Duration grace = autoDeleteProperties.getGraceAfterRead();
    if (grace == null || grace.isNegative()) {
      grace = java.time.Duration.ZERO;
    }
    return Instant.now().minus(grace);
  }

  /** Sender: grace after delete. Recipient who already read the notice: grace after that read. */
  private Instant deletedNoticeUntil(
      ChatMessage message,
      boolean ownMessage,
      boolean revealDeletedBody,
      Map<UUID, Instant> readAtByMessage) {
    if (message.getDeletedAt() == null || revealDeletedBody) {
      return null;
    }
    java.time.Duration grace = autoDeleteProperties.getGraceAfterRead();
    if (grace == null || grace.isNegative()) {
      grace = java.time.Duration.ZERO;
    }
    if (ownMessage) {
      return message.getDeletedAt().plus(grace);
    }
    if (readAtByMessage == null) {
      return null;
    }
    Instant readAt = readAtByMessage.get(message.getId());
    return readAt == null ? null : readAt.plus(grace);
  }

  private UUID resolveReplyTarget(UUID roomId, UUID replyToMessageId) {
    if (replyToMessageId == null) {
      return null;
    }
    if (!messageRepository.existsActiveInRoom(replyToMessageId, roomId)) {
      throw new BusinessException(
          ErrorCode.NOT_FOUND, MessageConstants.CHAT_REPLY_MESSAGE_NOT_FOUND);
    }
    return replyToMessageId;
  }

  private ChatMessageReplyPreview buildReplyPreview(ChatMessage message) {
    UUID replyToId = message.getReplyToMessageId();
    if (replyToId == null) {
      return null;
    }

    return messageRepository
        .findIncludingDeletedByIdAndRoomId(replyToId, message.getRoomId())
        .map(
            target -> {
              boolean deleted = target.getDeletedAt() != null;
              return ChatMessageReplyPreview.builder()
                  .messageId(replyToId)
                  .senderLabel(displayLabel(target.getSenderUserId()))
                  .senderNickname(chatNicknameForUser(target.getSenderUserId()))
                  .messageType(target.getMessageType())
                  .bodyPreview(deleted ? "" : replyBodyPreview(target))
                  .deleted(deleted)
                  .build();
            })
        .orElse(
            ChatMessageReplyPreview.builder()
                .messageId(replyToId)
                .senderLabel("")
                .senderNickname("")
                .messageType(ChatMessageType.TEXT)
                .bodyPreview("")
                .deleted(true)
                .build());
  }

  private static String replyBodyPreview(ChatMessage message) {
    if (message.getMessageType() == ChatMessageType.IMAGE) {
      return StringUtils.hasText(message.getBody()) ? message.getBody() : "Photo";
    }
    if (message.getMessageType() == ChatMessageType.VIDEO) {
      return StringUtils.hasText(message.getBody()) ? message.getBody() : "Video";
    }
    String body = message.getBody();
    if (!StringUtils.hasText(body)) {
      return "";
    }
    return body.length() <= 240 ? body : body.substring(0, 237) + "…";
  }

  private String chatNicknameForUser(UUID userId) {
    return ChatNicknameGenerator.fromEmail(resolveUserEmail(userId));
  }

  private String resolveUserEmail(UUID userId) {
    return userDao.findById(userId).map(User::getEmail).orElse("");
  }

  private String displayLabel(UUID userId) {
    return parentProfileDao
        .findByUser_Id(userId)
        .map(
            profile -> {
              if (familyHandleService.isConfigured(profile)) {
                return "@" + profile.getFamilyHandle();
              }
              return userDao
                  .findById(userId)
                  .map(User::getEmail)
                  .orElse("Parent");
            })
        .orElse("Parent");
  }
}
