package com.parentalcontrol.user.dto;

import java.util.UUID;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class AutoGreetingSettingsResponse {
  boolean enabled;
  UUID roomId;
  /** HH:mm morning window start. */
  String morningStart;
  /** Deprecated alias of morningStart for older clients. */
  String morningTime;
  /** HH:mm morning window end. */
  String morningEnd;
  /** HH:mm evening window start. */
  String eveningStart;
  /** HH:mm evening window end (00:00 means midnight / end of day). */
  String eveningEnd;
  String timezone;
  String morningMessage;
  String eveningMessage;
}
