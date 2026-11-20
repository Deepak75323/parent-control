package com.parentalcontrol.user.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import lombok.Data;

@Data
public class SetAutoGreetingSettingsRequest {

  @NotNull private Boolean enabled;

  private UUID roomId;

  /** HH:mm morning window start — default 07:05. Alias: morningTime. */
  @Size(max = 5)
  private String morningStart;

  /** Legacy alias for {@link #morningStart}. */
  @Size(max = 5)
  private String morningTime;

  /** HH:mm morning window end — default 12:00 */
  @Size(max = 5)
  private String morningEnd;

  /** HH:mm — default 23:55 */
  @Size(max = 5)
  private String eveningStart;

  /** HH:mm — default 00:00 (midnight) */
  @Size(max = 5)
  private String eveningEnd;

  /** IANA zone, e.g. Asia/Kolkata */
  @Size(max = 64)
  private String timezone;

  /** Plaintext morning chat body (sealed before send). */
  @Size(max = 200)
  private String morningMessage;

  /** Plaintext evening chat body (sealed before send). */
  @Size(max = 200)
  private String eveningMessage;
}
