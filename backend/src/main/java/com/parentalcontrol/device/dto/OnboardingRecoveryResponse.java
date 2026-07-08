package com.parentalcontrol.device.dto;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class OnboardingRecoveryResponse {
  boolean codeSent;
  String message;
}
