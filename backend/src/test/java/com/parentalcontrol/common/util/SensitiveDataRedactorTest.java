package com.parentalcontrol.common.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SensitiveDataRedactorTest {

  @Test
  void redactsEmail() {
    assertThat(SensitiveDataRedactor.email("parent@example.com")).isEqualTo("p***@example.com");
    assertThat(SensitiveDataRedactor.email(null)).isEqualTo("[REDACTED]");
  }

  @Test
  void redactsAndroidId() {
    assertThat(SensitiveDataRedactor.androidId("abcd1234efgh5678")).contains("…");
    assertThat(SensitiveDataRedactor.androidId("short")).isEqualTo("[REDACTED]");
  }
}
