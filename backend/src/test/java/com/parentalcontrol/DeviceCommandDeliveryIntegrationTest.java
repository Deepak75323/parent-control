package com.parentalcontrol;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.parentalcontrol.audit.service.AuditService;
import com.parentalcontrol.device.dao.DeviceCommandDao;
import com.parentalcontrol.device.dao.DeviceDao;
import com.parentalcontrol.device.domain.DeviceCommandStatus;
import com.parentalcontrol.device.domain.DeviceCommandType;
import com.parentalcontrol.device.service.FcmWakeService;
import com.parentalcontrol.monitoring.service.RealtimeEventService;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Verifies device-command STOMP/FCM notifications fire only after the command row is committed. */
@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DeviceCommandDeliveryIntegrationTest {

  @DynamicPropertySource
  static void configure(DynamicPropertyRegistry registry) {
    IntegrationTestSupport.configureLocalInfra(registry, "parental_control_command_delivery_test");
    registry.add("app.hmac.required", () -> "false");
    registry.add("app.fcm.enabled", () -> "false");
  }

  @Autowired private MockMvc mockMvc;

  @Autowired private ObjectMapper objectMapper;

  @Autowired private DeviceDao deviceRepository;

  @Autowired private DeviceCommandDao deviceCommandRepository;

  @Autowired private PlatformTransactionManager transactionManager;

  @MockBean private RealtimeEventService realtimeEventService;

  @MockBean private FcmWakeService fcmWakeService;

  @MockBean private AuditService auditService;

  private static String parentToken;
  private static String deviceToken;
  private static UUID deviceId;

  @BeforeEach
  void resetMocks() {
    reset(realtimeEventService, fcmWakeService, auditService);
  }

  @Test
  @Order(1)
  void setupParentAndDevice() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                                {
                                  "email": "cmd@example.com",
                                  "password": "SecurePass123!",
                                  "firstName": "Cmd",
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
                                {"email": "cmd@example.com", "password": "SecurePass123!"}
                                """))
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
                                  "deviceName": "Command Phone",
                                  "deviceModel": "Pixel",
                                  "osVersion": "14",
                                  "androidId": "cmd-android-id-001"
                                }
                                """,
                            pairingCode)))
            .andExpect(status().isOk())
            .andReturn();
    JsonNode pairData = objectMapper.readTree(pair.getResponse().getContentAsString()).get("data");
    deviceToken = pairData.get("deviceToken").asText();
    deviceId = UUID.fromString(pairData.get("deviceId").asText());

    deviceRepository
        .findById(deviceId)
        .ifPresent(
            device -> {
              device.setFcmToken("test-fcm-token");
              deviceRepository.save(device);
            });
  }

  @Test
  @Order(2)
  void sendCommand_publishesStompAndFcmAfterCommit() throws Exception {
    AtomicBoolean visibleInSeparateTransaction = new AtomicBoolean(false);

    doAnswer(
            invocation -> {
              UUID commandId = invocation.getArgument(1);
              TransactionTemplate tx = new TransactionTemplate(transactionManager);
              Boolean found =
                  tx.execute(
                      status ->
                          deviceCommandRepository
                              .findById(commandId)
                              .filter(cmd -> cmd.getStatus() == DeviceCommandStatus.PENDING)
                              .isPresent());
              visibleInSeparateTransaction.set(Boolean.TRUE.equals(found));
              return null;
            })
        .when(realtimeEventService)
        .publishDeviceCommand(any(), any(), any());

    MvcResult result =
        mockMvc
            .perform(
                post("/api/v1/devices/" + deviceId + "/commands")
                    .header("Authorization", "Bearer " + parentToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                                {"commandType": "REVOKE_MONITORING"}
                                """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("PENDING"))
            .andReturn();

    UUID commandId =
        UUID.fromString(
            objectMapper
                .readTree(result.getResponse().getContentAsString())
                .get("data")
                .get("id")
                .asText());

    verify(realtimeEventService)
        .publishDeviceCommand(
            eq(deviceId), eq(commandId), eq(DeviceCommandType.REVOKE_MONITORING.name()));
    verify(fcmWakeService)
        .wakeDevice(
            argThat(device -> deviceId.equals(device.getId())),
            eq("DEVICE_COMMAND"),
            eq(
                Map.of(
                    "commandId", commandId.toString(),
                    "commandType", DeviceCommandType.REVOKE_MONITORING.name())));

    assertThat(visibleInSeparateTransaction).isTrue();

    mockMvc
        .perform(
            get("/api/v1/device/commands/pending").header("Authorization", "Bearer " + deviceToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].id").value(commandId.toString()))
        .andExpect(jsonPath("$.data[0].commandType").value("REVOKE_MONITORING"));
  }

  @Test
  @Order(3)
  void sendCommand_rollbackSkipsStompAndFcm() throws Exception {
    doThrow(new RuntimeException("audit failed"))
        .when(auditService)
        .log(any(), any(), any(), any());

    long before = deviceCommandRepository.count();

    mockMvc
        .perform(
            post("/api/v1/devices/" + deviceId + "/commands")
                .header("Authorization", "Bearer " + parentToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                                {"commandType": "REVOKE_MONITORING"}
                                """))
        .andExpect(status().is5xxServerError());

    assertThat(deviceCommandRepository.count()).isEqualTo(before);
    verify(realtimeEventService, never()).publishDeviceCommand(any(), any(), any());
    verify(fcmWakeService, never()).wakeDevice(any(), any(), any());
  }
}
