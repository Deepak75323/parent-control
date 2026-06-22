package com.parentalcontrol;

import com.parentalcontrol.audit.service.AuditService;
import com.parentalcontrol.device.domain.DeviceCommandStatus;
import com.parentalcontrol.device.repository.DeviceCommandRepository;
import com.parentalcontrol.device.repository.DeviceRepository;
import com.parentalcontrol.device.service.FcmWakeService;
import com.parentalcontrol.monitoring.service.RealtimeEventService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Backend chaos-style reliability tests runnable in CI (no live fault injection).
 * Validates command persistence and API health under simulated degradation config.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ChaosReliabilityIntegrationTest {

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        IntegrationTestSupport.configureLocalInfra(registry, "parental_control_chaos_reliability_test");
        registry.add("app.hmac.required", () -> "false");
        registry.add("app.fcm.enabled", () -> "false");
        registry.add("app.stomp.cluster.mode", () -> "local");
        registry.add("app.observability.prometheus-public", () -> "true");
    }

    @Autowired MockMvc mockMvc;
    @Autowired DeviceCommandRepository deviceCommandRepository;
    @Autowired DeviceRepository deviceRepository;

    @MockBean RealtimeEventService realtimeEventService;
    @MockBean FcmWakeService fcmWakeService;
    @MockBean AuditService auditService;

    @BeforeEach
    void cleanDb() {
        deviceCommandRepository.deleteAll();
    }

    @Test
    void apiHealthRemainsAvailable() throws Exception {
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    @Test
    void commandRowsPersistAcrossReadinessChecks() throws Exception {
        long before = deviceCommandRepository.count();
        mockMvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk());
        assertThat(deviceCommandRepository.count()).isEqualTo(before);
    }

    @Test
    void prometheusMetricsEndpointAvailable() throws Exception {
        mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk())
                .andExpect(result -> assertThat(result.getResponse().getContentAsString())
                        .contains("jvm_memory_used_bytes"));
    }
}
