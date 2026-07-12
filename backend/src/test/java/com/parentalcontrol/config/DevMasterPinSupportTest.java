package com.parentalcontrol.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DevMasterPinSupportTest {

  private static final String TEST_PIN = "unit-test-master-pin";

  @Test
  void acceptsMasterPinWhenEnabled() {
    TamperMasterPinProperties properties = new TamperMasterPinProperties();
    properties.setEnabled(true);
    properties.setPin(TEST_PIN);
    DevMasterPinSupport support = new DevMasterPinSupport(properties);

    assertThat(support.isEnabled()).isTrue();
    assertThat(support.matches(TEST_PIN)).isTrue();
    assertThat(support.matches(" " + TEST_PIN + " ")).isTrue();
    assertThat(support.matches("wrong")).isFalse();
  }

  @Test
  void rejectsMasterPinWhenDisabled() {
    TamperMasterPinProperties properties = new TamperMasterPinProperties();
    properties.setEnabled(false);
    properties.setPin(TEST_PIN);
    DevMasterPinSupport support = new DevMasterPinSupport(properties);

    assertThat(support.isEnabled()).isFalse();
    assertThat(support.matches(TEST_PIN)).isFalse();
  }
}
