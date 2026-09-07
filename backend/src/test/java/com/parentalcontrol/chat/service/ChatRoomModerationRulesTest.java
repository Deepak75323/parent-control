package com.parentalcontrol.chat.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.parentalcontrol.chat.domain.ChatRoomMember;
import com.parentalcontrol.chat.domain.ChatRoomMemberRole;
import com.parentalcontrol.common.exception.BusinessException;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ChatRoomModerationRulesTest {

  private final UUID ownerId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
  private final UUID adminId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
  private final UUID memberId = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
  private final UUID otherMemberId = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");

  @Test
  void ownerCanRemoveMemberAndAdmin() {
    assertDoesNotThrow(
        () -> ChatRoomModerationRules.requireCanRemove(owner(), member()));
    assertDoesNotThrow(
        () -> ChatRoomModerationRules.requireCanRemove(owner(), admin()));
  }

  @Test
  void adminCanRemoveMemberOnly() {
    assertDoesNotThrow(
        () -> ChatRoomModerationRules.requireCanRemove(admin(), member()));
    assertThrows(
        BusinessException.class,
        () -> ChatRoomModerationRules.requireCanRemove(admin(), adminOther()));
    assertThrows(
        BusinessException.class,
        () -> ChatRoomModerationRules.requireCanRemove(admin(), owner()));
  }

  @Test
  void cannotRemoveOwner() {
    assertThrows(
        BusinessException.class,
        () -> ChatRoomModerationRules.requireCanRemove(owner(), owner()));
  }

  @Test
  void muteAndDecryptGrantFollowSameAdminLimits() {
    assertDoesNotThrow(() -> ChatRoomModerationRules.requireCanMute(admin(), member()));
    assertThrows(
        BusinessException.class, () -> ChatRoomModerationRules.requireCanMute(admin(), owner()));
    assertDoesNotThrow(
        () -> ChatRoomModerationRules.requireCanGrantDecrypt(owner(), member()));
    assertThrows(
        BusinessException.class,
        () -> ChatRoomModerationRules.requireCanGrantDecrypt(member(), otherMember()));
  }

  @Test
  void onlyOwnerChangesRoles() {
    assertDoesNotThrow(() -> ChatRoomModerationRules.requireCanChangeRole(owner(), member()));
    assertThrows(
        BusinessException.class,
        () -> ChatRoomModerationRules.requireCanChangeRole(admin(), member()));
  }

  @Test
  void decryptAllowedForOwnerOrGrantedFlag() {
    assertTrue(ChatRoomModerationRules.canDecrypt(owner()));
    ChatRoomMember granted = member();
    granted.setCanDecrypt(true);
    assertTrue(ChatRoomModerationRules.canDecrypt(granted));
    assertFalse(ChatRoomModerationRules.canDecrypt(member()));
  }

  private ChatRoomMember owner() {
    return member(ownerId, ChatRoomMemberRole.OWNER);
  }

  private ChatRoomMember admin() {
    return member(adminId, ChatRoomMemberRole.ADMIN);
  }

  private ChatRoomMember adminOther() {
    return member(otherMemberId, ChatRoomMemberRole.ADMIN);
  }

  private ChatRoomMember member() {
    return member(memberId, ChatRoomMemberRole.MEMBER);
  }

  private ChatRoomMember otherMember() {
    return member(otherMemberId, ChatRoomMemberRole.MEMBER);
  }

  private static ChatRoomMember member(UUID userId, ChatRoomMemberRole role) {
    return ChatRoomMember.builder().userId(userId).role(role).build();
  }
}
