package com.parentalcontrol.monitoring.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import lombok.Data;

@Data
public class SignalingMessageRequest {

  @NotNull private UUID sessionId;

  @NotBlank
  @Size(max = 64)
  private String messageType;

  @NotBlank
  @Size(max = 65536)
  private String payload;
}
