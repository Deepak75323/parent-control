package com.parentalcontrol;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.parentalcontrol.audit.service.AuditService;
import com.parentalcontrol.device.domain.DeviceCommand;
import com.parentalcontrol.device.domain.DeviceCommandStatus;
import com.parentalcontrol.device.repository.DeviceCommandRepository;
import com.parentalcontrol.device.repository.DeviceRepository;
import com.parentalcontrol.device.service.FcmWakeService;
import com.parentalcontrol.monitoring.service.RealtimeEventService;
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

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.reset;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Idempotent command execution: execution fingerprint prevents duplicate side effects
 * when ACK fails and stale reclaim runs.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DeviceCommandIdempotencyIntegrationTest {

    private static final int MAX_ATTEMPTS = 3;

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        IntegrationTestSupport.configureLocalInfra(registry, "parental_control_command_idempotency_test");
        registry.add("app.hmac.required", () -> "false");
        registry.add("app.fcm.enabled", () -> "false");
        registry.add("app.device-command.max-delivery-attempts", () -> String.valueOf(MAX_ATTEMPTS));
        registry.add("app.device-command.stale-delivered-seconds", () -> "1");
        registry.add("app.device-command.stale-executed-awaiting-ack-seconds", () -> "1");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DeviceRepository deviceRepository;

    @Autowired
    private DeviceCommandRepository deviceCommandRepository;

    @MockBean
    private RealtimeEventService realtimeEventService;

    @MockBean
    private FcmWakeService fcmWakeService;

    @MockBean
    private AuditService auditService;

    private static String parentToken;
    private static String deviceToken;
    private static UUID deviceId;
    private static final String RUN_ID = UUID.randomUUID().toString().substring(0, 8);

    @BeforeEach
    void resetMocks() {
        reset(realtimeEventService, fcmWakeService, auditService);
    }

    @Test
    @Order(1)
    void setupParentAndDevice() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "idempotent-%s@example.com",
                                  "password": "SecurePass123!",
                                  "firstName": "Idem",
                                  "lastName": "Parent",
                                  "consentAccepted": "true"
                                }
                                """.formatted(RUN_ID)))
                .andExpect(status().isCreated());

        MvcResult login = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "idempotent-%s@example.com", "password": "SecurePass123!"}
                                """.formatted(RUN_ID)))
                .andExpect(status().isOk())
                .andReturn();
        parentToken = objectMapper.readTree(login.getResponse().getContentAsString())
                .get("data").get("accessToken").asText();

        String pairingCode = IntegrationTestSupport.createPairingCode(mockMvc, objectMapper, parentToken);

        MvcResult pair = mockMvc.perform(post("/api/v1/device/pair")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("""
                                {
                                  "pairingCode": "%s",
                                  "deviceName": "Idempotent Phone",
                                  "deviceModel": "Pixel",
                                  "osVersion": "14",
                                  "androidId": "idempotent-android-%s",
                                  "consentAccepted": "true"
                                }
                                """, pairingCode, RUN_ID)))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode pairData = objectMapper.readTree(pair.getResponse().getContentAsString()).get("data");
        deviceToken = pairData.get("deviceToken").asText();
        deviceId = UUID.fromString(pairData.get("deviceId").asText());

        mockMvc.perform(post("/api/v1/device/heartbeat")
                        .header("Authorization", "Bearer " + deviceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "accessibilityEnabled": true,
                                  "accessibilityServiceConnected": true
                                }
                                """))
                .andExpect(status().isOk());
    }

    @Test
    @Order(2)
    void recordExecution_isIdempotent() throws Exception {
        UUID commandId = createRemoteCommand();

        mockMvc.perform(post("/api/v1/device/commands/" + commandId + "/claim")
                        .header("Authorization", "Bearer " + deviceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(true));

        mockMvc.perform(post("/api/v1/device/commands/" + commandId + "/executed")
                        .header("Authorization", "Bearer " + deviceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"success\": true}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/device/commands/" + commandId + "/executed")
                        .header("Authorization", "Bearer " + deviceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"success\": true}"))
                .andExpect(status().isOk());

        DeviceCommand command = deviceCommandRepository.findById(commandId).orElseThrow();
        assertThat(command.getExecutedAt()).isNotNull();
        assertThat(command.getExecutionSuccess()).isTrue();
        assertThat(command.getStatus()).isEqualTo(DeviceCommandStatus.DELIVERED);
    }

    @Test
    @Order(3)
    void staleDelivered_withExecutionFingerprint_isNotRequeued() throws Exception {
        UUID commandId = createRemoteCommand();

        mockMvc.perform(post("/api/v1/device/commands/" + commandId + "/claim")
                        .header("Authorization", "Bearer " + deviceToken))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/device/commands/" + commandId + "/executed")
                        .header("Authorization", "Bearer " + deviceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"success\": true}"))
                .andExpect(status().isOk());

        markDeliveredStale(commandId);
        triggerReclaim();

        DeviceCommand command = deviceCommandRepository.findById(commandId).orElseThrow();
        assertThat(command.getStatus()).isEqualTo(DeviceCommandStatus.DELIVERED);
        assertThat(command.getExecutedAt()).isNotNull();

        mockMvc.perform(get("/api/v1/device/commands/pending")
                        .header("Authorization", "Bearer " + deviceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    @Order(4)
    void staleDelivered_withoutExecutionFingerprint_isStillRequeued() throws Exception {
        UUID commandId = createRemoteCommand();

        mockMvc.perform(post("/api/v1/device/commands/" + commandId + "/claim")
                        .header("Authorization", "Bearer " + deviceToken))
                .andExpect(status().isOk());

        markDeliveredStale(commandId);
        triggerReclaim();

        DeviceCommand command = deviceCommandRepository.findById(commandId).orElseThrow();
        assertThat(command.getStatus()).isEqualTo(DeviceCommandStatus.PENDING);
        assertThat(command.getExecutedAt()).isNull();

        mockMvc.perform(get("/api/v1/device/commands/pending")
                        .header("Authorization", "Bearer " + deviceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(commandId.toString()));
    }

    @Test
    @Order(5)
    void ack_succeedsAfterExecutionFingerprint() throws Exception {
        UUID commandId = createRemoteCommand();

        mockMvc.perform(post("/api/v1/device/commands/" + commandId + "/claim")
                        .header("Authorization", "Bearer " + deviceToken))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/device/commands/" + commandId + "/executed")
                        .header("Authorization", "Bearer " + deviceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"success\": true}"))
                .andExpect(status().isOk());

        markDeliveredStale(commandId);
        triggerReclaim();

        mockMvc.perform(post("/api/v1/device/commands/" + commandId + "/ack")
                        .header("Authorization", "Bearer " + deviceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"success\": true}"))
                .andExpect(status().isOk());

        DeviceCommand command = deviceCommandRepository.findById(commandId).orElseThrow();
        assertThat(command.getStatus()).isEqualTo(DeviceCommandStatus.COMPLETED);
    }

    @Test
    @Order(6)
    void staleExecuted_autoFinalizesFromFingerprint() throws Exception {
        UUID commandId = createRemoteCommand();

        mockMvc.perform(post("/api/v1/device/commands/" + commandId + "/claim")
                        .header("Authorization", "Bearer " + deviceToken))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/device/commands/" + commandId + "/executed")
                        .header("Authorization", "Bearer " + deviceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"success\": true}"))
                .andExpect(status().isOk());

        DeviceCommand command = deviceCommandRepository.findById(commandId).orElseThrow();
        command.setExecutedAt(Instant.now().minus(10, ChronoUnit.SECONDS));
        deviceCommandRepository.save(command);

        triggerReclaim();

        DeviceCommand finalized = deviceCommandRepository.findById(commandId).orElseThrow();
        assertThat(finalized.getStatus()).isEqualTo(DeviceCommandStatus.COMPLETED);
    }

    @Test
    @Order(7)
    void duplicateClaim_afterExecutedFingerprintRequeueBlocked_staysDelivered() throws Exception {
        UUID commandId = createRemoteCommand();

        mockMvc.perform(post("/api/v1/device/commands/" + commandId + "/claim")
                        .header("Authorization", "Bearer " + deviceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(true));

        mockMvc.perform(post("/api/v1/device/commands/" + commandId + "/executed")
                        .header("Authorization", "Bearer " + deviceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"success\": true}"))
                .andExpect(status().isOk());

        markDeliveredStale(commandId);
        triggerReclaim();

        mockMvc.perform(post("/api/v1/device/commands/" + commandId + "/claim")
                        .header("Authorization", "Bearer " + deviceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(false));

        DeviceCommand command = deviceCommandRepository.findById(commandId).orElseThrow();
        assertThat(command.getStatus()).isEqualTo(DeviceCommandStatus.DELIVERED);
        assertThat(command.getDeliveryAttemptCount()).isEqualTo(1);
    }

    private UUID createRemoteCommand() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/devices/" + deviceId + "/commands")
                        .header("Authorization", "Bearer " + parentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"commandType": "GLOBAL_BACK"}
                                """))
                .andExpect(status().isOk())
                .andReturn();
        return UUID.fromString(
                objectMapper.readTree(result.getResponse().getContentAsString())
                        .get("data").get("id").asText());
    }

    private void markDeliveredStale(UUID commandId) {
        DeviceCommand command = deviceCommandRepository.findById(commandId).orElseThrow();
        command.setStatus(DeviceCommandStatus.DELIVERED);
        command.setDeliveredAt(Instant.now().minus(10, ChronoUnit.SECONDS));
        deviceCommandRepository.save(command);
    }

    private void triggerReclaim() throws Exception {
        mockMvc.perform(get("/api/v1/device/commands/pending")
                        .header("Authorization", "Bearer " + deviceToken))
                .andExpect(status().isOk());
    }
}
