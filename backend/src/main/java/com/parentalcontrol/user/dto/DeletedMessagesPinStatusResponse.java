package com.parentalcontrol.user.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DeletedMessagesPinStatusResponse {
  private boolean pinConfigured;
  private boolean unlocked;
  private boolean verified;
}
