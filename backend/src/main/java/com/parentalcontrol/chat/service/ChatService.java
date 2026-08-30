package com.parentalcontrol.chat.service;

import com.parentalcontrol.chat.domain.ChatMessage;
import com.parentalcontrol.chat.domain.ChatRoom;
import com.parentalcontrol.chat.domain.ChatRoomInvite;
import com.parentalcontrol.chat.domain.ChatRoomInviteStatus;
import com.parentalcontrol.chat.domain.ChatRoomMember;
import com.parentalcontrol.chat.domain.ChatRoomMemberRole;
import com.parentalcontrol.chat.dto.ChatInviteResponse;
import com.parentalcontrol.chat.dto.ChatMemberResponse;
import com.parentalcontrol.chat.dto.ChatMessageResponse;
import com.parentalcontrol.chat.dto.ChatRoomSummaryResponse;
import com.parentalcontrol.chat.dto.CreateChatInviteRequest;
import com.parentalcontrol.chat.dto.CreateChatRoomRequest;
import com.parentalcontrol.chat.dto.RespondChatInviteRequest;
import com.parentalcontrol.chat.dto.SendChatMessageRequest;
import com.parentalcontrol.chat.repository.ChatMessageRepository;
import com.parentalcontrol.chat.repository.ChatRoomInviteRepository;
import com.parentalcontrol.chat.repository.ChatRoomMemberRepository;
import com.parentalcontrol.chat.repository.ChatRoomRepository;
import com.parentalcontrol.common.constant.MessageConstants;
import com.parentalcontrol.common.domain.Role;
import com.parentalcontrol.common.exception.BusinessException;
import com.parentalcontrol.common.exception.ErrorCode;
import com.parentalcontrol.common.security.SecurityUtils;
import com.parentalcontrol.user.dao.ParentProfileDao;
import com.parentalcontrol.user.dao.UserDao;
import com.parentalcontrol.user.domain.ParentProfile;
import com.parentalcontrol.user.domain.User;
import com.parentalcontrol.user.service.FamilyHandleService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ChatService {

  private static final Pattern EMAIL_PATTERN =
      Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

  private final ChatRoomRepository roomRepository;
  private final ChatRoomMemberRepository memberRepository;
  private final ChatRoomInviteRepository inviteRepository;
  private final ChatMessageRepository messageRepository;
  private final ChatRealtimeService realtimeService;
  private final FamilyHandleService familyHandleService;
  private final ParentProfileDao parentProfileDao;
  private final UserDao userDao;

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

    return toRoomSummary(room, userId);
  }

  @Transactional(readOnly = true)
  public List<ChatRoomSummaryResponse> listRooms() {
    UUID userId = SecurityUtils.requireUserId();
    return roomRepository.findAllForMember(userId).stream()
        .map(room -> toRoomSummary(room, userId))
        .toList();
  }

  @Transactional(readOnly = true)
  public List<ChatMemberResponse> listMembers(UUID roomId) {
    UUID userId = SecurityUtils.requireUserId();
    requireActiveMember(roomId, userId);
    return memberRepository.findByRoomId(roomId).stream()
        .map(
            member ->
                ChatMemberResponse.builder()
                    .userId(member.getUserId())
                    .label(displayLabel(member.getUserId()))
                    .role(member.getRole())
                    .joinedAt(member.getJoinedAt())
                    .build())
        .toList();
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
    for (ChatMessage message : messageRepository.findByRoomId(roomId)) {
      message.setDeletedAt(now);
      messageRepository.save(message);
    }
    room.setDeletedAt(now);
    roomRepository.save(room);

    for (UUID memberUserId : memberUserIds) {
      realtimeService.publishRoomDeleted(memberUserId, roomId);
    }
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
      if (!memberRepository.existsByRoomIdAndUserId(room.getId(), userId)) {
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
  public List<ChatMessageResponse> listMessages(UUID roomId, int page, int size) {
    UUID userId = SecurityUtils.requireUserId();
    requireActiveMember(roomId, userId);

    Page<ChatMessage> messages =
        messageRepository.findByRoomIdOrderByCreatedAtDesc(
            roomId, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));

    List<ChatMessageResponse> responses = new ArrayList<>();
    for (ChatMessage message : messages.getContent()) {
      responses.add(toMessageResponse(message));
    }
    Collections.reverse(responses);
    return responses;
  }

  @Transactional
  public ChatMessageResponse sendMessage(UUID roomId, SendChatMessageRequest request) {
    UUID userId = SecurityUtils.requireUserId();
    requireActiveMember(roomId, userId);
    ChatRoom room = requireRoom(roomId);

    String body = request.getBody().trim();
    ChatMessage message =
        messageRepository.save(
            ChatMessage.builder().roomId(roomId).senderUserId(userId).body(body).build());

    room.setUpdatedAt(Instant.now());
    roomRepository.save(room);

    ChatMessageResponse response = toMessageResponse(message);
    for (ChatRoomMember member : memberRepository.findByRoomId(roomId)) {
      realtimeService.publishMessage(member.getUserId(), response);
    }
    return response;
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
    int memberCount = memberRepository.findByRoomId(room.getId()).size();
    boolean owner = room.getOwnerUserId().equals(viewerUserId);
    return ChatRoomSummaryResponse.builder()
        .id(room.getId())
        .name(room.getName())
        .ownerUserId(room.getOwnerUserId())
        .owner(owner)
        .memberCount(memberCount)
        .createdAt(room.getCreatedAt())
        .updatedAt(room.getUpdatedAt())
        .build();
  }

  private ChatInviteResponse toInviteResponse(ChatRoomInvite invite, String roomName) {
    return ChatInviteResponse.builder()
        .id(invite.getId())
        .roomId(invite.getRoomId())
        .roomName(roomName)
        .inviterUserId(invite.getInviterUserId())
        .inviterLabel(displayLabel(invite.getInviterUserId()))
        .inviteeIdentifier(invite.getInviteeIdentifier())
        .status(invite.getStatus())
        .createdAt(invite.getCreatedAt())
        .build();
  }

  private ChatMessageResponse toMessageResponse(ChatMessage message) {
    return ChatMessageResponse.builder()
        .id(message.getId())
        .roomId(message.getRoomId())
        .senderUserId(message.getSenderUserId())
        .senderLabel(displayLabel(message.getSenderUserId()))
        .body(message.getBody())
        .createdAt(message.getCreatedAt())
        .build();
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
