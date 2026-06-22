package com.parentalcontrol.observability;

import com.parentalcontrol.common.api.ApiResponse;
import com.parentalcontrol.device.domain.Device;
import com.parentalcontrol.device.repository.DeviceRepository;
import com.parentalcontrol.device.service.DeviceCertificationService;
import com.parentalcontrol.observability.dto.DeviceCertificationRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Ingest OEM certification results from device farm / validation runners. */
@RestController
@RequestMapping("/api/v1/admin/certification")
@RequiredArgsConstructor
public class DeviceCertificationController {

    private final DeviceRepository deviceRepository;
    private final DeviceCertificationService deviceCertificationService;
    private final OemObservabilityService oemObservabilityService;

    @PostMapping("/report")
    public ApiResponse<Void> ingestCertification(@RequestBody DeviceCertificationRequest request) {
        if (request.getScenarios() != null) {
            for (var scenario : request.getScenarios()) {
                long mttrMs = scenario.getMttrSeconds() != null ? scenario.getMttrSeconds() * 1000L : 0L;
                oemObservabilityService.recordCertificationRun(
                        request.getVendor(),
                        scenario.getStatus(),
                        request.getScore() != null ? request.getScore() : 0);
            }
        }
        if (request.getDeviceId() != null && request.getStatus() != null) {
            deviceRepository.findById(UUID.fromString(request.getDeviceId()))
                    .ifPresent(device -> persistCertification(device, request));
        }
        return ApiResponse.ok(null);
    }

    private void persistCertification(Device device, DeviceCertificationRequest request) {
        deviceCertificationService.applyCertificationResult(
                device,
                request.getStatus(),
                request.getScore() != null ? request.getScore() : 0,
                request.getVendor());
        deviceRepository.save(device);
    }
}
