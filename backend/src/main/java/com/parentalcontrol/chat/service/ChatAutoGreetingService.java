package com.parentalcontrol.chat.service;

import com.parentalcontrol.chat.util.ChatMessageObfuscation;
import com.parentalcontrol.user.dao.ParentProfileDao;
import com.parentalcontrol.user.domain.ParentProfile;
import com.parentalcontrol.user.service.AutoGreetingSettingsService;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@Slf4j
public class ChatAutoGreetingService {

  private final AutoGreetingSettingsService autoGreetingSettingsService;
  private final ParentProfileDao parentProfileDao;
  private final ChatService chatService;
  private final ChatAutoGreetingService self;

  public ChatAutoGreetingService(
      AutoGreetingSettingsService autoGreetingSettingsService,
      ParentProfileDao parentProfileDao,
      ChatService chatService,
      @Lazy ChatAutoGreetingService self) {
    this.autoGreetingSettingsService = autoGreetingSettingsService;
    this.parentProfileDao = parentProfileDao;
    this.chatService = chatService;
    this.self = self;
  }

  public int processDueGreetings() {
    List<ParentProfile> profiles = autoGreetingSettingsService.findEnabledWithRoom();
    int sent = 0;
    for (ParentProfile profile : profiles) {
      try {
        sent += self.processOne(profile);
      } catch (RuntimeException ex) {
        log.warn(
            "Auto greeting failed for profileId={} roomId={}: {}",
            profile.getId(),
            profile.getAutoGreetingRoomId(),
            ex.getMessage());
      }
    }
    return sent;
  }

  @Transactional
  public int processOne(ParentProfile profile) {
    UUID userId = profile.getUserId();
    UUID roomId = profile.getAutoGreetingRoomId();
    if (userId == null || roomId == null) {
      return 0;
    }

    ZoneId zone = resolveZone(profile.getAutoGreetingTimezone());
    ZonedDateTime zonedNow = ZonedDateTime.now(zone);
    LocalDate localDate = zonedNow.toLocalDate();
    LocalTime localTime = zonedNow.toLocalTime();

    LocalTime morningStart =
        profile.getAutoGreetingMorningTime() != null
            ? profile.getAutoGreetingMorningTime()
            : AutoGreetingSettingsService.DEFAULT_MORNING_START;
    LocalTime morningEnd =
        profile.getAutoGreetingMorningEnd() != null
            ? profile.getAutoGreetingMorningEnd()
            : AutoGreetingSettingsService.DEFAULT_MORNING_END;
    LocalTime eveningStart =
        profile.getAutoGreetingEveningStart() != null
            ? profile.getAutoGreetingEveningStart()
            : AutoGreetingSettingsService.DEFAULT_EVENING_START;
    LocalTime eveningEnd =
        profile.getAutoGreetingEveningEnd() != null
            ? profile.getAutoGreetingEveningEnd()
            : AutoGreetingSettingsService.DEFAULT_EVENING_END;

    String morningMessage =
        StringUtils.hasText(profile.getAutoGreetingMorningMessage())
            ? profile.getAutoGreetingMorningMessage()
            : AutoGreetingSettingsService.DEFAULT_MORNING_MESSAGE;
    String eveningMessage =
        StringUtils.hasText(profile.getAutoGreetingEveningMessage())
            ? profile.getAutoGreetingEveningMessage()
            : AutoGreetingSettingsService.DEFAULT_EVENING_MESSAGE;

    int sent = 0;
    if (AutoGreetingScheduleEvaluator.isInMorningWindow(localTime, morningStart, morningEnd)
        && !localDate.equals(profile.getAutoGreetingLastMorningOn())) {
      postGreeting(userId, roomId, morningMessage);
      profile.setAutoGreetingLastMorningOn(localDate);
      parentProfileDao.save(profile);
      sent++;
    }

    if (AutoGreetingScheduleEvaluator.isInEveningWindow(localTime, eveningStart, eveningEnd)) {
      LocalDate eveningKey =
          AutoGreetingScheduleEvaluator.eveningAnchorDate(
              localDate, localTime, eveningStart, eveningEnd);
      if (!eveningKey.equals(profile.getAutoGreetingLastEveningOn())) {
        postGreeting(userId, roomId, eveningMessage);
        profile.setAutoGreetingLastEveningOn(eveningKey);
        parentProfileDao.save(profile);
        sent++;
      }
    }
    return sent;
  }

  private void postGreeting(UUID userId, UUID roomId, String plaintext) {
    String sealed = ChatMessageObfuscation.encode(plaintext);
    chatService.sendMessageAsUser(userId, roomId, sealed, null, null);
  }

  private static ZoneId resolveZone(String timezone) {
    if (!StringUtils.hasText(timezone)) {
      return ZoneId.of(AutoGreetingSettingsService.DEFAULT_TIMEZONE);
    }
    try {
      return ZoneId.of(timezone.trim());
    } catch (DateTimeException ex) {
      return ZoneId.of(AutoGreetingSettingsService.DEFAULT_TIMEZONE);
    }
  }
}
