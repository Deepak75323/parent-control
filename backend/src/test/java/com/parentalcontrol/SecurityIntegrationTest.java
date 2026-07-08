package com.parentalcontrol;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.parentalcontrol.device.domain.Device;
import com.parentalcontrol.device.domain.DeviceStatus;
import com.parentalcontrol.device.repository.DeviceRepository;
import com.parentalcontrol.device.security.DeviceHmacService;
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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SecurityIntegrationTest {

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        IntegrationTestSupport.configureLocalInfra(registry, "parental_control_security_test");
        registry.add("app.hmac.required", () -> "true");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DeviceRepository deviceRepository;

    @Autowired
    private DeviceHmacService deviceHmacService;

    private static String parentToken;
    private static String deviceToken;
    private static String otherDeviceToken;
    private static String deviceHmacKey;
    private static UUID deviceId;
    private static UUID otherDeviceId;
    private static String pairingCode;
    private static String sessionRoomId;

    @Test
    @Order(1)
    void setupDevices() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "sec@example.com",
                                  "password": "SecurePass123!",
                                  "firstName": "Sec",
                                  "lastName": "Parent",
                                  "consentAccepted": "true"
                                }
                                """))
                .andExpect(status().isCreated());

        MvcResult login = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "sec@example.com", "password": "SecurePass123!"}
                                """))
                .andExpect(status().isOk())
                .andReturn();
        parentToken = objectMapper.readTree(login.getResponse().getContentAsString())
                .get("data").get("accessToken").asText();

        pairingCode = createPairingCode();
        pairDevice("sec-android-001", "Sec Phone");
        JsonNode first = objectMapper.readTree(lastPairResponse());
        deviceToken = first.get("data").get("deviceToken").asText();
        deviceHmacKey = first.get("data").get("deviceHmacKey").asText();
        deviceId = UUID.fromString(first.get("data").get("deviceId").asText());

        pairingCode = createPairingCode();
        pairDevice("sec-android-002", "Other Phone");
        JsonNode otherPair = objectMapper.readTree(lastPairResponse());
        otherDeviceToken = otherPair.get("data").get("deviceToken").asText();
        otherDeviceId = UUID.fromString(otherPair.get("data").get("deviceId").asText());
    }

    private String lastPairResponseBody;

    private String lastPairResponse() {
        return lastPairResponseBody;
    }

    private String createPairingCode() throws Exception {
        return IntegrationTestSupport.createPairingCode(mockMvc, objectMapper, parentToken);
    }

    private void pairDevice(String androidId, String name) throws Exception {
        MvcResult pair = mockMvc.perform(post("/api/v1/device/pair")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("""
                                {
                                  "pairingCode": "%s",
                                  "deviceName": "%s",
                                  "deviceModel": "Pixel",
                                  "osVersion": "14",
                                  "appVersion": "1.0.0",
                                  "androidId": "%s",
                                  "consentAccepted": "true"
                                }
                                """, pairingCode, name, androidId)))
                .andExpect(status().isCreated())
                .andReturn();
        lastPairResponseBody = pair.getResponse().getContentAsString();
    }

    @Test
    @Order(2)
    void rejectsInvalidHmacSignature() throws Exception {
        String body = "{\"entries\":[]}";
        String path = "/api/v1/device/usage-stats";
        String timestamp = String.valueOf(Instant.now().getEpochSecond());

        mockMvc.perform(post(path)
                        .header("Authorization", "Bearer " + deviceToken)
                        .header("X-Device-Timestamp", timestamp)
                        .header("X-Device-Nonce", UUID.randomUUID().toString())
                        .header("X-Device-Signature", "invalid-signature")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Order(3)
    void acceptsValidHmacSignature() throws Exception {
        String body = "{\"entries\":[]}";
        String path = "/api/v1/device/usage-stats";
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        String nonce = UUID.randomUUID().toString();
        String bodyHash = sha256Hex(body);
        String canonical = "POST\n" + path + "\n" + timestamp + "\n" + bodyHash;
        String signature = deviceHmacService.sign(deviceHmacKey, canonical);

        mockMvc.perform(post(path)
                        .header("Authorization", "Bearer " + deviceToken)
                        .header("X-Device-Timestamp", timestamp)
                        .header("X-Device-Nonce", nonce)
                        .header("X-Device-Signature", signature)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
    }

    @Test
    @Order(4)
    void childCannotAccessForeignDeviceSignaling() throws Exception {
        MvcResult stream = mockMvc.perform(post("/api/v1/monitoring/start-stream")
                        .header("Authorization", "Bearer " + parentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("""
                                {"deviceId": "%s", "streamType": "SCREEN"}
                                """, deviceId)))
                .andExpect(status().isOk())
                .andReturn();
        sessionRoomId = objectMapper.readTree(stream.getResponse().getContentAsString())
                .get("data").get("signalingRoomId").asText();

        mockMvc.perform(get("/api/v1/monitoring/signaling/" + sessionRoomId)
                        .header("Authorization", "Bearer " + otherDeviceToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(5)
    void rejectsStaleTokenVersion() throws Exception {
        Device device = deviceRepository.findById(deviceId).orElseThrow();
        device.setTokenVersion(device.getTokenVersion() + 1);
        deviceRepository.save(device);

        mockMvc.perform(post("/api/v1/device/heartbeat")
                        .header("Authorization", "Bearer " + deviceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"batteryLevel\": 50}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Order(6)
    void rejectsChildPolicyAckWithoutHmac() throws Exception {
        String body = "{\"policyVersion\": 1}";
        String path = "/api/v1/child/policies/ack";

        mockMvc.perform(post(path)
                        .header("Authorization", "Bearer " + deviceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Order(7)
    void acceptsChildPolicyAckWithValidHmac() throws Exception {
        String body = "{\"policyVersion\": 1}";
        String path = "/api/v1/child/policies/ack";
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        String nonce = UUID.randomUUID().toString();
        String bodyHash = sha256Hex(body);
        String canonical = "POST\n" + path + "\n" + timestamp + "\n" + bodyHash;
        String signature = deviceHmacService.sign(deviceHmacKey, canonical);

        mockMvc.perform(post(path)
                        .header("Authorization", "Bearer " + deviceToken)
                        .header("X-Device-Timestamp", timestamp)
                        .header("X-Device-Nonce", nonce)
                        .header("X-Device-Signature", signature)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
    }

    @Test
    @Order(8)
    void rejectsEventsPolicyViolationWithoutHmac() throws Exception {
        String body = """
                {
                  "violationType": "APP_BLOCKED",
                  "packageName": "com.example.app",
                  "details": "test"
                }
                """;
        String path = "/api/v1/events/policy-violations";

        mockMvc.perform(post(path)
                        .header("Authorization", "Bearer " + deviceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Order(9)
    void rejectsRevokedDeviceToken() throws Exception {
        Device device = deviceRepository.findById(deviceId).orElseThrow();
        device.setStatus(DeviceStatus.REVOKED);
        device.setTokenVersion(device.getTokenVersion() + 1);
        deviceRepository.save(device);

        mockMvc.perform(post("/api/v1/device/heartbeat")
                        .header("Authorization", "Bearer " + deviceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"batteryLevel\": 50}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("REPAIR_REQUIRED"));
    }

    @Test
    @Order(10)
    void rejectsSignalingWithoutSessionId() throws Exception {
        MvcResult stream = mockMvc.perform(post("/api/v1/monitoring/start-stream")
                        .header("Authorization", "Bearer " + parentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("""
                                {"deviceId": "%s", "streamType": "SCREEN"}
                                """, deviceId)))
                .andExpect(status().isOk())
                .andReturn();
        String roomId = objectMapper.readTree(stream.getResponse().getContentAsString())
                .get("data").get("signalingRoomId").asText();

        mockMvc.perform(post("/api/v1/monitoring/signaling/" + roomId)
                        .header("Authorization", "Bearer " + deviceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"messageType\": \"offer\", \"payload\": \"{\\\"type\\\":\\\"offer\\\"}\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(11)
    void rejectsChildSignalingPostWithoutHmac() throws Exception {
        MvcResult stream = mockMvc.perform(post("/api/v1/monitoring/start-stream")
                        .header("Authorization", "Bearer " + parentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("""
                                {"deviceId": "%s", "streamType": "SCREEN"}
                                """, deviceId)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode data = objectMapper.readTree(stream.getResponse().getContentAsString()).get("data");
        String roomId = data.get("signalingRoomId").asText();
        String sessionId = data.get("sessionId").asText();
        String body = String.format(
                "{\"messageType\": \"offer\", \"payload\": \"{\\\"type\\\":\\\"offer\\\"}\", \"sessionId\": \"%s\"}",
                sessionId);

        mockMvc.perform(post("/api/v1/monitoring/signaling/" + roomId)
                        .header("Authorization", "Bearer " + deviceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Order(12)
    void acceptsChildSignalingPostWithValidHmac() throws Exception {
        MvcResult stream = mockMvc.perform(post("/api/v1/monitoring/start-stream")
                        .header("Authorization", "Bearer " + parentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("""
                                {"deviceId": "%s", "streamType": "SCREEN"}
                                """, deviceId)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode data = objectMapper.readTree(stream.getResponse().getContentAsString()).get("data");
        String roomId = data.get("signalingRoomId").asText();
        String sessionId = data.get("sessionId").asText();
        String body = String.format(
                "{\"messageType\": \"offer\", \"payload\": \"{\\\"type\\\":\\\"offer\\\"}\", \"sessionId\": \"%s\"}",
                sessionId);
        String path = "/api/v1/monitoring/signaling/" + roomId;
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        String nonce = UUID.randomUUID().toString();
        String bodyHash = sha256Hex(body);
        String canonical = "POST\n" + path + "\n" + timestamp + "\n" + bodyHash;
        String signature = deviceHmacService.sign(deviceHmacKey, canonical);

        mockMvc.perform(post(path)
                        .header("Authorization", "Bearer " + deviceToken)
                        .header("X-Device-Timestamp", timestamp)
                        .header("X-Device-Nonce", nonce)
                        .header("X-Device-Signature", signature)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
    }

    private static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
