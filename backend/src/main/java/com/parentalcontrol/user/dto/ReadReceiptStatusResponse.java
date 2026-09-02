package com.parentalcontrol.user.dto;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ReadReceiptStatusResponse {
  boolean enabled;
}
