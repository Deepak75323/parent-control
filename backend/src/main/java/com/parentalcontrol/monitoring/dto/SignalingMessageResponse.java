package com.parentalcontrol.monitoring.dto;

import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SignalingMessageResponse {

  private UUID id;
  private String roomId;
  private String messageType;
  private String payload;
  private String senderRole;
}
