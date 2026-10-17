package com.parentalcontrol.chat.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.parentalcontrol.chat.domain.ChatRoomMember;
import com.parentalcontrol.chat.domain.ChatRoomMemberRole;
import com.parentalcontrol.chat.repository.ChatRoomMemberRepository;
import com.parentalcontrol.user.service.SharePresenceSettingsService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ParentPresenceFanoutServiceTest {

  @Mock private ParentPresenceTracker presenceTracker;
  @Mock private ChatRoomMemberRepository memberRepository;
  @Mock private ChatRealtimeService realtimeService;
  @Mock private SharePresenceSettingsService sharePresenceSettingsService;
  @Mock private org.springframework.messaging.simp.user.SimpUserRegistry simpUserRegistry;

  @InjectMocks private ParentPresenceFanoutService fanout;

  private UUID subject;
  private UUID partner;
  private UUID roomId;

  @BeforeEach
  void setUp() {
    subject = UUID.randomUUID();
    partner = UUID.randomUUID();
    roomId = UUID.randomUUID();
  }

  @Test
  void publishesToDirectPartnerWhenShareEnabled() {
    when(sharePresenceSettingsService.isEnabled(subject)).thenReturn(true);
    when(presenceTracker.onSessionConnected(subject, "s1"))
        .thenReturn(new ParentPresenceTracker.Transition(true, true, null));
    when(memberRepository.findActiveByUserId(subject))
        .thenReturn(List.of(member(roomId, subject)));
    when(memberRepository.findByRoomId(roomId))
        .thenReturn(List.of(member(roomId, subject), member(roomId, partner)));

    fanout.onSessionConnected(subject, "s1");

    ArgumentCaptor<java.util.Map<String, Object>> captor = ArgumentCaptor.forClass(java.util.Map.class);
    verify(realtimeService).publishPresenceUpdate(eq(partner), captor.capture());
    assertEquals("PRESENCE_UPDATE", captor.getValue().get("eventType"));
    assertEquals(true, captor.getValue().get("online"));
    assertEquals(false, captor.getValue().get("hidden"));
  }

  @Test
  void skipsPublishWhenShareDisabled() {
    when(sharePresenceSettingsService.isEnabled(subject)).thenReturn(false);
    when(presenceTracker.onSessionConnected(subject, "s1"))
        .thenReturn(new ParentPresenceTracker.Transition(true, true, null));

    fanout.onSessionConnected(subject, "s1");

    verify(realtimeService, never()).publishPresenceUpdate(any(), anyMap());
  }

  @Test
  void hidePreferencePublishesHidden() {
    when(memberRepository.findActiveByUserId(subject))
        .thenReturn(List.of(member(roomId, subject)));
    when(memberRepository.findByRoomId(roomId))
        .thenReturn(List.of(member(roomId, subject), member(roomId, partner)));

    fanout.publishSharePreferenceChanged(subject, false);

    ArgumentCaptor<java.util.Map<String, Object>> captor = ArgumentCaptor.forClass(java.util.Map.class);
    verify(realtimeService).publishPresenceUpdate(eq(partner), captor.capture());
    assertEquals(true, captor.getValue().get("hidden"));
    assertEquals(false, captor.getValue().get("online"));
  }

  @Test
  void publishesToRoomPartnerInLargerServer() {
    UUID third = UUID.randomUUID();
    when(sharePresenceSettingsService.isEnabled(subject)).thenReturn(true);
    when(presenceTracker.onSessionConnected(subject, "s1"))
        .thenReturn(new ParentPresenceTracker.Transition(true, true, null));
    when(memberRepository.findActiveByUserId(subject))
        .thenReturn(List.of(member(roomId, subject)));
    when(memberRepository.findByRoomId(roomId))
        .thenReturn(
            List.of(member(roomId, subject), member(roomId, partner), member(roomId, third)));

    fanout.onSessionConnected(subject, "s1");

    verify(realtimeService).publishPresenceUpdate(eq(partner), anyMap());
    verify(realtimeService).publishPresenceUpdate(eq(third), anyMap());
  }

  @Test
  void membersPresenceHidesOptedOutPartner() {
    try (var security = org.mockito.Mockito.mockStatic(com.parentalcontrol.common.security.SecurityUtils.class)) {
      security.when(com.parentalcontrol.common.security.SecurityUtils::requireUserId).thenReturn(subject);
      when(memberRepository.findByRoomId(roomId))
          .thenReturn(List.of(member(roomId, subject), member(roomId, partner)));
      when(sharePresenceSettingsService.isEnabled(partner)).thenReturn(false);
      when(presenceTracker.snapshot(subject))
          .thenReturn(new ParentPresenceTracker.Snapshot(true, null));
      when(simpUserRegistry.getUser(subject.toString())).thenReturn(null);

      var list = fanout.membersPresenceForRoom(roomId);

      org.junit.jupiter.api.Assertions.assertEquals(2, list.size());
      var self = list.stream().filter(p -> p.getUserId().equals(subject)).findFirst().orElseThrow();
      var other = list.stream().filter(p -> p.getUserId().equals(partner)).findFirst().orElseThrow();
      org.junit.jupiter.api.Assertions.assertFalse(self.isHidden());
      org.junit.jupiter.api.Assertions.assertTrue(other.isHidden());
      org.junit.jupiter.api.Assertions.assertFalse(other.isOnline());
      org.junit.jupiter.api.Assertions.assertNull(other.getLastSeenAt());
    }
  }

  private static ChatRoomMember member(UUID roomId, UUID userId) {
    return ChatRoomMember.builder()
        .roomId(roomId)
        .userId(userId)
        .role(ChatRoomMemberRole.MEMBER)
        .build();
  }

  private static void assertEquals(Object expected, Object actual) {
    org.junit.jupiter.api.Assertions.assertEquals(expected, actual);
  }
}
