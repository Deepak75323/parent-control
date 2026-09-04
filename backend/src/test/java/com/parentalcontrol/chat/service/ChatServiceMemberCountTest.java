package com.parentalcontrol.chat.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.parentalcontrol.chat.domain.ChatRoom;
import com.parentalcontrol.chat.domain.ChatRoomInvite;
import com.parentalcontrol.chat.domain.ChatRoomInviteStatus;
import com.parentalcontrol.chat.domain.ChatRoomMember;
import com.parentalcontrol.chat.domain.ChatRoomMemberRole;
import com.parentalcontrol.chat.dto.ChatRoomSummaryResponse;
import com.parentalcontrol.chat.dto.RespondChatInviteRequest;
import com.parentalcontrol.chat.repository.ChatMessageHideRepository;
import com.parentalcontrol.chat.repository.ChatMessageRepository;
import com.parentalcontrol.chat.repository.ChatRoomInviteRepository;
import com.parentalcontrol.chat.repository.ChatRoomMemberRepository;
import com.parentalcontrol.chat.repository.ChatRoomRepository;
import com.parentalcontrol.common.security.SecurityUtils;
import com.parentalcontrol.config.ChatAutoDeleteProperties;
import com.parentalcontrol.user.dao.ParentProfileDao;
import com.parentalcontrol.user.dao.UserDao;
import com.parentalcontrol.user.service.FamilyHandleService;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ChatServiceMemberCountTest {

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
  private final UUID ownerId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
  private final UUID leftMemberId = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
  private final UUID inviteId = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");

  @BeforeEach
  void setUp() {
    security = Mockito.mockStatic(SecurityUtils.class);
  }

  @AfterEach
  void tearDown() {
    security.close();
  }

  @Test
  void listRooms_memberCountExcludesSoftDeletedMembers() {
    security.when(SecurityUtils::requireUserId).thenReturn(ownerId);
    ChatRoom room = room(ownerId);
    when(roomRepository.findAllForMember(ownerId)).thenReturn(List.of(room));
    // Repository contract: findByRoomId returns active members only.
    when(memberRepository.findByRoomId(roomId)).thenReturn(List.of(activeMember(ownerId)));
    when(memberRepository.findByRoomIdAndUserId(roomId, ownerId))
        .thenReturn(Optional.of(activeMember(ownerId)));
    when(readReceiptService.countUnread(any(), any(), any(), any())).thenReturn(0);

    List<ChatRoomSummaryResponse> rooms = chatService.listRooms();

    assertEquals(1, rooms.size());
    assertEquals(1, rooms.get(0).getMemberCount());
  }

  @Test
  void respondToInvite_revivesSoftDeletedMembership() {
    security.when(SecurityUtils::requireUserId).thenReturn(leftMemberId);
    ChatRoomInvite invite =
        ChatRoomInvite.builder()
            .roomId(roomId)
            .inviterUserId(ownerId)
            .inviteeUserId(leftMemberId)
            .inviteeIdentifier("left@example.com")
            .status(ChatRoomInviteStatus.PENDING)
            .build();
    invite.setId(inviteId);

    ChatRoomMember left =
        ChatRoomMember.builder()
            .roomId(roomId)
            .userId(leftMemberId)
            .role(ChatRoomMemberRole.MEMBER)
            .joinedAt(Instant.parse("2026-09-01T00:00:00Z"))
            .build();
    left.setId(UUID.fromString("eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee"));
    left.setDeletedAt(Instant.parse("2026-09-04T07:42:03Z"));

    when(inviteRepository.findById(inviteId)).thenReturn(Optional.of(invite));
    when(roomRepository.findById(roomId)).thenReturn(Optional.of(room(ownerId)));
    when(memberRepository.findAnyByRoomIdAndUserId(roomId, leftMemberId))
        .thenReturn(Optional.of(left));
    when(memberRepository.findByRoomId(roomId)).thenReturn(List.of(activeMember(ownerId), left));
    when(inviteRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    when(parentProfileDao.findByUser_Id(any())).thenReturn(Optional.empty());
    when(userDao.findById(any())).thenReturn(Optional.empty());

    RespondChatInviteRequest request = new RespondChatInviteRequest();
    request.setAccept(true);
    chatService.respondToInvite(inviteId, request);

    ArgumentCaptor<ChatRoomMember> saved = ArgumentCaptor.forClass(ChatRoomMember.class);
    verify(memberRepository).save(saved.capture());
    assertNull(saved.getValue().getDeletedAt());
    assertEquals(ChatRoomMemberRole.MEMBER, saved.getValue().getRole());
  }

  private ChatRoom room(UUID owner) {
    ChatRoom room = ChatRoom.builder().ownerUserId(owner).name("Notes").build();
    room.setId(roomId);
    return room;
  }

  private ChatRoomMember activeMember(UUID userId) {
    ChatRoomMember member =
        ChatRoomMember.builder()
            .roomId(roomId)
            .userId(userId)
            .role(
                userId.equals(ownerId) ? ChatRoomMemberRole.OWNER : ChatRoomMemberRole.MEMBER)
            .joinedAt(Instant.now())
            .build();
    member.setId(UUID.randomUUID());
    return member;
  }
}
