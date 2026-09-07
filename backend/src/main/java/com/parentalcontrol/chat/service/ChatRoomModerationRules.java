package com.parentalcontrol.chat.service;

import com.parentalcontrol.chat.domain.ChatRoomMember;
import com.parentalcontrol.chat.domain.ChatRoomMemberRole;
import com.parentalcontrol.common.constant.MessageConstants;
import com.parentalcontrol.common.exception.BusinessException;
import com.parentalcontrol.common.exception.ErrorCode;

/** Pure permission checks for room moderation (kick / mute / role / decrypt grant). */
final class ChatRoomModerationRules {

  private ChatRoomModerationRules() {}

  static boolean isOwnerOrAdmin(ChatRoomMember actor) {
    return actor.getRole() == ChatRoomMemberRole.OWNER
        || actor.getRole() == ChatRoomMemberRole.ADMIN;
  }

  static boolean canClearHistory(ChatRoomMember actor) {
    return isOwnerOrAdmin(actor);
  }

  static boolean canDecrypt(ChatRoomMember actor) {
    return actor.getRole() == ChatRoomMemberRole.OWNER || actor.isCanDecrypt();
  }

  static void requireCanRemove(ChatRoomMember actor, ChatRoomMember target) {
    requireModerator(actor);
    if (target.getRole() == ChatRoomMemberRole.OWNER) {
      throw new BusinessException(ErrorCode.FORBIDDEN, MessageConstants.CHAT_CANNOT_REMOVE_OWNER);
    }
    if (actor.getRole() == ChatRoomMemberRole.ADMIN
        && target.getRole() != ChatRoomMemberRole.MEMBER) {
      throw new BusinessException(ErrorCode.FORBIDDEN, MessageConstants.CHAT_CANNOT_MODERATE_TARGET);
    }
    if (actor.getUserId().equals(target.getUserId())) {
      throw new BusinessException(ErrorCode.FORBIDDEN, MessageConstants.CHAT_CANNOT_MODERATE_TARGET);
    }
  }

  static void requireCanMute(ChatRoomMember actor, ChatRoomMember target) {
    requireModerator(actor);
    if (target.getRole() == ChatRoomMemberRole.OWNER) {
      throw new BusinessException(ErrorCode.FORBIDDEN, MessageConstants.CHAT_CANNOT_MODERATE_TARGET);
    }
    if (actor.getRole() == ChatRoomMemberRole.ADMIN
        && target.getRole() != ChatRoomMemberRole.MEMBER) {
      throw new BusinessException(ErrorCode.FORBIDDEN, MessageConstants.CHAT_CANNOT_MODERATE_TARGET);
    }
  }

  static void requireCanGrantDecrypt(ChatRoomMember actor, ChatRoomMember target) {
    requireModerator(actor);
    if (target.getRole() == ChatRoomMemberRole.OWNER) {
      throw new BusinessException(ErrorCode.FORBIDDEN, MessageConstants.CHAT_CANNOT_MODERATE_TARGET);
    }
    if (actor.getRole() == ChatRoomMemberRole.ADMIN
        && target.getRole() != ChatRoomMemberRole.MEMBER) {
      throw new BusinessException(ErrorCode.FORBIDDEN, MessageConstants.CHAT_CANNOT_MODERATE_TARGET);
    }
  }

  static void requireCanChangeRole(ChatRoomMember actor, ChatRoomMember target) {
    if (actor.getRole() != ChatRoomMemberRole.OWNER) {
      throw new BusinessException(
          ErrorCode.FORBIDDEN, MessageConstants.CHAT_ONLY_OWNER_CAN_CHANGE_ROLE);
    }
    if (target.getRole() == ChatRoomMemberRole.OWNER
        || actor.getUserId().equals(target.getUserId())) {
      throw new BusinessException(ErrorCode.FORBIDDEN, MessageConstants.CHAT_CANNOT_MODERATE_TARGET);
    }
  }

  static void requireModerator(ChatRoomMember actor) {
    if (!isOwnerOrAdmin(actor)) {
      throw new BusinessException(ErrorCode.FORBIDDEN, MessageConstants.CHAT_CANNOT_MODERATE_TARGET);
    }
  }
}
