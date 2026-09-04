package com.parentalcontrol.chat.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.parentalcontrol.chat.domain.ChatRoom;
import com.parentalcontrol.chat.domain.ChatRoomMember;
import com.parentalcontrol.chat.domain.ChatRoomMemberRole;
import com.parentalcontrol.chat.repository.ChatMessageHideRepository;
import com.parentalcontrol.chat.repository.ChatMessageRepository;
import com.parentalcontrol.chat.repository.ChatRoomInviteRepository;
import com.parentalcontrol.chat.repository.ChatRoomMemberRepository;
import com.parentalcontrol.chat.repository.ChatRoomRepository;
import com.parentalcontrol.common.exception.BusinessException;
import com.parentalcontrol.common.security.SecurityUtils;
import com.parentalcontrol.config.ChatAutoDeleteProperties;
import com.parentalcontrol.user.dao.ParentProfileDao;
import com.parentalcontrol.user.dao.UserDao;
import com.parentalcontrol.user.service.FamilyHandleService;
import java.util.List;
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
class ChatServiceTypingTest {

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

  @InjectMocks private ChatService chatService;

  private MockedStatic<SecurityUtils> security;

  private final UUID roomId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
  private final UUID typerId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
  private final UUID otherId = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");

  @BeforeEach
  void setUp() {
    security = Mockito.mockStatic(SecurityUtils.class);
    security.when(SecurityUtils::requireUserId).thenReturn(typerId);
  }

  @AfterEach
  void tearDown() {
    security.close();
  }

  @Test
  void notifyTyping_fansOutToOtherMembersOnly() {
    when(memberRepository.findByRoomIdAndUserId(roomId, typerId))
        .thenReturn(Optional.of(member(typerId)));
    when(roomRepository.findById(roomId)).thenReturn(Optional.of(room()));
    when(memberRepository.findByRoomId(roomId))
        .thenReturn(List.of(member(typerId), member(otherId)));
    when(userDao.findById(typerId)).thenReturn(Optional.empty());
    when(parentProfileDao.findByUser_Id(typerId)).thenReturn(Optional.empty());

    chatService.notifyTyping(roomId, true);

    verify(realtimeService)
        .publishTyping(eq(otherId), eq(roomId), eq(typerId), any(), any(), eq(true));
    verify(realtimeService, never())
        .publishTyping(eq(typerId), any(), any(), any(), any(), anyBoolean());
  }

  @Test
  void notifyTyping_rejectsNonMembers() {
    when(memberRepository.findByRoomIdAndUserId(roomId, typerId)).thenReturn(Optional.empty());

    assertThrows(BusinessException.class, () -> chatService.notifyTyping(roomId, true));
    verify(realtimeService, never())
        .publishTyping(any(), any(), any(), any(), any(), anyBoolean());
  }

  private ChatRoom room() {
    ChatRoom room = ChatRoom.builder().ownerUserId(typerId).name("notes").build();
    room.setId(roomId);
    return room;
  }

  private ChatRoomMember member(UUID userId) {
    return ChatRoomMember.builder()
        .roomId(roomId)
        .userId(userId)
        .role(userId.equals(typerId) ? ChatRoomMemberRole.OWNER : ChatRoomMemberRole.MEMBER)
        .build();
  }
}
