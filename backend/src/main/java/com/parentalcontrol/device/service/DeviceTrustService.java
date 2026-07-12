package com.parentalcontrol.device.service;

import com.parentalcontrol.common.constant.MessageConstants;
import com.parentalcontrol.common.exception.BusinessException;
import com.parentalcontrol.common.exception.ErrorCode;
import com.parentalcontrol.config.DeviceTrustProperties;
import com.parentalcontrol.config.PlayIntegrityProperties;
import com.parentalcontrol.device.domain.Device;
import com.parentalcontrol.device.domain.DeviceTrustLevel;
import com.parentalcontrol.device.dto.DeviceTrustSignalDto;
import com.parentalcontrol.device.security.DeviceTrustEngine;
import com.parentalcontrol.device.security.IntegrityVerdict;
import com.parentalcontrol.device.security.PlayIntegrityVerificationService;
import com.parentalcontrol.device.security.TrustEvaluation;
import com.parentalcontrol.monitoring.service.AlertService;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class DeviceTrustService {

  private final DeviceTrustProperties deviceTrustProperties;
  private final PlayIntegrityProperties playIntegrityProperties;
  private final PlayIntegrityVerificationService playIntegrityVerificationService;
  private final DeviceTrustEngine deviceTrustEngine;
  private final AlertService alertService;
  private final DeviceTrustObservabilityService observabilityService;

  public TrustEvaluation evaluatePairing(String integrityToken, DeviceTrustSignalDto signals) {
    IntegrityVerdict verdict =
        playIntegrityVerificationService.verifyAndDecode(
            integrityToken, playIntegrityProperties.getPackageName());
    TrustEvaluation evaluation = deviceTrustEngine.evaluate(verdict, signals, true);

    if (deviceTrustProperties.isEnforcementEnabled()
        && !evaluation.allowsPairing(deviceTrustProperties.getMinPairingScore())) {
      observabilityService.recordPairingDenied(evaluation.level(), evaluation.score());
      throw new BusinessException(
          ErrorCode.DEVICE_TRUST_DENIED,
          MessageConstants.DEVICE_FAILED_TRUST_REQUIREMENTS_FOR_PAIRING
              + String.join(", ", evaluation.reasons()));
    }
    return evaluation;
  }

  public TrustEvaluation evaluateHeartbeat(
      Device device, String integrityToken, DeviceTrustSignalDto signals) {
    IntegrityVerdict verdict =
        playIntegrityVerificationService.verifyAndDecode(
            integrityToken, playIntegrityProperties.getPackageName());
    TrustEvaluation evaluation = deviceTrustEngine.evaluate(verdict, signals, false);
    applyEvaluation(device, evaluation, signals);

    if (deviceTrustProperties.isEnforcementEnabled()
        && !evaluation.allowsSession(deviceTrustProperties.getMinSessionScore())) {
      observabilityService.recordSessionDenied(
          device.getId(), evaluation.level(), evaluation.score());
      throw new BusinessException(
          ErrorCode.DEVICE_TRUST_DENIED,
          MessageConstants.DEVICE_TRUST_SCORE_TOO_LOW_FOR_ACTIVE_SESSION);
    }
    if (evaluation.level() == DeviceTrustLevel.ELEVATED_RISK) {
      observabilityService.recordElevatedRisk(device.getId());
    }
    return evaluation;
  }

  public void applyEvaluation(
      Device device, TrustEvaluation evaluation, DeviceTrustSignalDto signals) {
    DeviceTrustLevel previous = device.getTrustLevel();
    device.setTrustScore(evaluation.score());
    device.setTrustLevel(evaluation.level());
    device.setLastTrustEvaluatedAt(Instant.now());
    device.setTrustSignalsJson(toSignalMap(signals));
    if (evaluation.integrityVerdict() != null) {
      device.setIntegrityVerdictJson(toVerdictMap(evaluation.integrityVerdict()));
    }
    if (evaluation.level() == DeviceTrustLevel.BLOCKED
        || evaluation.level() == DeviceTrustLevel.UNTRUSTED) {
      device.setTrustViolationCount(device.getTrustViolationCount() + 1);
    }
    if (previous != null
        && previous != evaluation.level()
        && isDegradation(previous, evaluation.level())) {
      alertService.checkDeviceTrustDegraded(device, previous, evaluation);
    }
  }

  private static boolean isDegradation(DeviceTrustLevel previous, DeviceTrustLevel current) {
    return rank(current) > rank(previous);
  }

  private static int rank(DeviceTrustLevel level) {
    return switch (level) {
      case TRUSTED -> 0;
      case ELEVATED_RISK -> 1;
      case UNKNOWN -> 2;
      case UNTRUSTED -> 3;
      case BLOCKED -> 4;
    };
  }

  private Map<String, Object> toSignalMap(DeviceTrustSignalDto signals) {
    if (signals == null) {
      return Map.of();
    }
    Map<String, Object> map = new HashMap<>();
    map.put("rootDetected", signals.getRootDetected());
    map.put("magiskDetected", signals.getMagiskDetected());
    map.put("fridaDetected", signals.getFridaDetected());
    map.put("emulatorDetected", signals.getEmulatorDetected());
    map.put("debuggerAttached", signals.getDebuggerAttached());
    map.put("hooksDetected", signals.getHooksDetected());
    map.put("testKeysBuild", signals.getTestKeysBuild());
    map.put("collectorVersion", signals.getCollectorVersion());
    return map;
  }

  private Map<String, Object> toVerdictMap(IntegrityVerdict verdict) {
    Map<String, Object> map = new HashMap<>();
    map.put("packageName", verdict.packageName());
    map.put("appRecognitionVerdict", verdict.appRecognitionVerdict());
    map.put("deviceRecognitionVerdicts", verdict.deviceRecognitionVerdicts());
    map.put("appLicensingVerdict", verdict.appLicensingVerdict());
    map.put("playRecognized", verdict.playRecognized());
    map.put("meetsDeviceIntegrity", verdict.meetsDeviceIntegrity());
    map.put("meetsStrongIntegrity", verdict.meetsStrongIntegrity());
    map.put("meetsBasicIntegrity", verdict.meetsBasicIntegrity());
    return map;
  }
}
