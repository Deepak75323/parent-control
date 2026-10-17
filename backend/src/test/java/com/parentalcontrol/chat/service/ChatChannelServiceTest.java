package com.parentalcontrol.chat.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.parentalcontrol.chat.domain.ChatChannel;
import com.parentalcontrol.chat.domain.ChatChannelType;
import com.parentalcontrol.chat.domain.ChatRoomMember;
import com.parentalcontrol.chat.domain.ChatRoomMemberRole;
import com.parentalcontrol.chat.dto.CreateChatChannelRequest;
import com.parentalcontrol.chat.repository.ChatChannelRepository;
import com.parentalcontrol.chat.repository.ChatRoomMemberRepository;
import com.parentalcontrol.common.constant.MessageConstants;
import com.parentalcontrol.common.exception.BusinessException;
import com.parentalcontrol.common.security.SecurityUtils;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ChatChannelServiceTest {

  @Mock private ChatChannelRepository channelRepository;
  @Mock private ChatRoomMemberRepository memberRepository;

  private ChatChannelService service;
  private MockedStatic<SecurityUtils> security;

  private final UUID roomId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
  private final UUID userId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

  @BeforeEach
  void setUp() {
    service = new ChatChannelService(channelRepository, memberRepository);
    security = Mockito.mockStatic(SecurityUtils.class);
    security.when(SecurityUtils::requireUserId).thenReturn(userId);
  }

  @AfterEach
  void tearDown() {
    security.close();
  }

  @Test
  void createChannel_rejectsMembers() {
    when(memberRepository.findByRoomIdAndUserId(roomId, userId))
        .thenReturn(Optional.of(member(ChatRoomMemberRole.MEMBER)));
    CreateChatChannelRequest req = new CreateChatChannelRequest();
    req.setName("school");
    req.setChannelType(ChatChannelType.TEXT);

    BusinessException ex =
        assertThrows(BusinessException.class, () -> service.createChannel(roomId, req));
    assertEquals(MessageConstants.CHAT_CHANNEL_CREATE_FORBIDDEN, ex.getMessage());
  }

  @Test
  void createChannel_createsForAdmin() {
    when(memberRepository.findByRoomIdAndUserId(roomId, userId))
        .thenReturn(Optional.of(member(ChatRoomMemberRole.ADMIN)));
    when(channelRepository.countActiveByRoomId(roomId)).thenReturn(2L);
    when(channelRepository.existsActiveNameIgnoreCase(roomId, "school")).thenReturn(false);
    when(channelRepository.findActiveByRoomId(roomId)).thenReturn(List.of());
    when(channelRepository.save(any()))
        .thenAnswer(
            inv -> {
              ChatChannel c = inv.getArgument(0);
              c.setId(UUID.randomUUID());
              return c;
            });

    CreateChatChannelRequest req = new CreateChatChannelRequest();
    req.setName("school");
    req.setChannelType(ChatChannelType.TEXT);

    var response = service.createChannel(roomId, req);
    assertEquals("school", response.getName());
    assertEquals(ChatChannelType.TEXT, response.getChannelType());
  }

  @Test
  void resolveTextChannel_rejectsVoice() {
    UUID channelId = UUID.randomUUID();
    when(channelRepository.findActiveByIdAndRoomId(channelId, roomId))
        .thenReturn(
            Optional.of(
                ChatChannel.builder()
                    .roomId(roomId)
                    .name("Lounge")
                    .channelType(ChatChannelType.VOICE)
                    .position(1)
                    .build()));

    BusinessException ex =
        assertThrows(
            BusinessException.class, () -> service.requireTextChannel(roomId, channelId));
    assertEquals(MessageConstants.CHAT_CHANNEL_TEXT_REQUIRED, ex.getMessage());
  }

  private ChatRoomMember member(ChatRoomMemberRole role) {
    return ChatRoomMember.builder().roomId(roomId).userId(userId).role(role).build();
  }
}
