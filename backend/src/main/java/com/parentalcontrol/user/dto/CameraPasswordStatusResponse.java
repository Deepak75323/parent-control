package com.parentalcontrol.user.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CameraPasswordStatusResponse {
  private boolean pinConfigured;
  private boolean verified;
}
