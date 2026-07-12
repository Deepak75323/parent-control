package com.parentalcontrol;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.parentalcontrol.device.dao.DeviceDao;
import java.util.UUID;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DefaultChildLoginPasswordIntegrationTest {

  @DynamicPropertySource
  static void configure(DynamicPropertyRegistry registry) {
    IntegrationTestSupport.configureLocalInfra(registry, "parental_control_family_pass_test");
    registry.add("app.hmac.required", () -> "false");
  }

  @Autowired private MockMvc mockMvc;

  @Autowired private ObjectMapper objectMapper;

  @Autowired private DeviceDao deviceRepository;

  @Autowired private PasswordEncoder passwordEncoder;

  private static String accessToken;

  @Test
  @Order(1)
  void registerAndLogin() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                                {
                                  "email": "family-pass@local.test",
                                  "password": "SecurePass123!",
                                  "firstName": "Family",
                                  "lastName": "Parent",
                                  "consentAccepted": "true"
                                }
                                """))
        .andExpect(status().isCreated());

    MvcResult login =
        mockMvc
            .perform(
                post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                                {
                                  "email": "family-pass@local.test",
                                  "password": "SecurePass123!"
                                }
                                """))
            .andExpect(status().isOk())
            .andReturn();
    accessToken =
        objectMapper
            .readTree(login.getResponse().getContentAsString())
            .get("data")
            .get("accessToken")
            .asText();
  }

  @Test
  @Order(2)
  void statusBeforeConfigured() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/parent/child-login-password/status")
                .header("Authorization", "Bearer " + accessToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.configured").value(false));
  }

  @Test
  @Order(3)
  void pairingCodeRequiresFamilyPassword() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/devices/pairing-code")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  @Order(4)
  void setFamilyPassword() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/parent/child-login-password")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"childLoginPassword\":\"FamilyPass123\"}"))
        .andExpect(status().isOk());
  }

  @Test
  @Order(5)
  void pairingCodeUsesFamilyPassword() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/devices/pairing-code")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.code").exists());
  }

  @Test
  @Order(6)
  void onboardingAuthWithFamilyPassword() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/onboarding/authenticate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                                {
                                  "childLoginPassword": "FamilyPass123"
                                }
                                """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.authenticated").value(true));
  }

  @Test
  @Order(7)
  void updateFamilyPasswordSyncsDevicesAfterPair() throws Exception {
    MvcResult codeResult =
        mockMvc
            .perform(
                post("/api/v1/devices/pairing-code")
                    .header("Authorization", "Bearer " + accessToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{}"))
            .andExpect(status().isOk())
            .andReturn();
    String code =
        objectMapper
            .readTree(codeResult.getResponse().getContentAsString())
            .get("data")
            .get("code")
            .asText();

    MvcResult pairResult =
        mockMvc
            .perform(
                post("/api/v1/device/pair")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                                {
                                  "pairingCode": "%s",
                                  "deviceName": "Family Phone",
                                  "deviceModel": "Test",
                                  "androidId": "family-pass-android-001",
                                  "consentAccepted": "true",
                                  "osVersion": "14",
                                  "appVersion": "1.0.0"
                                }
                                """
                            .formatted(code)))
            .andExpect(status().isOk())
            .andReturn();

    UUID deviceId =
        UUID.fromString(
            objectMapper
                .readTree(pairResult.getResponse().getContentAsString())
                .get("data")
                .get("deviceId")
                .asText());

    mockMvc
        .perform(
            post("/api/v1/parent/child-login-password")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"childLoginPassword\":\"NewFamily456\"}"))
        .andExpect(status().isOk());

    var device = deviceRepository.findById(deviceId).orElseThrow();
    assertThat(passwordEncoder.matches("NewFamily456", device.getChildLoginPasswordHash()))
        .isTrue();
  }
}
