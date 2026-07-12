package com.parentalcontrol.device.security;

import com.parentalcontrol.config.DeviceTrustProperties;
import com.parentalcontrol.config.PlayIntegrityProperties;
import com.parentalcontrol.device.domain.DeviceTrustLevel;
import com.parentalcontrol.device.dto.DeviceTrustSignalDto;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DeviceTrustEngine {

  private final DeviceTrustProperties deviceTrustProperties;
  private final PlayIntegrityProperties playIntegrityProperties;

  public TrustEvaluation evaluate(
      IntegrityVerdict integrity, DeviceTrustSignalDto signals, boolean pairing) {
    int score = 100;
    List<String> reasons = new ArrayList<>();

    if (integrity != null) {
      if (!integrity.meetsDeviceIntegrity()) {
        score -= 40;
        reasons.add("DEVICE_INTEGRITY_FAILED");
      }
      if (playIntegrityProperties.isRequireStrongIntegrity() && !integrity.meetsStrongIntegrity()) {
        score -= 20;
        reasons.add("STRONG_INTEGRITY_REQUIRED");
      }
      if (playIntegrityProperties.isRequirePlayRecognized() && !integrity.playRecognized()) {
        score -= 35;
        reasons.add("NOT_PLAY_RECOGNIZED");
      }
      if (!integrity.meetsBasicIntegrity()) {
        score -= 25;
        reasons.add("BASIC_INTEGRITY_FAILED");
      }
    } else if (playIntegrityProperties.isRequired()
        || deviceTrustProperties.isEnforcementEnabled()) {
      score -= 50;
      reasons.add("MISSING_INTEGRITY_VERDICT");
    }

    if (signals != null) {
      if (Boolean.TRUE.equals(signals.getRootDetected())) {
        score -= 50;
        reasons.add("ROOT_DETECTED");
      }
      if (Boolean.TRUE.equals(signals.getMagiskDetected())) {
        score -= 50;
        reasons.add("MAGISK_DETECTED");
      }
      if (Boolean.TRUE.equals(signals.getFridaDetected())) {
        score -= 60;
        reasons.add("FRIDA_DETECTED");
      }
      if (Boolean.TRUE.equals(signals.getHooksDetected())) {
        score -= 40;
        reasons.add("HOOKS_DETECTED");
      }
      if (Boolean.TRUE.equals(signals.getDebuggerAttached())) {
        score -= 30;
        reasons.add("DEBUGGER_ATTACHED");
      }
      if (Boolean.TRUE.equals(signals.getEmulatorDetected())) {
        score -= pairing ? 40 : 20;
        reasons.add("EMULATOR_DETECTED");
      }
      if (Boolean.TRUE.equals(signals.getTestKeysBuild())) {
        score -= 25;
        reasons.add("TEST_KEYS_BUILD");
      }
    }

    score = Math.max(0, Math.min(100, score));
    DeviceTrustLevel level = toLevel(score);
    return TrustEvaluation.builder()
        .score(score)
        .level(level)
        .reasons(reasons)
        .integrityVerdict(integrity)
        .build();
  }

  private DeviceTrustLevel toLevel(int score) {
    if (score < deviceTrustProperties.getBlockScore()) {
      return DeviceTrustLevel.BLOCKED;
    }
    if (score < deviceTrustProperties.getMinSessionScore()) {
      return DeviceTrustLevel.UNTRUSTED;
    }
    if (score < deviceTrustProperties.getMinPairingScore()) {
      return DeviceTrustLevel.ELEVATED_RISK;
    }
    return DeviceTrustLevel.TRUSTED;
  }
}
