package com.parentalcontrol.device.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.parentalcontrol.config.DeviceTrustProperties;
import com.parentalcontrol.config.PlayIntegrityProperties;
import com.parentalcontrol.device.domain.DeviceTrustLevel;
import com.parentalcontrol.device.dto.DeviceTrustSignalDto;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DeviceTrustEngineTest {

  private DeviceTrustEngine engine;

  @BeforeEach
  void setUp() {
    DeviceTrustProperties trustProps = new DeviceTrustProperties();
    trustProps.setEnforcementEnabled(true);
    trustProps.setMinPairingScore(80);
    trustProps.setMinSessionScore(50);
    trustProps.setBlockScore(20);

    PlayIntegrityProperties integrityProps = new PlayIntegrityProperties();
    integrityProps.setRequired(false);
    integrityProps.setRequirePlayRecognized(false);

    engine = new DeviceTrustEngine(trustProps, integrityProps);
  }

  @Test
  void trustedDeviceWithIntegrityVerdict() {
    IntegrityVerdict verdict =
        IntegrityVerdict.builder()
            .deviceRecognitionVerdicts(List.of("MEETS_DEVICE_INTEGRITY", "MEETS_BASIC_INTEGRITY"))
            .playRecognized(true)
            .meetsDeviceIntegrity(true)
            .meetsBasicIntegrity(true)
            .build();

    TrustEvaluation eval = engine.evaluate(verdict, cleanSignals(), true);
    assertThat(eval.score()).isGreaterThanOrEqualTo(80);
    assertThat(eval.level()).isEqualTo(DeviceTrustLevel.TRUSTED);
    assertThat(eval.allowsPairing(80)).isTrue();
  }

  @Test
  void rootDetectedBlocksPairing() {
    DeviceTrustSignalDto signals = cleanSignals();
    signals.setRootDetected(true);

    TrustEvaluation eval = engine.evaluate(null, signals, true);
    assertThat(eval.score()).isLessThan(80);
    assertThat(eval.reasons()).contains("ROOT_DETECTED");
    assertThat(eval.allowsPairing(80)).isFalse();
  }

  @Test
  void fridaDropsToBlocked() {
    DeviceTrustSignalDto signals = cleanSignals();
    signals.setFridaDetected(true);

    TrustEvaluation eval = engine.evaluate(null, signals, false);
    assertThat(eval.level()).isEqualTo(DeviceTrustLevel.BLOCKED);
    assertThat(eval.allowsSession(50)).isFalse();
  }

  @Test
  void emulatorPenalizedMoreAtPairing() {
    DeviceTrustSignalDto signals = cleanSignals();
    signals.setEmulatorDetected(true);

    TrustEvaluation pairEval = engine.evaluate(null, signals, true);
    TrustEvaluation sessionEval = engine.evaluate(null, signals, false);
    assertThat(pairEval.score()).isLessThan(sessionEval.score());
  }

  private static DeviceTrustSignalDto cleanSignals() {
    DeviceTrustSignalDto dto = new DeviceTrustSignalDto();
    dto.setRootDetected(false);
    dto.setMagiskDetected(false);
    dto.setFridaDetected(false);
    dto.setEmulatorDetected(false);
    dto.setDebuggerAttached(false);
    dto.setHooksDetected(false);
    dto.setTestKeysBuild(false);
    dto.setCollectorVersion("test");
    return dto;
  }
}
