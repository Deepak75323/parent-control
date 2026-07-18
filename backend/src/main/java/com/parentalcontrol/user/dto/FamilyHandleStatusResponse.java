package com.parentalcontrol.user.dto;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class FamilyHandleStatusResponse {
  boolean configured;

  /** Normalized handle without @, or null when not set. */
  String familyHandle;
}
