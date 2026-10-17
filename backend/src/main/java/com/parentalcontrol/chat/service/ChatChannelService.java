package com.parentalcontrol.chat.service;

import com.parentalcontrol.chat.domain.ChatChannel;
import com.parentalcontrol.chat.domain.ChatChannelType;
import com.parentalcontrol.chat.domain.ChatRoomMember;
import com.parentalcontrol.chat.domain.ChatRoomMemberRole;
import com.parentalcontrol.chat.dto.ChatChannelResponse;
import com.parentalcontrol.chat.dto.CreateChatChannelRequest;
import com.parentalcontrol.chat.repository.ChatChannelRepository;
import com.parentalcontrol.chat.repository.ChatRoomMemberRepository;
import com.parentalcontrol.common.constant.MessageConstants;
import com.parentalcontrol.common.exception.BusinessException;
import com.parentalcontrol.common.exception.ErrorCode;
import com.parentalcontrol.common.security.SecurityUtils;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class ChatChannelService {

  public static final int MAX_CHANNELS_PER_ROOM = 20;
  public static final String DEFAULT_TEXT_NAME = "general";
  public static final String DEFAULT_VOICE_NAME = "Lounge";

  private final ChatChannelRepository channelRepository;
  private final ChatRoomMemberRepository memberRepository;

  @Transactional
  public void ensureDefaultChannels(UUID roomId) {
    List<ChatChannel> existing = channelRepository.findActiveByRoomId(roomId);
    boolean hasText =
        existing.stream().anyMatch(c -> c.getChannelType() == ChatChannelType.TEXT);
    boolean hasVoice =
        existing.stream().anyMatch(c -> c.getChannelType() == ChatChannelType.VOICE);
    if (!hasText) {
      channelRepository.save(
          ChatChannel.builder()
              .roomId(roomId)
              .name(DEFAULT_TEXT_NAME)
              .channelType(ChatChannelType.TEXT)
              .position(0)
              .build());
    }
    if (!hasVoice) {
      channelRepository.save(
          ChatChannel.builder()
              .roomId(roomId)
              .name(DEFAULT_VOICE_NAME)
              .channelType(ChatChannelType.VOICE)
              .position(1)
              .build());
    }
  }

  @Transactional(readOnly = true)
  public List<ChatChannelResponse> listChannels(UUID roomId) {
    UUID userId = SecurityUtils.requireUserId();
    requireActiveMember(roomId, userId);
    return channelRepository.findActiveByRoomId(roomId).stream().map(this::toResponse).toList();
  }

  @Transactional(readOnly = true)
  public Map<UUID, List<ChatChannelResponse>> channelsByRoomIds(Collection<UUID> roomIds) {
    if (roomIds == null || roomIds.isEmpty()) {
      return Map.of();
    }
    return channelRepository.findActiveByRoomIdIn(roomIds).stream()
        .map(this::toResponse)
        .collect(
            Collectors.groupingBy(
                ChatChannelResponse::getRoomId,
                Collectors.collectingAndThen(
                    Collectors.toList(),
                    list ->
                        list.stream()
                            .sorted(
                                Comparator.comparingInt(ChatChannelResponse::getPosition)
                                    .thenComparing(ChatChannelResponse::getCreatedAt))
                            .toList())));
  }

  @Transactional
  public ChatChannelResponse createChannel(UUID roomId, CreateChatChannelRequest request) {
    UUID userId = SecurityUtils.requireUserId();
    ChatRoomMember member = requireActiveMember(roomId, userId);
    if (member.getRole() != ChatRoomMemberRole.OWNER
        && member.getRole() != ChatRoomMemberRole.ADMIN) {
      throw new BusinessException(
          ErrorCode.FORBIDDEN, MessageConstants.CHAT_CHANNEL_CREATE_FORBIDDEN);
    }

    String name = normalizeName(request.getName());
    ChatChannelType type = request.getChannelType();
    if (type == null) {
      throw new BusinessException(
          ErrorCode.VALIDATION_ERROR, MessageConstants.CHAT_CHANNEL_TYPE_REQUIRED);
    }
    if (channelRepository.countActiveByRoomId(roomId) >= MAX_CHANNELS_PER_ROOM) {
      throw new BusinessException(
          ErrorCode.VALIDATION_ERROR, MessageConstants.CHAT_CHANNEL_LIMIT);
    }
    if (channelRepository.existsActiveNameIgnoreCase(roomId, name)) {
      throw new BusinessException(
          ErrorCode.CONFLICT, MessageConstants.CHAT_CHANNEL_NAME_TAKEN);
    }

    int nextPosition =
        channelRepository.findActiveByRoomId(roomId).stream()
                .mapToInt(ChatChannel::getPosition)
                .max()
                .orElse(-1)
            + 1;

    ChatChannel channel =
        channelRepository.save(
            ChatChannel.builder()
                .roomId(roomId)
                .name(name)
                .channelType(type)
                .position(nextPosition)
                .build());
    return toResponse(channel);
  }

  @Transactional(readOnly = true)
  public ChatChannel requireTextChannel(UUID roomId, UUID channelId) {
    ChatChannel channel = requireChannel(roomId, channelId);
    if (channel.getChannelType() != ChatChannelType.TEXT) {
      throw new BusinessException(
          ErrorCode.VALIDATION_ERROR, MessageConstants.CHAT_CHANNEL_TEXT_REQUIRED);
    }
    return channel;
  }

  @Transactional(readOnly = true)
  public ChatChannel requireVoiceChannel(UUID roomId, UUID channelId) {
    ChatChannel channel = requireChannel(roomId, channelId);
    if (channel.getChannelType() != ChatChannelType.VOICE) {
      throw new BusinessException(
          ErrorCode.VALIDATION_ERROR, MessageConstants.CHAT_CHANNEL_VOICE_REQUIRED);
    }
    return channel;
  }

  @Transactional(readOnly = true)
  public ChatChannel resolveTextChannel(UUID roomId, UUID channelIdOrNull) {
    if (channelIdOrNull != null) {
      return requireTextChannel(roomId, channelIdOrNull);
    }
    return channelRepository.findActiveByRoomIdAndType(roomId, ChatChannelType.TEXT).stream()
        .findFirst()
        .orElseThrow(
            () ->
                new BusinessException(
                    ErrorCode.NOT_FOUND, MessageConstants.CHAT_CHANNEL_NOT_FOUND));
  }

  @Transactional(readOnly = true)
  public ChatChannel resolveVoiceChannel(UUID roomId, UUID channelIdOrNull) {
    if (channelIdOrNull != null) {
      return requireVoiceChannel(roomId, channelIdOrNull);
    }
    return channelRepository.findActiveByRoomIdAndType(roomId, ChatChannelType.VOICE).stream()
        .findFirst()
        .orElseThrow(
            () ->
                new BusinessException(
                    ErrorCode.NOT_FOUND, MessageConstants.CHAT_CHANNEL_NOT_FOUND));
  }

  @Transactional(readOnly = true)
  public ChatChannel requireChannel(UUID roomId, UUID channelId) {
    return channelRepository
        .findActiveByIdAndRoomId(channelId, roomId)
        .orElseThrow(
            () ->
                new BusinessException(
                    ErrorCode.NOT_FOUND, MessageConstants.CHAT_CHANNEL_NOT_FOUND));
  }

  private ChatRoomMember requireActiveMember(UUID roomId, UUID userId) {
    return memberRepository
        .findByRoomIdAndUserId(roomId, userId)
        .orElseThrow(
            () ->
                new BusinessException(
                    ErrorCode.FORBIDDEN, MessageConstants.CHAT_NOT_A_MEMBER));
  }

  private String normalizeName(String raw) {
    if (!StringUtils.hasText(raw)) {
      throw new BusinessException(
          ErrorCode.VALIDATION_ERROR, MessageConstants.CHAT_CHANNEL_NAME_REQUIRED);
    }
    String name = raw.trim().replaceAll("\\s+", "-");
    if (name.startsWith("#")) {
      name = name.substring(1).trim();
    }
    if (!StringUtils.hasText(name) || name.length() > 64) {
      throw new BusinessException(
          ErrorCode.VALIDATION_ERROR, MessageConstants.CHAT_CHANNEL_NAME_REQUIRED);
    }
    return name;
  }

  private ChatChannelResponse toResponse(ChatChannel channel) {
    return ChatChannelResponse.builder()
        .id(channel.getId())
        .roomId(channel.getRoomId())
        .name(channel.getName())
        .channelType(channel.getChannelType())
        .position(channel.getPosition())
        .createdAt(channel.getCreatedAt())
        .build();
  }
}
