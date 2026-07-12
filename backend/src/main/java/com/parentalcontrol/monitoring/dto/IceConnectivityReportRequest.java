package com.parentalcontrol.monitoring.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class IceConnectivityReportRequest {

  @NotBlank private String streamType;

  private String sessionId;

  /** ICE connection state: connected, failed, disconnected, etc. */
  @NotBlank private String iceConnectionState;

  /** Selected candidate pair type: host, srflx, relay, prflx */
  private String selectedCandidateType;

  private Boolean turnUsed;

  private String failureReason;
}
