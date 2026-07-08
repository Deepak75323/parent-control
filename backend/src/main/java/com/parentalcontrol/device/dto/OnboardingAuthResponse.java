package com.parentalcontrol.device.dto;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class OnboardingAuthResponse {
  boolean authenticated;
  /** Returned on successful recovery or password match against an active pairing code. */
  String pairingCode;
}
