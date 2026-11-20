package com.parentalcontrol.user.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import lombok.Data;

@Data
public class SetAutoGreetingSettingsRequest {

  @NotNull private Boolean enabled;

  private UUID roomId;

  /** HH:mm — default 07:05 */
  @Size(max = 5)
  private String morningTime;

  /** HH:mm — default 23:55 */
  @Size(max = 5)
  private String eveningStart;

  /** HH:mm — default 00:00 (midnight) */
  @Size(max = 5)
  private String eveningEnd;

  /** IANA zone, e.g. Asia/Kolkata */
  @Size(max = 64)
  private String timezone;
}
