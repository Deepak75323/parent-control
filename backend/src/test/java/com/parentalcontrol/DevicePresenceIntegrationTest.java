package com.parentalcontrol;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.parentalcontrol.device.dao.DeviceDao;
import com.parentalcontrol.device.domain.Device;
import com.parentalcontrol.device.domain.DeviceStatus;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
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
import org.springframework.test.web.servlet.MvcResult;

/** Verifies OFFLINE (presence) does not block heartbeats — only REVOKED + tokenVersion does. */
@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DevicePresenceIntegrationTest {

  @DynamicPropertySource
  static void configure(DynamicPropertyRegistry registry) {
    IntegrationTestSupport.configureLocalInfra(registry, "parental_control_presence_test");
    registry.add("app.hmac.required", () -> "false");
  }

  @Autowired private MockMvc mockMvc;

  @Autowired private ObjectMapper objectMapper;

  @Autowired private DeviceDao deviceRepository;

  private static String accessToken;
  private static String pairingCode;
  private static String deviceToken;
  private static UUID deviceUuid;

  @Test
  @Order(1)
  void setupPairing() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                                {
                                  "email": "presence@example.com",
                                  "password": "SecurePass123!",
                                  "firstName": "Pat",
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
                                {"email": "presence@example.com", "password": "SecurePass123!"}
                                """))
            .andExpect(status().isOk())
            .andReturn();
    accessToken =
        objectMapper
            .readTree(login.getResponse().getContentAsString())
            .get("data")
            .get("accessToken")
            .asText();

    pairingCode = IntegrationTestSupport.createPairingCode(mockMvc, objectMapper, accessToken);

    MvcResult pair =
        mockMvc
            .perform(
                post("/api/v1/device/pair")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        String.format(
                            """
                                {
                                  "pairingCode": "%s",
                                  "deviceName": "Presence Phone",
                                  "deviceModel": "Pixel",
                                  "osVersion": "14",
                                  "appVersion": "1.0.0",
                                  "androidId": "presence-android-001",
                                  "consentAccepted": "true"
                                }
                                """,
                            pairingCode)))
            .andExpect(status().isCreated())
            .andReturn();
    JsonNode pairBody = objectMapper.readTree(pair.getResponse().getContentAsString());
    deviceToken = pairBody.get("data").get("deviceToken").asText();
    deviceUuid = UUID.fromString(pairBody.get("data").get("deviceId").asText());
  }

  @Test
  @Order(2)
  void staleOfflineDeviceCanHeartbeatBackToOnline() throws Exception {
    Device device = deviceRepository.findById(deviceUuid).orElseThrow();
    device.setStatus(DeviceStatus.OFFLINE);
    device.setLastSeenAt(Instant.now().minus(15, ChronoUnit.MINUTES));
    deviceRepository.save(device);

    mockMvc
        .perform(
            post("/api/v1/device/heartbeat")
                .header("Authorization", "Bearer " + deviceToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                                {"batteryLevel": 90, "charging": true, "networkType": "WIFI"}
                                """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("ONLINE"));

    Device updated = deviceRepository.findById(deviceUuid).orElseThrow();
    assertThat(updated.getStatus()).isEqualTo(DeviceStatus.ONLINE);
    assertThat(updated.getLastSeenAt()).isAfter(Instant.now().minus(1, ChronoUnit.MINUTES));
  }

  @Test
  @Order(3)
  void revokedDeviceCannotHeartbeat() throws Exception {
    Device device = deviceRepository.findById(deviceUuid).orElseThrow();
    device.setStatus(DeviceStatus.REVOKED);
    device.setTokenVersion(device.getTokenVersion() + 1);
    deviceRepository.save(device);

    mockMvc
        .perform(
            post("/api/v1/device/heartbeat")
                .header("Authorization", "Bearer " + deviceToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                                {"batteryLevel": 90, "charging": true, "networkType": "WIFI"}
                                """))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("REPAIR_REQUIRED"));
  }
}
