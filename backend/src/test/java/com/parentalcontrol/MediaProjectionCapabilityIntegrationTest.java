package com.parentalcontrol;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.parentalcontrol.audit.service.AuditService;
import com.parentalcontrol.device.dao.DeviceDao;
import com.parentalcontrol.device.domain.Device;
import com.parentalcontrol.monitoring.service.RealtimeEventService;
import java.util.UUID;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class MediaProjectionCapabilityIntegrationTest {

  @DynamicPropertySource
  static void configure(DynamicPropertyRegistry registry) {
    IntegrationTestSupport.configureLocalInfra(registry, "parental_control_projection_test");
    registry.add("app.hmac.required", () -> "false");
  }

  @Autowired private MockMvc mockMvc;

  @Autowired private ObjectMapper objectMapper;

  @Autowired private DeviceDao deviceRepository;

  @MockBean private AuditService auditService;

  @MockBean private RealtimeEventService realtimeEventService;

  private static String parentToken;
  private static String deviceToken;
  private static UUID deviceId;
  private static final String RUN_ID = UUID.randomUUID().toString().substring(0, 8);

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
                                  "email": "proj-%s@example.com",
                                  "password": "SecurePass123!",
                                  "firstName": "Proj",
                                  "lastName": "Parent",
                                  "consentAccepted": "true"
                                }
                                """
                        .formatted(RUN_ID)))
        .andExpect(status().isCreated());

    MvcResult login =
        mockMvc
            .perform(
                post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                                {"email": "proj-%s@example.com", "password": "SecurePass123!"}
                                """
                            .formatted(RUN_ID)))
            .andExpect(status().isOk())
            .andReturn();
    parentToken =
        objectMapper
            .readTree(login.getResponse().getContentAsString())
            .get("data")
            .get("accessToken")
            .asText();

    String pairingCode =
        IntegrationTestSupport.createPairingCode(mockMvc, objectMapper, parentToken);

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
                                  "deviceName": "Proj Phone",
                                  "deviceModel": "Pixel",
                                  "osVersion": "14",
                                  "androidId": "proj-android-%s",
                                  "consentAccepted": true
                                }
                                """,
                            pairingCode, RUN_ID)))
            .andExpect(status().isCreated())
            .andReturn();
    JsonNode pairData = objectMapper.readTree(pair.getResponse().getContentAsString()).get("data");
    deviceToken = pairData.get("deviceToken").asText();
    deviceId = UUID.fromString(pairData.get("deviceId").asText());
  }

  @Test
  @Order(2)
  void heartbeat_lossClearsSilentScreenCapable() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/device/heartbeat")
                .header("Authorization", "Bearer " + deviceToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                                {
                                  "batteryLevel": 80,
                                  "charging": false,
                                  "networkType": "WIFI",
                                  "deviceOwnerActive": true,
                                  "mediaProjectionGranted": true,
                                  "silentScreenCapable": true
                                }
                                """))
        .andExpect(status().isOk());

    Device device = deviceRepository.findById(deviceId).orElseThrow();
    assertThat(device.isMediaProjectionGranted()).isTrue();
    assertThat(device.isSilentScreenCapable()).isTrue();

    mockMvc
        .perform(
            post("/api/v1/device/heartbeat")
                .header("Authorization", "Bearer " + deviceToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                                {
                                  "batteryLevel": 80,
                                  "charging": false,
                                  "networkType": "WIFI",
                                  "deviceOwnerActive": true,
                                  "mediaProjectionGranted": false,
                                  "mediaProjectionConsentState": "LOST_AFTER_PROCESS_DEATH",
                                  "silentScreenCapable": false
                                }
                                """))
        .andExpect(status().isOk());

    device = deviceRepository.findById(deviceId).orElseThrow();
    assertThat(device.isMediaProjectionGranted()).isFalse();
    assertThat(device.getMediaProjectionConsentState()).isEqualTo("LOST_AFTER_PROCESS_DEATH");
    assertThat(device.isSilentScreenCapable()).isFalse();

    mockMvc
        .perform(
            get("/api/v1/devices/" + deviceId).header("Authorization", "Bearer " + parentToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.mediaProjectionGranted").value(false))
        .andExpect(jsonPath("$.data.mediaProjectionConsentState").value("LOST_AFTER_PROCESS_DEATH"))
        .andExpect(jsonPath("$.data.silentScreenCapable").value(false));
  }

  @Test
  @Order(3)
  void startScreenShare_allowedWhenConsentNotYetGranted_childWillPrompt() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/monitoring/start-stream")
                .header("Authorization", "Bearer " + parentToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    String.format(
                        """
                                {"deviceId": "%s", "streamType": "SCREEN"}
                                """,
                        deviceId)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.sessionId").isNotEmpty());
  }

  /**
   * Lost consent must not block session create — child prompts / auto-approves on stream bootstrap
   * (older UX). Parent learns of decline via streaming failure events.
   */
  @Test
  @Order(4)
  void startScreenShare_allowedForNonDeviceOwnerWhenConsentLost() throws Exception {
    Device device = deviceRepository.findById(deviceId).orElseThrow();
    device.setDeviceOwnerActive(false);
    device.setMediaProjectionGranted(false);
    device.setMediaProjectionConsentState("LOST_AFTER_PROCESS_DEATH");
    deviceRepository.save(device);

    mockMvc
        .perform(
            post("/api/v1/monitoring/start-stream")
                .header("Authorization", "Bearer " + parentToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    String.format(
                        """
                                {"deviceId": "%s", "streamType": "SCREEN"}
                                """,
                        deviceId)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.sessionId").isNotEmpty());
  }

  /** Stale granted=true with lost state: still start — child must re-prompt Cast. */
  @Test
  @Order(5)
  void startScreenShare_allowedWhenStaleGrantedFlagWithLostConsentState() throws Exception {
    Device device = deviceRepository.findById(deviceId).orElseThrow();
    device.setMediaProjectionGranted(true);
    device.setMediaProjectionConsentState("LOST_AFTER_PROCESS_DEATH");
    device.setSilentScreenCapable(false);
    deviceRepository.save(device);

    mockMvc
        .perform(
            post("/api/v1/monitoring/start-stream")
                .header("Authorization", "Bearer " + parentToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    String.format(
                        """
                                {"deviceId": "%s", "streamType": "SCREEN"}
                                """,
                        deviceId)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.sessionId").isNotEmpty());
  }

  /** NEVER_GRANTED must allow start so first Cast prompt can run on the child. */
  @Test
  @Order(6)
  void startScreenShare_allowedWhenConsentNeverGranted() throws Exception {
    Device device = deviceRepository.findById(deviceId).orElseThrow();
    device.setMediaProjectionGranted(true);
    device.setMediaProjectionConsentState("NEVER_GRANTED");
    device.setSilentScreenCapable(true);
    deviceRepository.save(device);

    mockMvc
        .perform(
            post("/api/v1/monitoring/start-stream")
                .header("Authorization", "Bearer " + parentToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    String.format(
                        """
                                {"deviceId": "%s", "streamType": "SCREEN"}
                                """,
                        deviceId)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.sessionId").isNotEmpty());
  }
}
