package com.parentalcontrol.device.service;

import com.parentalcontrol.device.domain.Device;
import com.parentalcontrol.device.dto.HeartbeatRequest;
import com.parentalcontrol.observability.OemObservabilityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DeviceCertificationServiceTest {

    @Mock OemObservabilityService oemObservabilityService;

    DeviceCertificationService service;

    @BeforeEach
    void setUp() {
        service = new DeviceCertificationService(oemObservabilityService);
    }

    @Test
    void applyHeartbeatOemData_persistsVendorAndReadiness() {
        Device device = new Device();
        device.setOemVendor("unknown");
        HeartbeatRequest request = new HeartbeatRequest();
        request.setOemVendor("samsung");
        request.setAndroidSdkInt(34);
        request.setDeviceReadinessScore(85);
        request.setAndroid16ReadinessScore(78);
        request.setOemSetupComplete(true);
        request.setOemDiagnostics(Map.of(
                "failureClassifications", List.of("AUTOSTART_NOT_CONFIRMED")));

        service.applyHeartbeatOemData(device, request);

        assertThat(device.getOemVendor()).isEqualTo("samsung");
        assertThat(device.getAndroidSdkInt()).isEqualTo(34);
        assertThat(device.getDeviceReadinessScore()).isEqualTo(85);
        assertThat(device.getAndroid16ReadinessScore()).isEqualTo(78);
        assertThat(device.isOemSetupComplete()).isTrue();
        assertThat(device.getOemFailureClassifications()).containsExactly("AUTOSTART_NOT_CONFIRMED");
        verify(oemObservabilityService).recordHeartbeatOem(eq("samsung"), eq(85), eq(78), eq(true), anyList());
    }
}
