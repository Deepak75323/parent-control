package com.parentalcontrol.chat.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.parentalcontrol.chat.domain.ChatRoom;
import com.parentalcontrol.chat.domain.ChatRoomMember;
import com.parentalcontrol.chat.domain.ChatRoomMemberRole;
import com.parentalcontrol.chat.dto.ChatRoomSummaryResponse;
import com.parentalcontrol.chat.repository.ChatMessageHideRepository;
import com.parentalcontrol.chat.repository.ChatMessageRepository;
import com.parentalcontrol.chat.repository.ChatRoomInviteRepository;
import com.parentalcontrol.chat.repository.ChatRoomMemberRepository;
import com.parentalcontrol.chat.repository.ChatRoomRepository;
import com.parentalcontrol.common.exception.BusinessException;
import com.parentalcontrol.common.exception.ErrorCode;
import com.parentalcontrol.common.security.SecurityUtils;
import com.parentalcontrol.config.ChatAutoDeleteProperties;
import com.parentalcontrol.user.dao.ParentProfileDao;
import com.parentalcontrol.user.dao.UserDao;
import com.parentalcontrol.user.service.FamilyHandleService;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ChatServiceMembersPanelShareTest {

  @Mock private ChatRoomRepository roomRepository;
  @Mock private ChatRoomMemberRepository memberRepository;
  @Mock private ChatRoomInviteRepository inviteRepository;
  @Mock private ChatMessageRepository messageRepository;
  @Mock private ChatMessageHideRepository messageHideRepository;
  @Mock private ChatRealtimeService realtimeService;
  @Mock private ChatAttachmentStorageService attachmentStorageService;
  @Mock private ChatReadReceiptService readReceiptService;
  @Mock private ChatMessageReactionService reactionService;
  @Mock private ChatAutoDeleteProperties autoDeleteProperties;
  @Mock private FamilyHandleService familyHandleService;
  @Mock private ParentProfileDao parentProfileDao;
  @Mock private UserDao userDao;
  @Mock private ChatChannelService channelService;

  @InjectMocks private ChatService chatService;

  private MockedStatic<SecurityUtils> security;

  private final UUID roomId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
  private final UUID ownerId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
  private final UUID memberId = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");

  @BeforeEach
  void setUp() {
    security = Mockito.mockStatic(SecurityUtils.class);
  }

  @AfterEach
  void tearDown() {
    security.close();
  }

  @Test
  void setMembersPanelShared_ownerCanShare() {
    security.when(SecurityUtils::requireUserId).thenReturn(ownerId);
    ChatRoom room =
        ChatRoom.builder()
            .ownerUserId(ownerId)
            .name("Notes")
            .membersPanelShared(false)
            .build();
    room.setId(roomId);
    when(roomRepository.findById(roomId)).thenReturn(Optional.of(room));
    when(memberRepository.findByRoomIdAndUserId(roomId, ownerId))
        .thenReturn(
            Optional.of(
                ChatRoomMember.builder()
                    .roomId(roomId)
                    .userId(ownerId)
                    .role(ChatRoomMemberRole.OWNER)
                    .build()));
    when(memberRepository.findByRoomId(roomId))
        .thenReturn(
            List.of(
                ChatRoomMember.builder().roomId(roomId).userId(ownerId).role(ChatRoomMemberRole.OWNER).build(),
                ChatRoomMember.builder()
                    .roomId(roomId)
                    .userId(memberId)
                    .role(ChatRoomMemberRole.MEMBER)
                    .build()));
    when(channelService.channelsByRoomIds(List.of(roomId))).thenReturn(Map.of(roomId, List.of()));
    when(readReceiptService.countUnread(any(), any(), any(), any())).thenReturn(0);
    when(roomRepository.save(any(ChatRoom.class))).thenAnswer(inv -> inv.getArgument(0));

    ChatRoomSummaryResponse response = chatService.setMembersPanelShared(roomId, true);

    assertTrue(response.isMembersPanelShared());
    verify(realtimeService).publishRoomListBump(ownerId);
    verify(realtimeService).publishRoomListBump(memberId);
  }

  @Test
  void setMembersPanelShared_nonOwnerForbidden() {
    security.when(SecurityUtils::requireUserId).thenReturn(memberId);
    ChatRoom room =
        ChatRoom.builder()
            .ownerUserId(ownerId)
            .name("Notes")
            .membersPanelShared(false)
            .build();
    room.setId(roomId);
    when(roomRepository.findById(roomId)).thenReturn(Optional.of(room));

    BusinessException ex =
        assertThrows(BusinessException.class, () -> chatService.setMembersPanelShared(roomId, true));
    assertEquals(ErrorCode.FORBIDDEN, ex.getErrorCode());
  }
}
