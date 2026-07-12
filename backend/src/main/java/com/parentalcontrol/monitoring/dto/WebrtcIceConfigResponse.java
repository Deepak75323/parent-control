package com.parentalcontrol.monitoring.dto;

import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class WebrtcIceConfigResponse {
  private List<IceServerDto> iceServers;

  /** Unix epoch seconds when ephemeral TURN credentials expire (null for static/dev). */
  private Long credentialExpiresAt;

  private Long credentialTtlSeconds;

  @Data
  @Builder
  public static class IceServerDto {
    private List<String> urls;
    private String username;
    private String credential;
  }
}
