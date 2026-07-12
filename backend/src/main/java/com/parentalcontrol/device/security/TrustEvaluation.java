package com.parentalcontrol.device.security;

import com.parentalcontrol.device.domain.DeviceTrustLevel;
import java.util.List;
import lombok.Builder;

@Builder
public record TrustEvaluation(
    int score, DeviceTrustLevel level, List<String> reasons, IntegrityVerdict integrityVerdict) {
  public boolean allowsPairing(int minPairingScore) {
    return score >= minPairingScore && level != DeviceTrustLevel.BLOCKED;
  }

  public boolean allowsSession(int minSessionScore) {
    return score >= minSessionScore && level != DeviceTrustLevel.BLOCKED;
  }
}
