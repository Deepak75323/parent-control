package com.parentalcontrol.chat.service;

import com.parentalcontrol.chat.domain.ChatRoomCall;
import com.parentalcontrol.chat.domain.ChatRoomCallParticipant;
import com.parentalcontrol.chat.domain.ChatRoomCallStatus;
import com.parentalcontrol.chat.domain.ChatRoomMember;
import com.parentalcontrol.chat.domain.ChatRoomMemberRole;
import com.parentalcontrol.chat.dto.ChatCallParticipantResponse;
import com.parentalcontrol.chat.dto.ChatCallResponse;
import com.parentalcontrol.chat.dto.ChatCallSignalRequest;
import com.parentalcontrol.chat.repository.ChatRoomCallParticipantRepository;
import com.parentalcontrol.chat.repository.ChatRoomCallRepository;
import com.parentalcontrol.chat.repository.ChatRoomMemberRepository;
import com.parentalcontrol.common.constant.MessageConstants;
import com.parentalcontrol.common.exception.BusinessException;
import com.parentalcontrol.common.exception.ErrorCode;
import com.parentalcontrol.common.security.SecurityUtils;
import com.parentalcontrol.common.util.ChatNicknameGenerator;
import com.parentalcontrol.user.dao.ParentProfileDao;
import com.parentalcontrol.user.dao.UserDao;
import com.parentalcontrol.user.domain.ParentProfile;
import com.parentalcontrol.user.domain.User;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class ChatCallService {

  public static final int MAX_PARTICIPANTS = 4;

  private final ChatRoomCallRepository callRepository;
  private final ChatRoomCallParticipantRepository participantRepository;
  private final ChatRoomMemberRepository memberRepository;
  private final ChatChannelService channelService;
  private final ChatRealtimeService realtimeService;
  private final ParentProfileDao parentProfileDao;
  private final UserDao userDao;

  @Transactional
  public ChatCallResponse startOrGetActive(UUID roomId, UUID channelIdOrNull) {
    UUID userId = SecurityUtils.requireUserId();
    requireActiveMember(roomId, userId);
    UUID channelId = channelService.resolveVoiceChannel(roomId, channelIdOrNull).getId();

    Optional<ChatRoomCall> existing =
        callRepository.findFirstByChannelIdAndStatusOrderByCreatedAtDesc(
            channelId, ChatRoomCallStatus.ACTIVE);
    if (existing.isPresent()) {
      return joinInternal(existing.get(), userId, false);
    }

    ChatRoomCall call =
        callRepository.save(
            ChatRoomCall.builder()
                .roomId(roomId)
                .channelId(channelId)
                .startedByUserId(userId)
                .status(ChatRoomCallStatus.ACTIVE)
                .build());
    participantRepository.save(
        ChatRoomCallParticipant.builder().callId(call.getId()).userId(userId).build());

    ChatCallResponse response = toResponse(call);
    publishToRoom(roomId, callEvent("CALL_STARTED", response, userId, null, null, null));
    return response;
  }

  @Transactional(readOnly = true)
  public ChatCallResponse getActive(UUID roomId, UUID channelIdOrNull) {
    UUID userId = SecurityUtils.requireUserId();
    requireActiveMember(roomId, userId);
    if (channelIdOrNull != null) {
      UUID channelId = channelService.resolveVoiceChannel(roomId, channelIdOrNull).getId();
      return callRepository
          .findFirstByChannelIdAndStatusOrderByCreatedAtDesc(channelId, ChatRoomCallStatus.ACTIVE)
          .map(this::toResponse)
          .orElse(null);
    }
    return callRepository
        .findFirstByRoomIdAndStatusOrderByCreatedAtDesc(roomId, ChatRoomCallStatus.ACTIVE)
        .map(this::toResponse)
        .orElse(null);
  }

  @Transactional
  public ChatCallResponse join(UUID roomId, UUID callId) {
    UUID userId = SecurityUtils.requireUserId();
    requireActiveMember(roomId, userId);
    ChatRoomCall call = requireCallInRoom(roomId, callId);
    return joinInternal(call, userId, true);
  }

  @Transactional
  public void leave(UUID roomId, UUID callId) {
    UUID userId = SecurityUtils.requireUserId();
    requireActiveMember(roomId, userId);
    ChatRoomCall call = requireCallInRoom(roomId, callId);
    if (call.getStatus() != ChatRoomCallStatus.ACTIVE) {
      return;
    }
    ChatRoomCallParticipant participant =
        participantRepository
            .findByCallIdAndUserId(callId, userId)
            .orElseThrow(
                () ->
                    new BusinessException(
                        ErrorCode.FORBIDDEN, MessageConstants.CHAT_CALL_NOT_PARTICIPANT));
    if (participant.getLeftAt() == null) {
      participant.setLeftAt(Instant.now());
      participantRepository.save(participant);
    }

    publishToRoom(
        roomId,
        callEvent("CALL_LEFT", toResponse(call), userId, null, null, null));

    if (participantRepository.countActiveByCallId(callId) == 0) {
      endCall(call, userId, "CALL_ENDED");
    }
  }

  @Transactional
  public void end(UUID roomId, UUID callId) {
    UUID userId = SecurityUtils.requireUserId();
    ChatRoomMember member = requireActiveMember(roomId, userId);
    ChatRoomCall call = requireCallInRoom(roomId, callId);
    if (call.getStatus() != ChatRoomCallStatus.ACTIVE) {
      return;
    }
    boolean allowed =
        call.getStartedByUserId().equals(userId)
            || member.getRole() == ChatRoomMemberRole.OWNER
            || member.getRole() == ChatRoomMemberRole.ADMIN;
    if (!allowed) {
      throw new BusinessException(ErrorCode.FORBIDDEN, MessageConstants.CHAT_CALL_CANNOT_END);
    }
    endCall(call, userId, "CALL_ENDED");
  }

  @Transactional
  public void signal(UUID roomId, UUID callId, ChatCallSignalRequest request) {
    UUID fromUserId = SecurityUtils.requireUserId();
    requireActiveMember(roomId, fromUserId);
    ChatRoomCall call = requireCallInRoom(roomId, callId);
    if (call.getStatus() != ChatRoomCallStatus.ACTIVE) {
      throw new BusinessException(ErrorCode.CONFLICT, MessageConstants.CHAT_CALL_NOT_ACTIVE);
    }
    requireActiveParticipant(callId, fromUserId);
    requireActiveParticipant(callId, request.getToUserId());

    String type = request.getMessageType().trim().toUpperCase(Locale.ROOT);
    if (!type.equals("OFFER") && !type.equals("ANSWER") && !type.equals("ICE")) {
      throw new BusinessException(
          ErrorCode.VALIDATION_ERROR, "messageType must be OFFER, ANSWER, or ICE");
    }

    Map<String, Object> event =
        callEvent(
            "CALL_SIGNAL",
            toResponse(call),
            fromUserId,
            request.getToUserId(),
            type,
            request.getPayload());
    realtimeService.publishCallEvent(request.getToUserId(), event);
  }

  private ChatCallResponse joinInternal(ChatRoomCall call, UUID userId, boolean announceJoin) {
    if (call.getStatus() != ChatRoomCallStatus.ACTIVE) {
      throw new BusinessException(ErrorCode.CONFLICT, MessageConstants.CHAT_CALL_NOT_ACTIVE);
    }
    Optional<ChatRoomCallParticipant> existing =
        participantRepository.findByCallIdAndUserId(call.getId(), userId);
    if (existing.isPresent() && existing.get().getLeftAt() == null) {
      return toResponse(call);
    }
    long active = participantRepository.countActiveByCallId(call.getId());
    if (active >= MAX_PARTICIPANTS) {
      throw new BusinessException(ErrorCode.CONFLICT, MessageConstants.CHAT_CALL_FULL);
    }
    if (existing.isPresent()) {
      ChatRoomCallParticipant p = existing.get();
      p.setLeftAt(null);
      p.setJoinedAt(Instant.now());
      participantRepository.save(p);
    } else {
      participantRepository.save(
          ChatRoomCallParticipant.builder().callId(call.getId()).userId(userId).build());
    }
    ChatCallResponse response = toResponse(call);
    if (announceJoin) {
      publishToRoom(
          call.getRoomId(), callEvent("CALL_JOINED", response, userId, null, null, null));
    }
    return response;
  }

  private void endCall(ChatRoomCall call, UUID actorUserId, String eventType) {
    Instant now = Instant.now();
    call.setStatus(ChatRoomCallStatus.ENDED);
    call.setEndedAt(now);
    callRepository.save(call);
    for (ChatRoomCallParticipant p : participantRepository.findActiveByCallId(call.getId())) {
      p.setLeftAt(now);
      participantRepository.save(p);
    }
    publishToRoom(
        call.getRoomId(),
        callEvent(eventType, toResponse(call), actorUserId, null, null, null));
  }

  private ChatRoomCall requireCallInRoom(UUID roomId, UUID callId) {
    ChatRoomCall call =
        callRepository
            .findById(callId)
            .orElseThrow(
                () ->
                    new BusinessException(
                        ErrorCode.NOT_FOUND, MessageConstants.CHAT_CALL_NOT_FOUND));
    if (!call.getRoomId().equals(roomId)) {
      throw new BusinessException(ErrorCode.NOT_FOUND, MessageConstants.CHAT_CALL_NOT_FOUND);
    }
    return call;
  }

  private void requireActiveParticipant(UUID callId, UUID userId) {
    ChatRoomCallParticipant p =
        participantRepository
            .findByCallIdAndUserId(callId, userId)
            .orElseThrow(
                () ->
                    new BusinessException(
                        ErrorCode.FORBIDDEN, MessageConstants.CHAT_CALL_SIGNAL_TARGET));
    if (p.getLeftAt() != null) {
      throw new BusinessException(
          ErrorCode.FORBIDDEN, MessageConstants.CHAT_CALL_SIGNAL_TARGET);
    }
  }

  private ChatRoomMember requireActiveMember(UUID roomId, UUID userId) {
    return memberRepository
        .findByRoomIdAndUserId(roomId, userId)
        .orElseThrow(
            () ->
                new BusinessException(ErrorCode.FORBIDDEN, MessageConstants.CHAT_NOT_A_MEMBER));
  }

  private ChatCallResponse toResponse(ChatRoomCall call) {
    List<ChatCallParticipantResponse> participants =
        participantRepository.findActiveByCallId(call.getId()).stream()
            .map(
                p ->
                    ChatCallParticipantResponse.builder()
                        .userId(p.getUserId())
                        .label(displayLabel(p.getUserId()))
                        .nickname(chatNicknameForUser(p.getUserId()))
                        .joinedAt(p.getJoinedAt())
                        .build())
            .toList();
    return ChatCallResponse.builder()
        .id(call.getId())
        .roomId(call.getRoomId())
        .channelId(call.getChannelId())
        .startedByUserId(call.getStartedByUserId())
        .status(call.getStatus())
        .createdAt(call.getCreatedAt())
        .participants(participants)
        .build();
  }

  private Map<String, Object> callEvent(
      String eventType,
      ChatCallResponse call,
      UUID userId,
      UUID toUserId,
      String messageType,
      String payload) {
    Map<String, Object> map = new HashMap<>();
    map.put("eventType", eventType);
    map.put("roomId", call.getRoomId().toString());
    if (call.getChannelId() != null) {
      map.put("channelId", call.getChannelId().toString());
    }
    map.put("callId", call.getId().toString());
    map.put("status", call.getStatus().name());
    map.put("startedByUserId", call.getStartedByUserId().toString());
    map.put("participants", call.getParticipants());
    if (userId != null) {
      map.put("userId", userId.toString());
    }
    if (toUserId != null) {
      map.put("toUserId", toUserId.toString());
    }
    if (messageType != null) {
      map.put("messageType", messageType);
    }
    if (payload != null) {
      map.put("payload", payload);
    }
    return map;
  }

  private void publishToRoom(UUID roomId, Map<String, Object> event) {
    for (ChatRoomMember member : memberRepository.findByRoomId(roomId)) {
      realtimeService.publishCallEvent(member.getUserId(), event);
    }
  }

  private String displayLabel(UUID userId) {
    Optional<ParentProfile> profile = parentProfileDao.findByUser_Id(userId);
    if (profile.isPresent()) {
      ParentProfile p = profile.get();
      if (StringUtils.hasText(p.getFamilyHandle())) {
        return "@" + p.getFamilyHandle();
      }
      String name =
          ((p.getFirstName() == null ? "" : p.getFirstName())
                  + " "
                  + (p.getLastName() == null ? "" : p.getLastName()))
              .trim();
      if (StringUtils.hasText(name)) {
        return name;
      }
    }
    return userDao.findById(userId).map(User::getEmail).orElse("Parent");
  }

  private String chatNicknameForUser(UUID userId) {
    return ChatNicknameGenerator.fromEmail(
        userDao.findById(userId).map(User::getEmail).orElse(userId.toString()));
  }
}
