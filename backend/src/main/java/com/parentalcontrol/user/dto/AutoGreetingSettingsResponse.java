package com.parentalcontrol.user.dto;

import java.util.UUID;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class AutoGreetingSettingsResponse {
  boolean enabled;
  UUID roomId;
  /** HH:mm local morning send time. */
  String morningTime;
  /** HH:mm evening window start. */
  String eveningStart;
  /** HH:mm evening window end (00:00 means midnight / end of day). */
  String eveningEnd;
  String timezone;
  String morningMessage;
  String eveningMessage;
}
