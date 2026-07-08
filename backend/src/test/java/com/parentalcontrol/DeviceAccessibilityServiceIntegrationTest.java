package com.parentalcontrol;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.parentalcontrol.device.domain.Device;
import com.parentalcontrol.device.repository.DeviceRepository;
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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DeviceAccessibilityServiceIntegrationTest {

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        IntegrationTestSupport.configureLocalInfra(registry, "parental_control_accessibility_test");
        registry.add("app.hmac.required", () -> "false");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DeviceRepository deviceRepository;

    private static String parentToken;
    private static String deviceToken;
    private static UUID deviceId;

    @Test
    @Order(1)
    void setupPairing() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "a11y@example.com",
                                  "password": "SecurePass123!",
                                  "firstName": "A11y",
                                  "lastName": "Parent",
                                  "consentAccepted": "true"
                                }
                                """))
                .andExpect(status().isCreated());

        MvcResult login = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "a11y@example.com", "password": "SecurePass123!"}
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
                                  "deviceName": "A11y Phone",
                                  "deviceModel": "Pixel",
                                  "osVersion": "14",
                                  "androidId": "a11y-android-001"
                                }
                                """, pairingCode)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode pairData = objectMapper.readTree(pair.getResponse().getContentAsString()).get("data");
        deviceToken = pairData.get("deviceToken").asText();
        deviceId = UUID.fromString(pairData.get("deviceId").asText());
    }

    @Test
    @Order(2)
    void heartbeat_updatesAccessibilityServiceConnected() throws Exception {
        mockMvc.perform(post("/api/v1/device/heartbeat")
                        .header("Authorization", "Bearer " + deviceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "batteryLevel": 80,
                                  "charging": false,
                                  "networkType": "WIFI",
                                  "accessibilityEnabled": true,
                                  "accessibilityServiceConnected": true
                                }
                                """))
                .andExpect(status().isOk());

        Device device = deviceRepository.findById(deviceId).orElseThrow();
        assertThat(device.isAccessibilityServiceConnected()).isTrue();

        mockMvc.perform(post("/api/v1/device/heartbeat")
                        .header("Authorization", "Bearer " + deviceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "batteryLevel": 80,
                                  "charging": false,
                                  "networkType": "WIFI",
                                  "accessibilityEnabled": true,
                                  "accessibilityServiceConnected": false
                                }
                                """))
                .andExpect(status().isOk());

        device = deviceRepository.findById(deviceId).orElseThrow();
        assertThat(device.isAccessibilityServiceConnected()).isFalse();

        mockMvc.perform(get("/api/v1/devices/" + deviceId)
                        .header("Authorization", "Bearer " + parentToken))
                .andExpect(status().isOk());
    }
}
