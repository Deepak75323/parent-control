package com.parentalcontrol.user.service;

import com.parentalcontrol.chat.repository.ChatRoomMemberRepository;
import com.parentalcontrol.common.constant.MessageConstants;
import com.parentalcontrol.common.exception.BusinessException;
import com.parentalcontrol.common.exception.ErrorCode;
import com.parentalcontrol.common.security.SecurityUtils;
import com.parentalcontrol.user.dao.ParentProfileDao;
import com.parentalcontrol.user.domain.ParentProfile;
import com.parentalcontrol.user.dto.AutoGreetingSettingsResponse;
import com.parentalcontrol.user.dto.SetAutoGreetingSettingsRequest;
import java.time.DateTimeException;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class AutoGreetingSettingsService {

  public static final String DEFAULT_MORNING_MESSAGE = "good morning baby";
  public static final String DEFAULT_EVENING_MESSAGE = "good night baby";
  public static final String DEFAULT_TIMEZONE = "Asia/Kolkata";
  public static final LocalTime DEFAULT_MORNING = LocalTime.of(7, 5);
  public static final LocalTime DEFAULT_EVENING_START = LocalTime.of(23, 55);
  public static final LocalTime DEFAULT_EVENING_END = LocalTime.MIDNIGHT;

  private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");

  private final ParentProfileDao parentProfileDao;
  private final ChatRoomMemberRepository memberRepository;

  @Transactional(readOnly = true)
  public AutoGreetingSettingsResponse getSettings() {
    UUID userId = SecurityUtils.requireUserId();
    ParentProfile profile = requireProfile(userId);
    return toResponse(profile);
  }

  @Transactional
  public AutoGreetingSettingsResponse updateSettings(SetAutoGreetingSettingsRequest request) {
    UUID userId = SecurityUtils.requireUserId();
    ParentProfile profile = requireProfile(userId);

    boolean enabled = Boolean.TRUE.equals(request.getEnabled());
    UUID roomId = request.getRoomId() != null ? request.getRoomId() : profile.getAutoGreetingRoomId();
    if (enabled) {
      if (roomId == null) {
        throw new BusinessException(ErrorCode.VALIDATION_ERROR, MessageConstants.AUTO_GREETING_ROOM_REQUIRED);
      }
      requireActiveMembership(roomId, userId);
    } else if (roomId != null) {
      requireActiveMembership(roomId, userId);
    }

    LocalTime morning = parseTimeOrDefault(request.getMorningTime(), DEFAULT_MORNING);
    LocalTime eveningStart = parseTimeOrDefault(request.getEveningStart(), DEFAULT_EVENING_START);
    LocalTime eveningEnd = parseTimeOrDefault(request.getEveningEnd(), DEFAULT_EVENING_END);
    String timezone = resolveTimezone(request.getTimezone(), profile.getAutoGreetingTimezone());

    profile.setAutoGreetingEnabled(enabled);
    profile.setAutoGreetingRoomId(roomId);
    profile.setAutoGreetingMorningTime(morning);
    profile.setAutoGreetingEveningStart(eveningStart);
    profile.setAutoGreetingEveningEnd(eveningEnd);
    profile.setAutoGreetingTimezone(timezone);
    parentProfileDao.save(profile);
    return toResponse(profile);
  }

  @Transactional(readOnly = true)
  public List<ParentProfile> findEnabledWithRoom() {
    return parentProfileDao.findAutoGreetingEnabled();
  }

  private ParentProfile requireProfile(UUID userId) {
    return parentProfileDao
        .findByUser_Id(userId)
        .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, MessageConstants.USER_NOT_FOUND));
  }

  private void requireActiveMembership(UUID roomId, UUID userId) {
    memberRepository
        .findByRoomIdAndUserId(roomId, userId)
        .orElseThrow(
            () -> new BusinessException(ErrorCode.FORBIDDEN, MessageConstants.CHAT_NOT_A_MEMBER));
  }

  private static LocalTime parseTimeOrDefault(String raw, LocalTime fallback) {
    if (!StringUtils.hasText(raw)) {
      return fallback;
    }
    try {
      return LocalTime.parse(raw.trim(), HH_MM);
    } catch (DateTimeParseException ex) {
      throw new BusinessException(ErrorCode.VALIDATION_ERROR, MessageConstants.AUTO_GREETING_INVALID_TIMES);
    }
  }

  private static String resolveTimezone(String requested, String existing) {
    String candidate =
        StringUtils.hasText(requested)
            ? requested.trim()
            : (StringUtils.hasText(existing) ? existing : DEFAULT_TIMEZONE);
    try {
      ZoneId.of(candidate);
      return candidate;
    } catch (DateTimeException ex) {
      throw new BusinessException(
          ErrorCode.VALIDATION_ERROR, MessageConstants.AUTO_GREETING_INVALID_TIMEZONE);
    }
  }

  private static AutoGreetingSettingsResponse toResponse(ParentProfile profile) {
    LocalTime morning =
        profile.getAutoGreetingMorningTime() != null
            ? profile.getAutoGreetingMorningTime()
            : DEFAULT_MORNING;
    LocalTime eveningStart =
        profile.getAutoGreetingEveningStart() != null
            ? profile.getAutoGreetingEveningStart()
            : DEFAULT_EVENING_START;
    LocalTime eveningEnd =
        profile.getAutoGreetingEveningEnd() != null
            ? profile.getAutoGreetingEveningEnd()
            : DEFAULT_EVENING_END;
    String timezone =
        StringUtils.hasText(profile.getAutoGreetingTimezone())
            ? profile.getAutoGreetingTimezone()
            : DEFAULT_TIMEZONE;
    return AutoGreetingSettingsResponse.builder()
        .enabled(profile.isAutoGreetingEnabled())
        .roomId(profile.getAutoGreetingRoomId())
        .morningTime(morning.format(HH_MM))
        .eveningStart(eveningStart.format(HH_MM))
        .eveningEnd(eveningEnd.format(HH_MM))
        .timezone(timezone)
        .morningMessage(DEFAULT_MORNING_MESSAGE)
        .eveningMessage(DEFAULT_EVENING_MESSAGE)
        .build();
  }
}
