package com.parentalcontrol.chat.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.parentalcontrol.chat.domain.ChatRoomCall;
import com.parentalcontrol.chat.domain.ChatRoomCallParticipant;
import com.parentalcontrol.chat.domain.ChatRoomCallStatus;
import com.parentalcontrol.chat.domain.ChatRoomMember;
import com.parentalcontrol.chat.domain.ChatRoomMemberRole;
import com.parentalcontrol.chat.repository.ChatRoomCallParticipantRepository;
import com.parentalcontrol.chat.repository.ChatRoomCallRepository;
import com.parentalcontrol.chat.repository.ChatRoomMemberRepository;
import com.parentalcontrol.common.constant.MessageConstants;
import com.parentalcontrol.common.exception.BusinessException;
import com.parentalcontrol.common.security.SecurityUtils;
import com.parentalcontrol.user.dao.ParentProfileDao;
import com.parentalcontrol.user.dao.UserDao;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ChatCallServiceTest {

  @Mock private ChatRoomCallRepository callRepository;
  @Mock private ChatRoomCallParticipantRepository participantRepository;
  @Mock private ChatRoomMemberRepository memberRepository;
  @Mock private ChatChannelService channelService;
  @Mock private ChatRealtimeService realtimeService;
  @Mock private ParentProfileDao parentProfileDao;
  @Mock private UserDao userDao;

  private ChatCallService service;
  private MockedStatic<SecurityUtils> security;

  private final UUID roomId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
  private final UUID userId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
  private final UUID callId = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
  private final UUID channelId = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");

  @BeforeEach
  void setUp() {
    service =
        new ChatCallService(
            callRepository,
            participantRepository,
            memberRepository,
            channelService,
            realtimeService,
            parentProfileDao,
            userDao);
    security = Mockito.mockStatic(SecurityUtils.class);
    security.when(SecurityUtils::requireUserId).thenReturn(userId);
  }

  @AfterEach
  void tearDown() {
    security.close();
  }

  @Test
  void startOrGetActive_createsCallWhenNone() {
    when(memberRepository.findByRoomIdAndUserId(roomId, userId))
        .thenReturn(Optional.of(member(userId, ChatRoomMemberRole.MEMBER)));
    com.parentalcontrol.chat.domain.ChatChannel voice =
        com.parentalcontrol.chat.domain.ChatChannel.builder()
            .roomId(roomId)
            .name("Lounge")
            .channelType(com.parentalcontrol.chat.domain.ChatChannelType.VOICE)
            .position(1)
            .build();
    voice.setId(channelId);
    when(channelService.resolveVoiceChannel(roomId, null)).thenReturn(voice);
    when(callRepository.findFirstByChannelIdAndStatusOrderByCreatedAtDesc(
            channelId, ChatRoomCallStatus.ACTIVE))
        .thenReturn(Optional.empty());
    when(callRepository.save(any()))
        .thenAnswer(
            inv -> {
              ChatRoomCall c = inv.getArgument(0);
              c.setId(callId);
              return c;
            });
    when(participantRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    when(participantRepository.findActiveByCallId(callId)).thenReturn(List.of(activeParticipant()));
    when(memberRepository.findByRoomId(roomId))
        .thenReturn(List.of(member(userId, ChatRoomMemberRole.MEMBER)));
    when(parentProfileDao.findByUser_Id(userId)).thenReturn(Optional.empty());
    when(userDao.findById(userId)).thenReturn(Optional.empty());

    var response = service.startOrGetActive(roomId, null);

    assertEquals(callId, response.getId());
    assertEquals(channelId, response.getChannelId());
    assertEquals(ChatRoomCallStatus.ACTIVE, response.getStatus());
    verify(realtimeService).publishCallEvent(eq(userId), any());
  }

  @Test
  void join_rejectsWhenFull() {
    when(memberRepository.findByRoomIdAndUserId(roomId, userId))
        .thenReturn(Optional.of(member(userId, ChatRoomMemberRole.MEMBER)));
    ChatRoomCall call =
        ChatRoomCall.builder()
            .roomId(roomId)
            .startedByUserId(userId)
            .status(ChatRoomCallStatus.ACTIVE)
            .build();
    call.setId(callId);
    when(callRepository.findById(callId)).thenReturn(Optional.of(call));
    when(participantRepository.findByCallIdAndUserId(callId, userId)).thenReturn(Optional.empty());
    when(participantRepository.countActiveByCallId(callId)).thenReturn(4L);

    BusinessException ex =
        assertThrows(BusinessException.class, () -> service.join(roomId, callId));
    assertEquals(MessageConstants.CHAT_CALL_FULL, ex.getMessage());
  }

  @Test
  void end_rejectsPlainMemberWhoDidNotStart() {
    UUID starter = UUID.fromString("eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee");
    when(memberRepository.findByRoomIdAndUserId(roomId, userId))
        .thenReturn(Optional.of(member(userId, ChatRoomMemberRole.MEMBER)));
    ChatRoomCall call =
        ChatRoomCall.builder()
            .roomId(roomId)
            .startedByUserId(starter)
            .status(ChatRoomCallStatus.ACTIVE)
            .build();
    call.setId(callId);
    when(callRepository.findById(callId)).thenReturn(Optional.of(call));

    BusinessException ex =
        assertThrows(BusinessException.class, () -> service.end(roomId, callId));
    assertEquals(MessageConstants.CHAT_CALL_CANNOT_END, ex.getMessage());
  }

  private ChatRoomMember member(UUID id, ChatRoomMemberRole role) {
    return ChatRoomMember.builder().roomId(roomId).userId(id).role(role).build();
  }

  private ChatRoomCallParticipant activeParticipant() {
    return ChatRoomCallParticipant.builder().callId(callId).userId(userId).build();
  }
}
