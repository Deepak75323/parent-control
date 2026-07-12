package com.parentalcontrol;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PairingRateLimitIntegrationTest {

  @DynamicPropertySource
  static void configure(DynamicPropertyRegistry registry) {
    IntegrationTestSupport.configureLocalInfra(registry, "parental_control_pair_rate_test");
    registry.add("app.rate-limit.enabled", () -> "true");
    registry.add("app.rate-limit.auth-requests-per-minute", () -> "2");
    registry.add("app.hmac.required", () -> "false");
  }

  @Autowired private MockMvc mockMvc;

  @Test
  @Order(1)
  void pairingEndpointEnforcesRateLimit() throws Exception {
    String body =
        """
                {
                  "pairingCode": "INVALID1",
                  "deviceName": "Bot",
                  "deviceModel": "Pixel",
                  "osVersion": "14",
                  "appVersion": "1.0.0",
                  "androidId": "rate-limit-bot-001",
                  "consentAccepted": "true"
                }
                """;

    mockMvc
        .perform(post("/api/v1/device/pair").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isBadRequest());

    mockMvc
        .perform(post("/api/v1/device/pair").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isBadRequest());

    mockMvc
        .perform(post("/api/v1/device/pair").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isTooManyRequests());
  }
}
