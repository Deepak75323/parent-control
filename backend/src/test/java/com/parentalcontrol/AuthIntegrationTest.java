package com.parentalcontrol;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AuthIntegrationTest {

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        IntegrationTestSupport.configureLocalInfra(registry, "parental_control_auth_test");
        registry.add("app.hmac.required", () -> "false");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private static String accessToken;
    private static String pairingCode;
    private static String deviceToken;
    private static String deviceId;

    @Test
    @Order(1)
    void registerParent() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "parent@example.com",
                                  "password": "SecurePass123!",
                                  "firstName": "Jane",
                                  "lastName": "Doe",
                                  "consentAccepted": "true"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").exists());
    }

    @Test
    @Order(2)
    void loginParent() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "parent@example.com",
                                  "password": "SecurePass123!"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        accessToken = body.get("data").get("accessToken").asText();
        assertThat(accessToken).isNotBlank();
    }

    @Test
    @Order(3)
    void createPairingCode() throws Exception {
        pairingCode = IntegrationTestSupport.createPairingCode(mockMvc, objectMapper, accessToken);
    }

    @Test
    @Order(31)
    void pairingCodeStatusBeforeClaim() throws Exception {
        mockMvc.perform(get("/api/v1/devices/pairing-code/" + pairingCode + "/status")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.code").value(pairingCode))
                .andExpect(jsonPath("$.data.used").value(false))
                .andExpect(jsonPath("$.data.expired").value(false));
    }

    @Test
    @Order(33)
    void onboardingAuthWithChildPassword() throws Exception {
        mockMvc.perform(post("/api/v1/onboarding/authenticate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "childLoginPassword": "TestChild1234"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.authenticated").value(true))
                .andExpect(jsonPath("$.data.pairingCode").value(pairingCode));
    }

    @Test
    @Order(34)
    void onboardingRecoveryWithSetupCode() throws Exception {
        mockMvc.perform(post("/api/v1/onboarding/authenticate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("""
                                {
                                  "pairingCode": "%s",
                                  "setupCodeOnly": true
                                }
                                """, pairingCode)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.authenticated").value(true))
                .andExpect(jsonPath("$.data.pairingCode").value(pairingCode));
    }

    @Test
    @Order(35)
    void pairChildDevice() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/device/pair")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("""
                                {
                                  "pairingCode": "%s",
                                  "deviceName": "Child Phone",
                                  "deviceModel": "Pixel 8",
                                  "osVersion": "14",
                                  "appVersion": "1.0.0",
                                  "androidId": "android-test-001",
                                  "consentAccepted": "true"
                                }
                                """, pairingCode)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.deviceToken").exists())
                .andReturn();

        JsonNode pairBody = objectMapper.readTree(result.getResponse().getContentAsString());
        deviceToken = pairBody.get("data").get("deviceToken").asText();
        deviceId = pairBody.get("data").get("deviceId").asText();
    }

    @Test
    @Order(41)
    void pairingCodeStatusAfterClaim() throws Exception {
        mockMvc.perform(get("/api/v1/devices/pairing-code/" + pairingCode + "/status")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.used").value(true))
                .andExpect(jsonPath("$.data.deviceId").value(deviceId))
                .andExpect(jsonPath("$.data.deviceName").value("Child Phone"));
    }

    @Test
    @Order(36)
    void onboardingRecoveryCodeFlow() throws Exception {
        mockMvc.perform(post("/api/v1/onboarding/recovery/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "androidId": "android-test-001"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.codeSent").value(true));

        MvcResult alerts = mockMvc.perform(get("/api/v1/alerts")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andReturn();
        String alertsBody = alerts.getResponse().getContentAsString();
        assertThat(alertsBody).contains("CHILD_LOGIN_RECOVERY");
        assertThat(alertsBody).contains("verificationCode");

        JsonNode alertsJson = objectMapper.readTree(alertsBody);
        String recoveryCode = null;
        for (JsonNode alert : alertsJson.get("data").get("content")) {
            if ("CHILD_LOGIN_RECOVERY".equals(alert.get("alertType").asText())) {
                recoveryCode = alert.get("payloadJson").get("verificationCode").asText();
                break;
            }
        }
        assertThat(recoveryCode).isNotBlank();

        mockMvc.perform(post("/api/v1/onboarding/authenticate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("""
                                {
                                  "recoveryCode": "%s",
                                  "androidId": "android-test-001"
                                }
                                """, recoveryCode)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.authenticated").value(true));
    }

    @Test
    @Order(5)
    void heartbeatAndListDevices() throws Exception {
        mockMvc.perform(post("/api/v1/device/heartbeat")
                        .header("Authorization", "Bearer " + deviceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "batteryLevel": 85,
                                  "charging": false,
                                  "networkType": "WIFI",
                                  "latitude": 37.7749,
                                  "longitude": -122.4194
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ONLINE"));

        mockMvc.perform(get("/api/v1/devices")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].deviceName").value("Child Phone"));
    }

    @Test
    @Order(6)
    void ingestAndListNotifications() throws Exception {
        mockMvc.perform(post("/api/v1/device/notifications")
                        .header("Authorization", "Bearer " + deviceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "notifications": [
                                    {
                                      "packageName": "com.example.chat",
                                      "title": "New message",
                                      "body": "Hello from school",
                                      "postedAt": "2026-05-19T10:00:00Z"
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accepted").value(1));

        mockMvc.perform(get("/api/v1/devices/" + deviceId + "/notifications")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].title").value("New message"));
    }

    @Test
    @Order(7)
    void ingestAndListLocationHistory() throws Exception {
        mockMvc.perform(post("/api/v1/device/location")
                        .header("Authorization", "Bearer " + deviceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "latitude": 37.7750,
                                  "longitude": -122.4195,
                                  "accuracyM": 12.5,
                                  "recordedAt": "2026-05-19T10:05:00Z"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.latitude").value(37.7750));

        mockMvc.perform(get("/api/v1/devices/" + deviceId + "/location-history")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].longitude").value(-122.4195));
    }

    @Test
    @Order(8)
    void ingestUsageStats() throws Exception {
        mockMvc.perform(post("/api/v1/device/usage-stats")
                        .header("Authorization", "Bearer " + deviceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "entries": [
                                    {
                                      "packageName": "com.example.game",
                                      "usageMs": 3600000,
                                      "recordedAt": "2026-05-19T11:00:00Z"
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accepted").value(1));

        mockMvc.perform(get("/api/v1/devices/" + deviceId + "/usage-stats")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].packageName").value("com.example.game"));
    }

    @Test
    @Order(9)
    void lowBatteryAlertAndScreenShare() throws Exception {
        mockMvc.perform(post("/api/v1/device/heartbeat")
                        .header("Authorization", "Bearer " + deviceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "batteryLevel": 15,
                                  "charging": false,
                                  "networkType": "WIFI"
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/alerts")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].alertType").value("LOW_BATTERY"));

        mockMvc.perform(post("/api/v1/monitoring/start-screen-share")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("""
                                {"deviceId": "%s"}
                                """, deviceId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.signalingRoomId").exists());
    }

    @Test
    @Order(42)
    void tamperGateVerifyPinAndCode() throws Exception {
        mockMvc.perform(post("/api/v1/parent/pin")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pin\":\"4321\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/device/tamper/attempt")
                        .header("Authorization", "Bearer " + deviceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.parentPinConfigured").value(true));

        MvcResult alerts = mockMvc.perform(get("/api/v1/alerts")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andReturn();
        String alertsBody = alerts.getResponse().getContentAsString();
        assertThat(alertsBody).contains("DEVICE_ADMIN_DISABLE_ATTEMPT");
        assertThat(alertsBody).contains("verificationCode");

        mockMvc.perform(post("/api/v1/device/tamper/verify")
                        .header("Authorization", "Bearer " + deviceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pin\":\"9999\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.verified").value(false));

        mockMvc.perform(post("/api/v1/device/tamper/verify")
                        .header("Authorization", "Bearer " + deviceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pin\":\"4321\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.verified").value(true))
                .andExpect(jsonPath("$.data.unlockExpiresAt").exists());

        MvcResult tamperCode = mockMvc.perform(get("/api/v1/devices/" + deviceId + "/tamper-code")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andReturn();
        String code = objectMapper.readTree(tamperCode.getResponse().getContentAsString())
                .get("data").get("code").asText();

        mockMvc.perform(post("/api/v1/device/tamper/verify")
                        .header("Authorization", "Bearer " + deviceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"code\":\"%s\"}", code)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.verified").value(true));

        mockMvc.perform(post("/api/v1/device/heartbeat")
                        .header("Authorization", "Bearer " + deviceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "batteryLevel": 80,
                                  "deviceAdminActive": false
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.deviceAdminActive").value(false));
    }

    @Test
    @Order(43)
    void tamperGateAcceptsDevMasterPin() throws Exception {
        mockMvc.perform(post("/api/v1/device/tamper/verify")
                        .header("Authorization", "Bearer " + deviceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pin\":\"Deepak@123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.verified").value(true))
                .andExpect(jsonPath("$.data.unlockExpiresAt").exists());
    }
}
