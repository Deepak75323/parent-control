package com.parentalcontrol.user.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ParentPinStatusResponse {
  private boolean pinConfigured;
  private boolean verified;
  private boolean silentStreamUnlocked;
}
