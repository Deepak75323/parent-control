package com.parentalcontrol.friend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.parentalcontrol.chat.service.ChatService;
import com.parentalcontrol.common.domain.Role;
import com.parentalcontrol.common.exception.BusinessException;
import com.parentalcontrol.common.security.SecurityUtils;
import com.parentalcontrol.friend.domain.ParentFriendship;
import com.parentalcontrol.friend.domain.ParentFriendshipStatus;
import com.parentalcontrol.friend.dto.CreateFriendRequest;
import com.parentalcontrol.friend.dto.RespondFriendRequest;
import com.parentalcontrol.friend.repository.ParentFriendshipRepository;
import com.parentalcontrol.messaging.ClusterStompMessagingService;
import com.parentalcontrol.user.dao.ParentProfileDao;
import com.parentalcontrol.user.dao.UserDao;
import com.parentalcontrol.user.domain.User;
import com.parentalcontrol.user.service.FamilyHandleService;
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
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ParentFriendServiceTest {

  @Mock private ParentFriendshipRepository friendshipRepository;
  @Mock private ChatService chatService;
  @Mock private FamilyHandleService familyHandleService;
  @Mock private ParentProfileDao parentProfileDao;
  @Mock private UserDao userDao;
  @Mock private ClusterStompMessagingService messagingTemplate;

  @InjectMocks private ParentFriendService service;

  private MockedStatic<SecurityUtils> security;
  private UUID me;
  private UUID other;

  @BeforeEach
  void setUp() {
    me = UUID.randomUUID();
    other = UUID.randomUUID();
    security = org.mockito.Mockito.mockStatic(SecurityUtils.class);
    security.when(SecurityUtils::requireUserId).thenReturn(me);
  }

  @AfterEach
  void tearDown() {
    security.close();
  }

  @Test
  void sendRequest_persistsPending() {
    User otherUser = User.builder().email("b@example.com").role(Role.ROLE_PARENT).build();
    otherUser.setId(other);
    when(userDao.findByEmailIgnoreCase("b@example.com")).thenReturn(Optional.of(otherUser));
    when(friendshipRepository.findActiveBetween(eq(me), eq(other), anyList()))
        .thenReturn(Optional.empty());
    when(friendshipRepository.save(any()))
        .thenAnswer(
            inv -> {
              ParentFriendship f = inv.getArgument(0);
              f.setId(UUID.randomUUID());
              return f;
            });
    when(userDao.findById(any())).thenReturn(Optional.of(otherUser));
    when(parentProfileDao.findByUser_Id(any())).thenReturn(Optional.empty());

    CreateFriendRequest req = new CreateFriendRequest();
    req.setIdentifier("b@example.com");
    var response = service.sendRequest(req);

    assertEquals("PENDING", response.getStatus());
    ArgumentCaptor<ParentFriendship> captor = ArgumentCaptor.forClass(ParentFriendship.class);
    verify(friendshipRepository).save(captor.capture());
    assertEquals(ParentFriendshipStatus.PENDING, captor.getValue().getStatus());
  }

  @Test
  void sendRequest_rejectsSelf() {
    User self = User.builder().email("a@example.com").role(Role.ROLE_PARENT).build();
    self.setId(me);
    when(userDao.findByEmailIgnoreCase("a@example.com")).thenReturn(Optional.of(self));
    CreateFriendRequest req = new CreateFriendRequest();
    req.setIdentifier("a@example.com");
    assertThrows(BusinessException.class, () -> service.sendRequest(req));
  }

  @Test
  void accept_createsDmRoom() {
    UUID friendshipId = UUID.randomUUID();
    ParentFriendship pending =
        ParentFriendship.builder()
            .requesterUserId(other)
            .addresseeUserId(me)
            .inviteeIdentifier("@other")
            .status(ParentFriendshipStatus.PENDING)
            .build();
    pending.setId(friendshipId);
    when(friendshipRepository.findById(friendshipId)).thenReturn(Optional.of(pending));
    when(friendshipRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    UUID roomId = UUID.randomUUID();
    when(chatService.ensureDirectRoomBetween(eq(other), eq(me), any())).thenReturn(roomId);
    User otherUser = User.builder().email("o@example.com").role(Role.ROLE_PARENT).build();
    otherUser.setId(other);
    when(userDao.findById(any())).thenReturn(Optional.of(otherUser));
    when(parentProfileDao.findByUser_Id(any())).thenReturn(Optional.empty());

    RespondFriendRequest respond = new RespondFriendRequest();
    respond.setAccept(true);
    var response = service.respond(friendshipId, respond);

    assertEquals("ACCEPTED", response.getStatus());
    assertEquals(roomId, response.getDmRoomId());
    verify(chatService).ensureDirectRoomBetween(eq(other), eq(me), any());
  }

  @Test
  void openDm_ensuresRoomAndBackfillsMissingDmRoomId() {
    ParentFriendship accepted =
        ParentFriendship.builder()
            .requesterUserId(me)
            .addresseeUserId(other)
            .inviteeIdentifier("@other")
            .status(ParentFriendshipStatus.ACCEPTED)
            .dmRoomId(null)
            .build();
    accepted.setId(UUID.randomUUID());
    when(friendshipRepository.findActiveBetween(
            eq(me), eq(other), eq(List.of(ParentFriendshipStatus.ACCEPTED))))
        .thenReturn(Optional.of(accepted));
    UUID roomId = UUID.randomUUID();
    when(chatService.ensureDirectRoomBetween(eq(me), eq(other), any())).thenReturn(roomId);
    when(friendshipRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    User otherUser = User.builder().email("o@example.com").role(Role.ROLE_PARENT).build();
    otherUser.setId(other);
    when(userDao.findById(any())).thenReturn(Optional.of(otherUser));
    when(parentProfileDao.findByUser_Id(any())).thenReturn(Optional.empty());

    var summary = service.openDm(other);

    assertEquals(roomId, summary.getDmRoomId());
    assertEquals(other, summary.getUserId());
    verify(chatService).ensureDirectRoomBetween(eq(me), eq(other), any());
  }
}
