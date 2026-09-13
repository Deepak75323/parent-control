package com.parentalcontrol.chat.service;

import com.parentalcontrol.chat.domain.ChatRoomMember;
import com.parentalcontrol.chat.dto.ChatPartnerPresenceResponse;
import com.parentalcontrol.chat.repository.ChatRoomMemberRepository;
import com.parentalcontrol.common.constant.MessageConstants;
import com.parentalcontrol.common.exception.BusinessException;
import com.parentalcontrol.common.exception.ErrorCode;
import com.parentalcontrol.common.security.SecurityUtils;
import com.parentalcontrol.user.service.SharePresenceSettingsService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ParentPresenceFanoutService {

  private final ParentPresenceTracker presenceTracker;
  private final ChatRoomMemberRepository memberRepository;
  private final ChatRealtimeService realtimeService;
  private final SharePresenceSettingsService sharePresenceSettingsService;

  public void onSessionConnected(UUID userId, String sessionId) {
    ParentPresenceTracker.Transition transition =
        presenceTracker.onSessionConnected(userId, sessionId);
    if (!transition.onlineChanged()) {
      return;
    }
    if (!sharePresenceSettingsService.isEnabled(userId)) {
      return;
    }
    publishToDirectPartners(userId, true, transition.lastSeenAt(), false);
  }

  public void onSessionDisconnected(UUID userId, String sessionId) {
    ParentPresenceTracker.Transition transition =
        presenceTracker.onSessionDisconnected(userId, sessionId);
    if (!transition.onlineChanged()) {
      return;
    }
    if (!sharePresenceSettingsService.isEnabled(userId)) {
      return;
    }
    publishToDirectPartners(userId, false, transition.lastSeenAt(), false);
  }

  /** Called when the parent toggles share-presence in Settings. */
  public void publishSharePreferenceChanged(UUID userId, boolean shareEnabled) {
    if (!shareEnabled) {
      publishToDirectPartners(userId, false, null, true);
      return;
    }
    ParentPresenceTracker.Snapshot snap = presenceTracker.snapshot(userId);
    publishToDirectPartners(userId, snap.online(), snap.lastSeenAt(), false);
  }

  @Transactional(readOnly = true)
  public ChatPartnerPresenceResponse partnerPresenceForRoom(UUID roomId) {
    UUID viewerId = SecurityUtils.requireUserId();
    List<ChatRoomMember> members = memberRepository.findByRoomId(roomId);
    if (members.stream().noneMatch(m -> m.getUserId().equals(viewerId))) {
      throw new BusinessException(ErrorCode.FORBIDDEN, MessageConstants.CHAT_NOT_A_MEMBER);
    }
    if (members.size() != 2) {
      return ChatPartnerPresenceResponse.builder()
          .userId(null)
          .online(false)
          .lastSeenAt(null)
          .hidden(true)
          .build();
    }
    UUID partnerId =
        members.stream()
            .map(ChatRoomMember::getUserId)
            .filter(id -> !id.equals(viewerId))
            .findFirst()
            .orElse(null);
    if (partnerId == null) {
      return ChatPartnerPresenceResponse.builder().hidden(true).build();
    }
    if (!sharePresenceSettingsService.isEnabled(partnerId)) {
      return ChatPartnerPresenceResponse.builder()
          .userId(partnerId)
          .online(false)
          .lastSeenAt(null)
          .hidden(true)
          .build();
    }
    ParentPresenceTracker.Snapshot snap = presenceTracker.snapshot(partnerId);
    return ChatPartnerPresenceResponse.builder()
        .userId(partnerId)
        .online(snap.online())
        .lastSeenAt(snap.lastSeenAt())
        .hidden(false)
        .build();
  }

  private void publishToDirectPartners(
      UUID subjectUserId, boolean online, Instant lastSeenAt, boolean hidden) {
    List<DirectPartner> partners = findDirectPartners(subjectUserId);
    for (DirectPartner partner : partners) {
      Map<String, Object> event = new HashMap<>();
      event.put("eventType", "PRESENCE_UPDATE");
      event.put("roomId", partner.roomId().toString());
      event.put("userId", subjectUserId.toString());
      event.put("online", !hidden && online);
      event.put("hidden", hidden);
      if (!hidden && lastSeenAt != null) {
        event.put("lastSeenAt", lastSeenAt.toString());
      }
      try {
        realtimeService.publishPresenceUpdate(partner.partnerUserId(), event);
      } catch (Exception e) {
        log.warn(
            "Failed to publish presence subject={} partner={} roomId={}",
            subjectUserId,
            partner.partnerUserId(),
            partner.roomId(),
            e);
      }
    }
  }

  private List<DirectPartner> findDirectPartners(UUID userId) {
    List<ChatRoomMember> myMemberships = memberRepository.findActiveByUserId(userId);
    List<DirectPartner> partners = new ArrayList<>();
    for (ChatRoomMember membership : myMemberships) {
      List<ChatRoomMember> roomMembers = memberRepository.findByRoomId(membership.getRoomId());
      if (roomMembers.size() != 2) {
        continue;
      }
      for (ChatRoomMember other : roomMembers) {
        if (!other.getUserId().equals(userId)) {
          partners.add(new DirectPartner(membership.getRoomId(), other.getUserId()));
        }
      }
    }
    return partners;
  }

  private record DirectPartner(UUID roomId, UUID partnerUserId) {}
}
