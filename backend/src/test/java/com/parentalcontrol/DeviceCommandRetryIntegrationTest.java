package com.parentalcontrol;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.parentalcontrol.audit.service.AuditService;
import com.parentalcontrol.device.domain.Device;
import com.parentalcontrol.device.domain.DeviceCommand;
import com.parentalcontrol.device.domain.DeviceCommandStatus;
import com.parentalcontrol.device.domain.DeviceCommandType;
import com.parentalcontrol.device.domain.DeviceStatus;
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
 * Stale DELIVERED command recovery: DELIVERED → PENDING (up to max retries) → FAILED when exhausted.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DeviceCommandRetryIntegrationTest {

    private static final int MAX_ATTEMPTS = 3;

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        IntegrationTestSupport.configureLocalInfra(registry, "parental_control_command_retry_test");
        registry.add("app.hmac.required", () -> "false");
        registry.add("app.fcm.enabled", () -> "false");
        registry.add("app.device-command.max-delivery-attempts", () -> String.valueOf(MAX_ATTEMPTS));
        registry.add("app.device-command.stale-delivered-seconds", () -> "1");
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
                                  "email": "retry@example.com",
                                  "password": "SecurePass123!",
                                  "firstName": "Retry",
                                  "lastName": "Parent",
                                  "consentAccepted": "true"
                                }
                                """))
                .andExpect(status().isCreated());

        MvcResult login = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "retry@example.com", "password": "SecurePass123!"}
                                """))
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
                                  "deviceName": "Retry Phone",
                                  "deviceModel": "Pixel",
                                  "osVersion": "14",
                                  "androidId": "retry-android-id-001",
                                  "consentAccepted": "true"
                                }
                                """, pairingCode)))
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
    void staleDelivered_requeuesToPending() throws Exception {
        UUID commandId = createRemoteCommand();

        mockMvc.perform(post("/api/v1/device/commands/" + commandId + "/claim")
                        .header("Authorization", "Bearer " + deviceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(true));

        markDeliveredStale(commandId);

        mockMvc.perform(get("/api/v1/device/commands/pending")
                        .header("Authorization", "Bearer " + deviceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(commandId.toString()));

        DeviceCommand requeued = deviceCommandRepository.findById(commandId).orElseThrow();
        assertThat(requeued.getStatus()).isEqualTo(DeviceCommandStatus.PENDING);
        assertThat(requeued.getDeliveredAt()).isNull();
        assertThat(requeued.getDeliveryAttemptCount()).isEqualTo(1);

        deviceCommandRepository.deleteById(commandId);
    }

    @Test
    @Order(3)
    void duplicateClaim_isRejected() throws Exception {
        UUID commandId = createRemoteCommand();

        mockMvc.perform(post("/api/v1/device/commands/" + commandId + "/claim")
                        .header("Authorization", "Bearer " + deviceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(true));

        mockMvc.perform(post("/api/v1/device/commands/" + commandId + "/claim")
                        .header("Authorization", "Bearer " + deviceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(false));

        DeviceCommand command = deviceCommandRepository.findById(commandId).orElseThrow();
        assertThat(command.getStatus()).isEqualTo(DeviceCommandStatus.DELIVERED);
        assertThat(command.getDeliveryAttemptCount()).isEqualTo(1);
    }

    @Test
    @Order(4)
    void staleDelivered_exhaustsMaxRetries_marksFailed() throws Exception {
        UUID commandId = createRemoteCommand();

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            mockMvc.perform(post("/api/v1/device/commands/" + commandId + "/claim")
                            .header("Authorization", "Bearer " + deviceToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").value(true));

            DeviceCommand claimed = deviceCommandRepository.findById(commandId).orElseThrow();
            assertThat(claimed.getDeliveryAttemptCount()).isEqualTo(attempt);

            if (attempt < MAX_ATTEMPTS) {
                markDeliveredStale(commandId);
                triggerReclaim();
                assertThat(deviceCommandRepository.findById(commandId).orElseThrow().getStatus())
                        .isEqualTo(DeviceCommandStatus.PENDING);
            } else {
                markDeliveredStale(commandId);
                triggerReclaim();
            }
        }

        DeviceCommand failed = deviceCommandRepository.findById(commandId).orElseThrow();
        assertThat(failed.getStatus()).isEqualTo(DeviceCommandStatus.FAILED);
        assertThat(failed.getDeliveryAttemptCount()).isEqualTo(MAX_ATTEMPTS);
        assertThat(failed.getErrorMessage()).contains("max retries");

        mockMvc.perform(get("/api/v1/device/commands/pending")
                        .header("Authorization", "Bearer " + deviceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    @Order(5)
    void ack_idempotentWhenAlreadyCompleted() throws Exception {
        UUID commandId = createRemoteCommand();

        mockMvc.perform(post("/api/v1/device/commands/" + commandId + "/claim")
                        .header("Authorization", "Bearer " + deviceToken))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/device/commands/" + commandId + "/ack")
                        .header("Authorization", "Bearer " + deviceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"success\": true}"))
                .andExpect(status().isOk());

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
    void ack_ignoredWhenRequeued_preventsSideEffects() throws Exception {
        UUID commandId = createRevokeCommand();

        mockMvc.perform(post("/api/v1/device/commands/" + commandId + "/claim")
                        .header("Authorization", "Bearer " + deviceToken))
                .andExpect(status().isOk());

        DeviceCommand command = deviceCommandRepository.findById(commandId).orElseThrow();
        command.setStatus(DeviceCommandStatus.PENDING);
        command.setDeliveredAt(null);
        deviceCommandRepository.save(command);

        mockMvc.perform(post("/api/v1/device/commands/" + commandId + "/ack")
                        .header("Authorization", "Bearer " + deviceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"success\": true}"))
                .andExpect(status().isOk());

        Device device = deviceRepository.findById(deviceId).orElseThrow();
        assertThat(device.getStatus()).isNotEqualTo(DeviceStatus.REVOKED);

        DeviceCommand unchanged = deviceCommandRepository.findById(commandId).orElseThrow();
        assertThat(unchanged.getStatus()).isEqualTo(DeviceCommandStatus.PENDING);
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

    private UUID createRevokeCommand() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/devices/" + deviceId + "/commands")
                        .header("Authorization", "Bearer " + parentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"commandType": "REVOKE_MONITORING"}
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
