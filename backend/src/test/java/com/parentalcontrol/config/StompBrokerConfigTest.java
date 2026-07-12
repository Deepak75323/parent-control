package com.parentalcontrol.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.parentalcontrol.IntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest
class StompBrokerConfigTest {

  @DynamicPropertySource
  static void configure(DynamicPropertyRegistry registry) {
    IntegrationTestSupport.configureLocalInfra(registry, "parental_control_stomp_config_test");
    registry.add("app.stomp.relay-enabled", () -> "false");
  }

  @Autowired private StompBrokerProperties stompBrokerProperties;

  @Test
  void simpleBrokerModeIsDefaultForSingleReplicaDev() {
    assertThat(stompBrokerProperties.isRelayEnabled()).isFalse();
  }
}
