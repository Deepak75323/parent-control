package com.parentalcontrol.chat.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.ArgumentMatchers.any;
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
import com.parentalcontrol.config.ChatAutoDeleteProperties;
import com.parentalcontrol.user.dao.ParentProfileDao;
import com.parentalcontrol.user.dao.UserDao;
import com.parentalcontrol.user.service.FamilyHandleService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ChatServiceDirectRoomTest {

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

  @Test
  void ensureDirectRoomBetween_skipsOrdinaryTwoPersonServer() {
    UUID userA = UUID.randomUUID();
    UUID userB = UUID.randomUUID();
    ChatRoom notes =
        ChatRoom.builder().ownerUserId(userA).name("Notes").directMessage(false).build();
    notes.setId(UUID.randomUUID());
    when(roomRepository.findAllForMember(userA)).thenReturn(List.of(notes));
    when(roomRepository.save(any(ChatRoom.class)))
        .thenAnswer(
            inv -> {
              ChatRoom room = inv.getArgument(0);
              room.setId(UUID.randomUUID());
              return room;
            });
    when(memberRepository.save(any(ChatRoomMember.class))).thenAnswer(inv -> inv.getArgument(0));

    UUID dmId = chatService.ensureDirectRoomBetween(userA, userB, "Golden Lily");

    assertNotEquals(notes.getId(), dmId);
    ArgumentCaptor<ChatRoom> captor = ArgumentCaptor.forClass(ChatRoom.class);
    verify(roomRepository).save(captor.capture());
    assertEquals(true, captor.getValue().isDirectMessage());
    assertEquals("Golden Lily", captor.getValue().getName());
    verify(channelService).ensureDefaultChannels(dmId);
  }

  @Test
  void ensureDirectRoomBetween_reusesExistingDirectRoom() {
    UUID userA = UUID.randomUUID();
    UUID userB = UUID.randomUUID();
    ChatRoom dm =
        ChatRoom.builder().ownerUserId(userA).name("Golden Lily").directMessage(true).build();
    dm.setId(UUID.randomUUID());
    when(roomRepository.findAllForMember(userA)).thenReturn(List.of(dm));
    when(memberRepository.findByRoomId(dm.getId()))
        .thenReturn(
            List.of(
                ChatRoomMember.builder()
                    .roomId(dm.getId())
                    .userId(userA)
                    .role(ChatRoomMemberRole.OWNER)
                    .joinedAt(Instant.now())
                    .build(),
                ChatRoomMember.builder()
                    .roomId(dm.getId())
                    .userId(userB)
                    .role(ChatRoomMemberRole.MEMBER)
                    .joinedAt(Instant.now())
                    .build()));

    UUID dmId = chatService.ensureDirectRoomBetween(userA, userB, "Golden Lily");

    assertEquals(dm.getId(), dmId);
    verify(roomRepository, never()).save(any());
  }
}
