package com.parentalcontrol.friend.service;

import com.parentalcontrol.chat.service.ChatService;
import com.parentalcontrol.common.constant.MessageConstants;
import com.parentalcontrol.common.domain.Role;
import com.parentalcontrol.common.exception.BusinessException;
import com.parentalcontrol.common.exception.ErrorCode;
import com.parentalcontrol.common.security.SecurityUtils;
import com.parentalcontrol.common.util.ChatNicknameGenerator;
import com.parentalcontrol.friend.domain.ParentFriendship;
import com.parentalcontrol.friend.domain.ParentFriendshipStatus;
import com.parentalcontrol.friend.dto.CreateFriendRequest;
import com.parentalcontrol.friend.dto.FriendRequestResponse;
import com.parentalcontrol.friend.dto.FriendSummaryResponse;
import com.parentalcontrol.friend.dto.RespondFriendRequest;
import com.parentalcontrol.friend.repository.ParentFriendshipRepository;
import com.parentalcontrol.messaging.ClusterStompMessagingService;
import com.parentalcontrol.user.dao.ParentProfileDao;
import com.parentalcontrol.user.dao.UserDao;
import com.parentalcontrol.user.domain.ParentProfile;
import com.parentalcontrol.user.domain.User;
import com.parentalcontrol.user.service.FamilyHandleService;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ParentFriendService {

  private static final Pattern EMAIL_PATTERN =
      Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

  private final ParentFriendshipRepository friendshipRepository;
  private final ChatService chatService;
  private final FamilyHandleService familyHandleService;
  private final ParentProfileDao parentProfileDao;
  private final UserDao userDao;
  private final ClusterStompMessagingService messagingTemplate;

  @Transactional
  public FriendRequestResponse sendRequest(CreateFriendRequest request) {
    UUID requesterId = SecurityUtils.requireUserId();
    String identifier = request.getIdentifier().trim();
    User addressee = resolveParentUser(identifier);
    if (addressee.getId().equals(requesterId)) {
      throw new BusinessException(
          ErrorCode.VALIDATION_ERROR, MessageConstants.FRIEND_CANNOT_ADD_YOURSELF);
    }

    friendshipRepository
        .findActiveBetween(
            requesterId,
            addressee.getId(),
            List.of(ParentFriendshipStatus.PENDING, ParentFriendshipStatus.ACCEPTED))
        .ifPresent(
            existing -> {
              if (existing.getStatus() == ParentFriendshipStatus.ACCEPTED) {
                throw new BusinessException(
                    ErrorCode.CONFLICT, MessageConstants.FRIEND_ALREADY_FRIENDS);
              }
              throw new BusinessException(
                  ErrorCode.CONFLICT, MessageConstants.FRIEND_REQUEST_ALREADY_PENDING);
            });

    ParentFriendship friendship =
        friendshipRepository.save(
            ParentFriendship.builder()
                .requesterUserId(requesterId)
                .addresseeUserId(addressee.getId())
                .inviteeIdentifier(identifier)
                .status(ParentFriendshipStatus.PENDING)
                .build());

    FriendRequestResponse response = toRequestResponse(friendship, requesterId);
    publishFriendEvent(addressee.getId(), "FRIEND_REQUEST", friendship.getId());
    return response;
  }

  @Transactional(readOnly = true)
  public List<FriendRequestResponse> listIncomingPending() {
    UUID userId = SecurityUtils.requireUserId();
    return friendshipRepository
        .findByAddresseeUserIdAndStatusOrderByCreatedAtDesc(
            userId, ParentFriendshipStatus.PENDING)
        .stream()
        .map(f -> toRequestResponse(f, userId))
        .toList();
  }

  @Transactional(readOnly = true)
  public List<FriendRequestResponse> listOutgoingPending() {
    UUID userId = SecurityUtils.requireUserId();
    return friendshipRepository
        .findByRequesterUserIdAndStatusOrderByCreatedAtDesc(
            userId, ParentFriendshipStatus.PENDING)
        .stream()
        .map(f -> toRequestResponse(f, userId))
        .toList();
  }

  @Transactional
  public FriendRequestResponse respond(UUID requestId, RespondFriendRequest request) {
    UUID userId = SecurityUtils.requireUserId();
    ParentFriendship friendship =
        friendshipRepository
            .findById(requestId)
            .orElseThrow(
                () ->
                    new BusinessException(
                        ErrorCode.NOT_FOUND, MessageConstants.FRIEND_REQUEST_NOT_FOUND));
    if (!friendship.getAddresseeUserId().equals(userId)) {
      throw new BusinessException(ErrorCode.FORBIDDEN, MessageConstants.FRIEND_REQUEST_NOT_FOUND);
    }
    if (friendship.getStatus() != ParentFriendshipStatus.PENDING) {
      throw new BusinessException(
          ErrorCode.CONFLICT, MessageConstants.FRIEND_REQUEST_ALREADY_RESPONDED);
    }

    friendship.setRespondedAt(Instant.now());
    if (Boolean.TRUE.equals(request.getAccept())) {
      friendship.setStatus(ParentFriendshipStatus.ACCEPTED);
      String roomName = chatNicknameForUser(friendship.getRequesterUserId());
      UUID dmRoomId =
          chatService.ensureDirectRoomBetween(
              friendship.getRequesterUserId(), friendship.getAddresseeUserId(), roomName);
      friendship.setDmRoomId(dmRoomId);
    } else {
      friendship.setStatus(ParentFriendshipStatus.REJECTED);
    }
    friendshipRepository.save(friendship);

    FriendRequestResponse response = toRequestResponse(friendship, userId);
    publishFriendEvent(
        friendship.getRequesterUserId(),
        Boolean.TRUE.equals(request.getAccept()) ? "FRIEND_ACCEPTED" : "FRIEND_REJECTED",
        friendship.getId());
    return response;
  }

  @Transactional(readOnly = true)
  public List<FriendSummaryResponse> listFriends() {
    UUID userId = SecurityUtils.requireUserId();
    return friendshipRepository
        .findAcceptedForUser(userId, ParentFriendshipStatus.ACCEPTED)
        .stream()
        .map(
            f -> {
              UUID friendId =
                  f.getRequesterUserId().equals(userId)
                      ? f.getAddresseeUserId()
                      : f.getRequesterUserId();
              return FriendSummaryResponse.builder()
                  .friendshipId(f.getId())
                  .userId(friendId)
                  .label(displayLabel(friendId))
                  .nickname(chatNicknameForUser(friendId))
                  .dmRoomId(f.getDmRoomId())
                  .build();
            })
        .toList();
  }

  /**
   * Discord-style: open (find or create) the DM for an accepted friend and return the room id.
   * Backfills {@code dmRoomId} when accept previously skipped room creation.
   */
  @Transactional
  public FriendSummaryResponse openDm(UUID friendUserId) {
    UUID userId = SecurityUtils.requireUserId();
    if (friendUserId == null || friendUserId.equals(userId)) {
      throw new BusinessException(
          ErrorCode.VALIDATION_ERROR, MessageConstants.FRIEND_CANNOT_ADD_YOURSELF);
    }
    ParentFriendship friendship =
        friendshipRepository
            .findActiveBetween(
                userId, friendUserId, List.of(ParentFriendshipStatus.ACCEPTED))
            .orElseThrow(
                () -> new BusinessException(ErrorCode.NOT_FOUND, MessageConstants.FRIEND_NOT_FOUND));

    UUID owner = friendship.getRequesterUserId();
    UUID peer = friendship.getAddresseeUserId();
    String roomName = chatNicknameForUser(friendUserId);
    UUID dmRoomId = chatService.ensureDirectRoomBetween(owner, peer, roomName);
    if (friendship.getDmRoomId() == null || !friendship.getDmRoomId().equals(dmRoomId)) {
      friendship.setDmRoomId(dmRoomId);
      friendshipRepository.save(friendship);
    }

    return FriendSummaryResponse.builder()
        .friendshipId(friendship.getId())
        .userId(friendUserId)
        .label(displayLabel(friendUserId))
        .nickname(chatNicknameForUser(friendUserId))
        .dmRoomId(dmRoomId)
        .build();
  }

  @Transactional
  public void removeFriend(UUID friendUserId) {
    UUID userId = SecurityUtils.requireUserId();
    ParentFriendship friendship =
        friendshipRepository
            .findActiveBetween(
                userId, friendUserId, List.of(ParentFriendshipStatus.ACCEPTED))
            .orElseThrow(
                () -> new BusinessException(ErrorCode.NOT_FOUND, MessageConstants.FRIEND_NOT_FOUND));
    friendship.setStatus(ParentFriendshipStatus.REMOVED);
    friendship.setDeletedAt(Instant.now());
    friendshipRepository.save(friendship);
    publishFriendEvent(friendUserId, "FRIEND_REMOVED", friendship.getId());
  }

  private FriendRequestResponse toRequestResponse(ParentFriendship friendship, UUID viewerId) {
    UUID counterpartId =
        friendship.getRequesterUserId().equals(viewerId)
            ? friendship.getAddresseeUserId()
            : friendship.getRequesterUserId();
    return FriendRequestResponse.builder()
        .id(friendship.getId())
        .requesterUserId(friendship.getRequesterUserId())
        .addresseeUserId(friendship.getAddresseeUserId())
        .status(friendship.getStatus().name())
        .identifier(friendship.getInviteeIdentifier())
        .counterpartLabel(displayLabel(counterpartId))
        .counterpartNickname(chatNicknameForUser(counterpartId))
        .dmRoomId(friendship.getDmRoomId())
        .createdAt(friendship.getCreatedAt())
        .respondedAt(friendship.getRespondedAt())
        .build();
  }

  private void publishFriendEvent(UUID userId, String eventType, UUID friendshipId) {
    messagingTemplate.convertAndSendToUser(
        userId.toString(),
        "/queue/friend-requests",
        Map.of("eventType", eventType, "friendshipId", friendshipId.toString()));
  }

  private User resolveParentUser(String identifier) {
    if (EMAIL_PATTERN.matcher(identifier).matches()) {
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

  private static void requireParentRole(User user) {
    if (user.getRole() != Role.ROLE_PARENT) {
      throw new BusinessException(ErrorCode.NOT_FOUND, MessageConstants.PARENT_ACCOUNT_NOT_FOUND);
    }
  }

  private String chatNicknameForUser(UUID userId) {
    return ChatNicknameGenerator.fromEmail(
        userDao.findById(userId).map(User::getEmail).orElse(""));
  }

  private String displayLabel(UUID userId) {
    return parentProfileDao
        .findByUser_Id(userId)
        .map(
            profile -> {
              if (familyHandleService.isConfigured(profile)) {
                return "@" + profile.getFamilyHandle();
              }
              return userDao.findById(userId).map(User::getEmail).orElse("Parent");
            })
        .orElse("Parent");
  }
}
