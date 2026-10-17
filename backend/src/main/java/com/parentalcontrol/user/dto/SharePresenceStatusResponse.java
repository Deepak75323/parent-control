package com.parentalcontrol.user.dto;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class SharePresenceStatusResponse {
  boolean enabled;
}
